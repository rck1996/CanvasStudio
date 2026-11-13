package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

/** Editable RGB8 raster stack. PSD planar data is staged in bands, never a full canvas. */
internal object PsdLayerWriter {
    data class Raster(val bounds: Rect, val renderBand: (Bitmap, top: Int, rows: Int) -> Unit)

    data class Layer(
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val blendMode: LayerBlendMode,
        val clipping: Boolean,
        val alphaLocked: Boolean,
        val raster: Raster,
    )

    fun write(file: File, width: Int, height: Int, layers: List<Layer>, composite: Raster) {
        require(width in 1..30_000 && height in 1..30_000 && width.toLong() * height <= 40_000_000L)
        require(layers.size in 1..Short.MAX_VALUE.toInt())
        require(composite.bounds == Rect(0, 0, width, height))
        require(!layers.first().clipping && layers.zipWithNext().none { (a, b) -> a.clipping && b.clipping }) {
            "PSD no puede conservar esta cadena de recortes. Exporta PNG para conservar su apariencia."
        }
        val records = layers.map { layer ->
            val bounds = layer.raster.bounds
            require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= width && bounds.bottom <= height &&
                bounds.width() >= 0 && bounds.height() >= 0)
            layerRecord(layer)
        }
        val unpadded = 2L + records.sumOf { it.size.toLong() } +
            layers.sumOf { (pixelCount(it.raster.bounds) + 2) * 4 }
        val layerInfoLength = unpadded + (unpadded and 1)
        val layerAndMaskLength = 4 + layerInfoLength + 4
        val fileSize = 38 + layerAndMaskLength + 2 + pixelCount(composite.bounds) * 4
        require(fileSize <= 1_500_000_000L) { "El PSD por capas superaría el límite de 1,5 GB." }
        require(file.parentFile?.usableSpace?.let { it > fileSize + 16 * 1024 * 1024 } == true) {
            "No hay espacio temporal suficiente para exportar el PSD por capas."
        }

        RandomAccessFile(file, "rw").use { out ->
            out.writeInt(0x38425053) // 8BPS
            out.writeShort(1)
            out.write(ByteArray(6))
            out.writeShort(4)
            out.writeInt(height)
            out.writeInt(width)
            out.writeShort(8)
            out.writeShort(3) // RGB
            out.writeInt(0) // Color mode data
            out.writeInt(0) // No profile: the interchange policy is untagged sRGB.
            out.writeInt(layerAndMaskLength.toInt())
            out.writeInt(layerInfoLength.toInt())
            out.writeShort(-layers.size) // First extra merged channel is transparency.
            records.forEach(out::write)
            layers.forEach { writeRaster(out, it.raster, LAYER_SHIFTS, layerChannels = true) }
            if (unpadded and 1 != 0L) out.writeByte(0)
            out.writeInt(0) // Global layer mask data
            writeRaster(out, composite, COMPOSITE_SHIFTS, layerChannels = false)
            check(out.filePointer == fileSize)
        }
    }

    private fun layerRecord(layer: Layer): ByteArray = bytes {
        val bounds = layer.raster.bounds
        writeInt(bounds.top); writeInt(bounds.left); writeInt(bounds.bottom); writeInt(bounds.right)
        writeShort(4)
        for (id in intArrayOf(-1, 0, 1, 2)) {
            writeShort(id)
            writeInt((pixelCount(bounds) + 2).toInt())
        }
        writeBytes("8BIM")
        writeBytes(blendKey(layer.blendMode))
        writeByte((layer.opacity * 255f).toInt().coerceIn(0, 255))
        writeByte(if (layer.clipping) 1 else 0)
        writeByte((if (layer.alphaLocked) 1 else 0) or (if (layer.visible) 0 else 2))
        writeByte(0)
        val extra = bytes {
            writeInt(0) // User mask already applied to layer alpha.
            writeInt(0) // Blending ranges
            val name = layer.name.ifBlank { "Capa" }.take(255)
            val ascii = name.map { if (it in ' '..'~') it else '?' }.joinToString("")
            writeByte(ascii.length)
            writeBytes(ascii)
            repeat((4 - (1 + ascii.length) % 4) % 4) { writeByte(0) }
            writeBytes("8BIM")
            writeBytes("luni")
            writeInt(4 + name.length * 2)
            writeInt(name.length)
            writeChars(name)
        }
        writeInt(extra.size)
        write(extra)
    }

    private fun writeRaster(out: RandomAccessFile, raster: Raster, shifts: IntArray, layerChannels: Boolean) {
        val bounds = raster.bounds
        val pixels = pixelCount(bounds)
        val channelStride = pixels + if (layerChannels) 2 else 0
        if (!layerChannels) out.writeShort(0) // One raw compression field for the merged image.
        val start = out.filePointer
        if (layerChannels) repeat(4) { channel ->
            out.seek(start + channel * channelStride)
            out.writeShort(0)
        }
        if (pixels > 0) {
            val width = bounds.width()
            val band = Bitmap.createBitmap(width, minOf(TileStorage.TILE_SIZE, bounds.height()), Bitmap.Config.ARGB_8888)
            val blockRows = minOf(32, band.height)
            val argb = IntArray(width * blockRows)
            val plane = ByteArray(argb.size)
            try {
                for (top in bounds.top until bounds.bottom step band.height) {
                    val rows = minOf(band.height, bounds.bottom - top)
                    raster.renderBand(band, top, rows)
                    for (offset in 0 until rows step blockRows) {
                        val countRows = minOf(blockRows, rows - offset)
                        val count = countRows * width
                        band.getPixels(argb, 0, width, 0, offset, width, countRows)
                        for ((channel, shift) in shifts.withIndex()) {
                            for (i in 0 until count) plane[i] = (argb[i] ushr shift).toByte()
                            out.seek(start + channel * channelStride + (if (layerChannels) 2 else 0) +
                                (top - bounds.top + offset).toLong() * width)
                            out.write(plane, 0, count)
                        }
                    }
                }
            } finally {
                band.recycle()
            }
        }
        out.seek(start + shifts.size * channelStride)
    }

    private fun pixelCount(bounds: Rect): Long = bounds.width().toLong() * bounds.height()

    private fun bytes(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use(write) }.toByteArray()

    private fun blendKey(mode: LayerBlendMode): String = when (mode) {
        LayerBlendMode.NORMAL -> "norm"
        LayerBlendMode.MULTIPLY -> "mul "
        LayerBlendMode.SCREEN -> "scrn"
        LayerBlendMode.OVERLAY -> "over"
        LayerBlendMode.ADD -> "lddg"
        LayerBlendMode.DARKEN -> "dark"
        LayerBlendMode.LIGHTEN -> "lite"
        LayerBlendMode.SOFT_LIGHT -> "sLit"
        LayerBlendMode.HARD_LIGHT -> "hLit"
        LayerBlendMode.DIFFERENCE -> "diff"
        LayerBlendMode.COLOR_DODGE -> "div "
        LayerBlendMode.COLOR_BURN -> "idiv"
    }

    private val LAYER_SHIFTS = intArrayOf(24, 16, 8, 0)
    private val COMPOSITE_SHIFTS = intArrayOf(16, 8, 0, 24)
}
