package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import com.orbyte.canvasstudio.drawing.BasicPsdCodec
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import com.orbyte.canvasstudio.drawing.TileStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/**
 * Export metadata captured from the document's owner thread *after* pending
 * stroke commits have been resolved. Encoders never inspect mutable View /
 * Compose state. Tile reads use detached per-tile snapshots on the IO worker.
 *
 * Each export is written into an isolated temporary file. Source revisions and
 * the document metadata epoch are checked before encoding and before exposing
 * bytes to the selected destination. If editing overlaps an export, the staged
 * file is discarded; the user can retry without receiving mixed raster epochs.
 *
 * This is optimistic validation, NOT a frozen historical copy: it may reject
 * exports on actively edited documents. It deliberately avoids pinning many
 * full-resolution layer bitmaps in RAM.
 */
internal class DocumentExportSnapshot(
    val width: Int,
    val height: Int,
    private val cacheDirectory: File,
    private val metadataEpoch: Long,
    private val currentMetadataEpoch: () -> Long,
    private val layers: List<Layer>,
) {
    data class Layer(
        val name: String,
        val surface: SparseTileSurface,
        val maskSurface: SparseTileSurface?,
        val visible: Boolean,
        val opacity: Float,
        val blendMode: LayerBlendMode,
        val clipping: Boolean,
        val maskEnabled: Boolean,
    )

    private data class PinnedSurface(val surface: SparseTileSurface, val revision: Long)

    private val pinned = layers.flatMap { layer ->
        buildList {
            add(layer.surface)
            layer.maskSurface?.let { add(it) }
        }
    }.distinct().map { surface -> PinnedSurface(surface, surface.rasterRevision()) }

    private fun validateUnchanged() {
        check(metadataEpoch == currentMetadataEpoch() &&
            pinned.all { it.surface.rasterRevision() == it.revision }) {
            "El documento cambió durante la exportación. Vuelve a intentarlo."
        }
    }

    private fun composerLayers(): List<PreviewCompositor.Layer> =
        layers.map { layer ->
            PreviewCompositor.Layer(
                surface = layer.surface,
                maskSurface = layer.maskSurface,
                visible = layer.visible,
                opacity = layer.opacity,
                blendMode = layer.blendMode,
                clipping = layer.clipping,
                maskEnabled = layer.maskEnabled,
            )
        }

    private fun fullComposite(includePaper: Boolean): Bitmap {
        val requiredBytes = width.toLong() * height.toLong() * 4L
        check(requiredBytes <= Runtime.getRuntime().maxMemory() * .42) {
            "El lienzo es demasiado grande para exportarlo como un bitmap único en este dispositivo."
        }
        return PreviewCompositor().render(
            width = width,
            height = height,
            layers = composerLayers(),
            maxSize = max(width, height),
            includePaper = includePaper,
        )
    }

    /** PNG encoding stays off the UI thread and is verified before publishing. */
    fun writePng(destination: OutputStream, includePaper: Boolean = true) =
        writePngTo({ destination }, includePaper)

    fun writePngTo(openDestination: () -> OutputStream, includePaper: Boolean = true) {
        staged(".png", openDestination) { output ->
            writeCompositePng(output, composerLayers(), includePaper)
        }
    }

    private fun writeCompositePng(
        output: OutputStream,
        renderLayers: List<PreviewCompositor.Layer>,
        includePaper: Boolean,
    ) {
        val compositor = PreviewCompositor()
        writeBandedPng(output, width, height, TileStorage.TILE_SIZE) { band, top, rows ->
            validateUnchanged()
            compositor.renderBand(band, top, rows, renderLayers, includePaper)
        }
    }

    /** Minimal merged-image PSD, deliberately not claiming editable PSD layers. */
    fun writeBasicPsd(destination: OutputStream) = writeBasicPsdTo { destination }

    fun writeBasicPsdTo(openDestination: () -> OutputStream) {
        staged(".psd", openDestination) { output ->
            val bitmap = fullComposite(includePaper = false)
            try {
                BasicPsdCodec.write(bitmap, output)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Editable layers are represented by ORA PNG entries, not a flattened PSD. */
    fun writeOpenRaster(destination: OutputStream) = writeOpenRasterTo { destination }

    fun writeOpenRasterTo(openDestination: () -> OutputStream) {
        require(width.toLong() * height <= MAX_ORA_PIXELS) {
            "El documento es demasiado grande para exportarlo como OpenRaster en este dispositivo."
        }
        require(layers.none { it.clipping }) {
            "OpenRaster no admite exportar capas con recorte todavía. Desactiva el recorte o exporta PNG."
        }
        staged(".ora", openDestination) { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                val mime = "image/openraster".toByteArray(Charsets.US_ASCII)
                val crc = CRC32().apply { update(mime) }
                zip.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong()
                    compressedSize = mime.size.toLong()
                    this.crc = crc.value
                })
                zip.write(mime)
                zip.closeEntry()

                val reversed = layers.asReversed()
                val xml = buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    append("<image version=\"0.0.1\" w=\"").append(width)
                    append("\" h=\"").append(height)
                        .append("\" name=\"Canvas Studio\">\n")
                    append("  <stack name=\"root\">\n")
                    reversed.forEachIndexed { index, layer ->
                        append("    <layer name=\"").append(xmlEscape(layer.name))
                            .append("\" src=\"data/layer")
                            .append(index.toString().padStart(3, '0'))
                            .append(".png\" visibility=\"")
                            .append(if (layer.visible) "visible" else "hidden")
                            .append("\" opacity=\"").append(layer.opacity)
                            .append("\" composite-op=\"").append(oraBlendMode(layer.blendMode))
                            .append("\"/>\n")
                    }
                    append("  </stack>\n</image>\n")
                }
                zip.putNextEntry(ZipEntry("stack.xml"))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val renderLayers = composerLayers()
                renderLayers.asReversed().forEachIndexed { index, layer ->
                    // OpenRaster stores these attributes in stack.xml. Bake only the mask
                    // into the pixels so an external editor does not apply opacity twice.
                    val pixels = layer.copy(
                        visible = true,
                        opacity = 1f,
                        blendMode = LayerBlendMode.NORMAL,
                        clipping = false,
                    )
                    zip.putNextEntry(ZipEntry("data/layer${index.toString().padStart(3, '0')}.png"))
                    writeCompositePng(zip, listOf(pixels), includePaper = false)
                    zip.closeEntry()
                }

                zip.putNextEntry(ZipEntry("mergedimage.png"))
                writeCompositePng(zip, renderLayers, includePaper = false)
                zip.closeEntry()

                val thumbnail = PreviewCompositor().render(
                    width, height, renderLayers, maxSize = 256, includePaper = false,
                )
                try {
                    writeBitmapEntry(zip, "Thumbnails/thumbnail.png", thumbnail)
                } finally {
                    thumbnail.recycle()
                }
            }
        }
    }

    private fun staged(extension: String, openDestination: () -> OutputStream, encode: (OutputStream) -> Unit) {
        validateUnchanged()
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) {
            "No se pudo preparar el espacio temporal para exportar."
        }
        val temporary = File.createTempFile("canvas-export-", extension, cacheDirectory)
        try {
            FileOutputStream(temporary).use(encode)
            // No destination bytes are released if a stroke/layer changed
            // during PNG encoding, PSD flattening or ORA ZIP construction.
            validateUnchanged()
            openDestination().use { destination ->
                FileInputStream(temporary).use { source -> source.copyTo(destination) }
                destination.flush()
            }
        } finally {
            temporary.delete()
        }
    }

    private fun writeBitmapEntry(zip: ZipOutputStream, path: String, bitmap: Bitmap) {
        zip.putNextEntry(ZipEntry(path))
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
            "No se pudo codificar una capa del archivo OpenRaster."
        }
        zip.closeEntry()
    }

    private fun oraBlendMode(mode: LayerBlendMode): String = when (mode) {
        LayerBlendMode.NORMAL -> "svg:src-over"
        LayerBlendMode.MULTIPLY -> "svg:multiply"
        LayerBlendMode.SCREEN -> "svg:screen"
        LayerBlendMode.OVERLAY -> "svg:overlay"
        LayerBlendMode.ADD -> "svg:plus"
        LayerBlendMode.DARKEN -> "svg:darken"
        LayerBlendMode.LIGHTEN -> "svg:lighten"
        LayerBlendMode.SOFT_LIGHT -> "svg:soft-light"
        LayerBlendMode.HARD_LIGHT -> "svg:hard-light"
        LayerBlendMode.DIFFERENCE -> "svg:difference"
        LayerBlendMode.COLOR_DODGE -> "svg:color-dodge"
        LayerBlendMode.COLOR_BURN -> "svg:color-burn"
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private companion object {
        const val MAX_ORA_PIXELS = 40_000_000L
    }
}
