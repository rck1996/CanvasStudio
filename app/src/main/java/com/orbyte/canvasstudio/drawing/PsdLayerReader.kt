package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.abs

/** Bounded PSD v1 raster-layer reader. The caller owns [read]'s input stream. */
internal object PsdLayerReader {
    /** Layers are in PSD file order, bottom to top (the same order as DrawingView). */
    class Imported(
        val width: Int,
        val height: Int,
        val layers: List<Layer>,
        val hasEmbeddedProfile: Boolean = false,
    ) : AutoCloseable {
        override fun close() {
            layers.forEach { it.bitmap?.recycle() }
        }
    }

    data class Layer(
        val name: String,
        val left: Int,
        val top: Int,
        val bitmap: Bitmap?,
        val visible: Boolean,
        val opacity: Float,
        val blendMode: LayerBlendMode,
        val clipping: Boolean,
        val alphaLocked: Boolean,
    )

    private const val MAX_DIMENSION = 30_000
    private const val MAX_PIXELS = 40_000_000L
    private const val MAX_LAYERS = 1_024
    private const val MAX_NAME_UNITS = 4_096
    private const val MAX_PARSED_BYTES = 512L * 1024 * 1024
    private const val MAX_RESOURCE_BYTES = 64L * 1024 * 1024
    private const val MAX_ALLOC_BYTES = 192L * 1024 * 1024
    private const val SCRATCH_BYTES = 8_192

    private data class Channel(val id: Int, val length: Long)
    private data class Record(
        val layer: Layer,
        val width: Int,
        val height: Int,
        val channels: List<Channel>,
    ) {
        val area get() = width * height
    }

    /**
     * Returns null only for a supported, validated composite-only PSD; reopen it with BasicPsdCodec.
     * RGB values are interpreted as sRGB, without ICC conversion. The presence of an ICC resource
     * is reported for layered files; resource payloads and the layered file's merged image are skipped.
     */
    fun read(input: InputStream): Imported? {
        val owned = ArrayList<Layer>()
        var transferred = false
        try {
            val root = Source(BufferedInputStream(input)).root()
            require(root.int() == 0x38425053) { "El archivo no es un PSD válido." } // 8BPS
            require(root.u16() == 1) { "Solo se admite PSD versión 1; PSB no es compatible." }
            root.zeroPadding(6)
            val channels = root.u16()
            val height = root.int()
            val width = root.int()
            val depth = root.u16()
            val mode = root.u16()
            require(depth == 8 && mode == 3 && channels in 3..4) {
                "El PSD debe ser RGB de 8 bits, con un máximo de un canal alfa."
            }
            require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION &&
                width.toLong() * height <= MAX_PIXELS) { "Las dimensiones del PSD exceden el límite de importación." }
            require(root.u32() == 0L) { "El PSD RGB contiene datos de modo de color no compatibles." }
            val resources = root.u32()
            require(resources <= MAX_RESOURCE_BYTES) { "Los recursos del PSD exceden el límite de 64 MiB." }
            val hasEmbeddedProfile = readImageResources(root.section(resources, "recursos de imagen"))

            val layerMask = root.section(root.u32(), "capas y máscaras")
            if (layerMask.remaining > 0) {
                val layerInfo = layerMask.section(layerMask.u32(), "información de capas")
                val records = if (layerInfo.remaining == 0L) {
                    emptyList()
                } else {
                    val count = abs(layerInfo.i16())
                    require(count <= MAX_LAYERS) { "El PSD supera el límite de $MAX_LAYERS capas." }
                    List(count) { readRecord(layerInfo) }
                }
                val declaredChannels = records.sumOf { record -> record.channels.sumOf { it.length } }
                val padding = layerInfo.remaining - declaredChannels
                // Producers use even or four-byte alignment for this section.
                require(padding in 0L..3L) { "Las longitudes de los canales PSD no coinciden con la sección de capas." }
                if (records.isNotEmpty()) {
                    require(!records.first().layer.clipping && records.zipWithNext().none { (below, above) ->
                        below.layer.clipping && above.layer.clipping
                    }) { "El PSD contiene una cadena de recortes no compatible." }
                    checkAllocation(records)
                    owned.ensureCapacity(records.size)
                    records.forEach { record -> owned.add(readLayer(layerInfo, record)) }
                }
                layerInfo.zeroPadding(padding)
                if (layerMask.remaining > 0) {
                    require(layerMask.u32() == 0L) { "Las máscaras globales PSD no son compatibles." }
                    readTags(layerMask, alignment = 4) { key, _ ->
                        require(key !in UNSUPPORTED_GLOBAL_TAGS) {
                            "El PSD usa información de capas, máscaras o color no compatible ($key)."
                        }
                    }
                }
            }
            if (owned.isEmpty()) {
                validateComposite(root, channels, width, height)
                return null
            }
            return Imported(width, height, owned.toList(), hasEmbeddedProfile).also { transferred = true }
        } catch (error: EOFException) {
            throw IllegalArgumentException("PSD truncado: faltan datos del archivo.", error)
        } catch (error: IOException) {
            throw IllegalArgumentException("No se pudo leer el archivo PSD.", error)
        } catch (error: OutOfMemoryError) {
            throw IllegalArgumentException("No hay memoria suficiente para importar las capas del PSD.", error)
        } finally {
            if (!transferred) owned.forEach { it.bitmap?.recycle() }
        }
    }

    private fun readImageResources(data: Section): Boolean {
        var hasProfile = false
        while (data.remaining > 0) {
            require(data.code() == "8BIM") { "Firma de recurso de imagen PSD inválida." }
            val id = data.u16()
            val nameSize = data.u8()
            data.skip(nameSize.toLong())
            if (nameSize % 2 == 0) data.zeroPadding(1)
            val length = data.u32()
            data.skip(length)
            if (length % 2L != 0L) data.zeroPadding(1)
            if (id == 0x040f && length > 0L) hasProfile = true // ICC profile
        }
        return hasProfile
    }

    private fun readRecord(data: Section): Record {
        val top = data.int()
        val left = data.int()
        val h = data.int().toLong() - top
        val w = data.int().toLong() - left
        // Photoshop can use -1 on one axis for an empty placeholder.
        val empty = w <= 0L && h <= 0L && w >= -1L && h >= -1L
        require(empty || (w in 0L..MAX_DIMENSION.toLong() && h in 0L..MAX_DIMENSION.toLong())) {
            "Una capa PSD tiene límites de píxeles inválidos o demasiado grandes."
        }
        val width = if (empty) 0 else w.toInt()
        val height = if (empty) 0 else h.toInt()
        val area = width.toLong() * height
        require(area <= MAX_PIXELS) { "Una capa PSD supera el límite de píxeles." }
        val channelCount = data.u16()
        require(channelCount <= 56) { "Cantidad de canales de capa PSD inválida." }
        var channelBits = 0
        val channels = List(channelCount) {
            val id = data.i16()
            require(id != -2 && id != -3) { "Las máscaras editables PSD no son compatibles. Aplica la máscara antes de exportar." }
            require(id in -1..2) { "Canal de capa PSD no compatible ($id)." }
            val bit = 1 shl (id + 1)
            require(channelBits and bit == 0) { "Una capa PSD contiene canales duplicados." }
            channelBits = channelBits or bit
            val length = data.u32()
            require(length >= 2 || (area == 0L && length == 0L)) { "Longitud de canal de capa PSD inválida." }
            Channel(id, length)
        }

        require(data.code() == "8BIM") { "Firma del modo de fusión PSD inválida." }
        val blendKey = data.code()
        val opacity = data.u8() / 255f
        val clipping = data.u8()
        require(clipping in 0..1) { "Valor de recorte de capa PSD inválido." }
        val flags = data.u8()
        val visible = flags and 2 == 0
        var alphaLocked = flags and 1 != 0
        data.zeroPadding(1)
        val extra = data.section(data.u32(), "datos de capa")
        require(extra.u32() == 0L) { "Las máscaras editables PSD no son compatibles. Aplica la máscara antes de exportar." }
        val blending = extra.section(extra.u32(), "rangos de fusión")
        require(blending.remaining in 0L..456L && blending.remaining % 8L == 0L) {
            "Longitud de rangos de fusión PSD inválida."
        }
        while (blending.remaining != 0L) {
            require(blending.int() == 0x0000ffff) { "Los rangos de fusión personalizados (Blend If) no son compatibles." }
        }
        val nameSize = extra.u8()
        val legacyName = String(extra.bytes(nameSize), Charsets.ISO_8859_1)
        extra.zeroPadding((4 - (nameSize + 1) % 4) % 4)
        var unicodeName: String? = null
        readTags(extra, alignment = 2) { key, block ->
            when (key) {
                "luni" -> {
                    require(unicodeName == null) { "Una capa PSD contiene nombres Unicode duplicados." }
                    val units = block.u32()
                    require(units <= MAX_NAME_UNITS) { "El nombre de una capa PSD es demasiado largo." }
                    unicodeName = String(block.bytes((2 * units).toInt()), Charsets.UTF_16BE).trimEnd('\u0000')
                    block.zeroPadding(max = 3L)
                }
                "lsct", "lsdk" -> {
                    require(block.u32() == 0L) { "Los grupos de capas PSD no son compatibles." }
                }
                "lspf" -> {
                    val protected = block.int()
                    alphaLocked = alphaLocked || protected and 1 != 0
                }
                "iOpa" -> require(block.u8() == 255) { "La opacidad de relleno PSD no es compatible." }
                "clbl", "tsly" -> require(block.u8() == 1) { "La configuración de fusión PSD ($key) no es compatible." }
                "infx", "knko" -> require(block.u8() == 0) { "La configuración de fusión PSD ($key) no es compatible." }
                in RASTER_METADATA_TAGS -> Unit
                else -> throw IllegalArgumentException(
                    "El PSD contiene una función de capa no compatible ($key). Rasteriza la capa antes de exportar.",
                )
            }
        }
        require(flags and 0x18 != 0x18) { "El PSD contiene una capa no rasterizada." }
        require(area == 0L || channelBits and 0b1110 == 0b1110) { "Faltan canales RGB en una capa PSD." }
        val blend = BLEND_MODES[blendKey]
            ?: throw IllegalArgumentException("Modo de fusión PSD no compatible ($blendKey).")
        val layer = Layer(unicodeName ?: legacyName, left, top, null, visible, opacity, blend, clipping == 1, alphaLocked)
        return Record(layer, width, height, channels)
    }

    private fun checkAllocation(records: List<Record>) {
        val area = records.sumOf { it.area.toLong() }
        val maxArea = records.maxOf { it.area }.toLong()
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val budget = minOf(MAX_ALLOC_BYTES, runtime.maxMemory() / 2, available / 2)
        // Retained ARGB bitmaps + the largest working ARGB array + row, ZIP and record overhead.
        require(area <= MAX_PIXELS && 4L * (area + maxArea) + 2L * 1024 * 1024 <= budget) {
            "Las capas PSD exceden el presupuesto de memoria disponible para la importación."
        }
    }

    private fun readLayer(data: Section, record: Record): Layer {
        val pixels = if (record.area == 0) null else IntArray(record.area) { 0xff000000.toInt() }
        val row = ByteArray(if (pixels == null) 0 else record.width)
        for (channel in record.channels) {
            val section = data.section(channel.length, "canal de capa")
            if (channel.length == 0L) continue
            val compression = section.u16()
            val height = if (pixels == null) 0 else record.height
            val shift = when (channel.id) {
                -1 -> 24
                0 -> 16
                1 -> 8
                else -> 0
            }
            val mask = (0xff shl shift).inv()
            fun accept(y: Int) {
                val target = checkNotNull(pixels)
                var offset = y * row.size
                for (value in row) {
                    target[offset] = (target[offset] and mask) or ((value.toInt() and 255) shl shift)
                    offset++
                }
            }
            when (compression) {
                0 -> repeat(height) { y ->
                    section.read(row)
                    accept(y)
                }
                1 -> {
                    val lengths = IntArray(height) { section.u16() }
                    lengths.forEachIndexed { y, length ->
                        decodePackBits(section.section(length.toLong(), "fila PackBits"), row)
                        accept(y)
                    }
                }
                2, 3 -> readZip(section, row, height, predict = compression == 3, accept = ::accept)
                else -> throw IllegalArgumentException("Compresión de canal PSD no compatible ($compression).")
            }
            if (compression <= 1) section.zeroPadding(max = 1L)
        }
        if (pixels == null) return record.layer
        val bitmap = Bitmap.createBitmap(pixels, record.width, record.height, Bitmap.Config.ARGB_8888)
        return try {
            record.layer.copy(bitmap = bitmap)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun decodePackBits(encoded: Section, row: ByteArray) {
        var out = 0
        while (encoded.remaining > 0L) {
            val code = encoded.u8()
            if (code == 128) continue // PackBits no-op, including at the end of a row.
            // Some writers include a single alignment byte in the row length.
            if (out == row.size && code == 0 && encoded.remaining == 0L) break
            val size = if (code < 128) code + 1 else 257 - code
            require(size <= row.size - out) { "Una fila PackBits del PSD excede el ancho de la capa." }
            if (code < 128) {
                encoded.read(row, out, size)
            } else {
                row.fill(encoded.u8().toByte(), out, out + size)
            }
            out += size
        }
        require(out == row.size) { "Una fila PackBits del PSD está incompleta." }
    }

    private fun readZip(
        section: Section,
        row: ByteArray,
        height: Int,
        predict: Boolean,
        accept: (Int) -> Unit,
    ) {
        val inflater = Inflater()
        try {
            val compressed = ByteArray(SCRATCH_BYTES)
            var lastInputSize = 0
            fun inflate(target: ByteArray, offset: Int, length: Int): Int {
                while (!inflater.finished()) {
                    if (inflater.needsInput()) {
                        lastInputSize = minOf(compressed.size.toLong(), section.remaining).toInt()
                        require(lastInputSize > 0) { "Un canal ZIP del PSD está truncado." }
                        section.read(compressed, length = lastInputSize)
                        inflater.setInput(compressed, 0, lastInputSize)
                    }
                    val count = inflater.inflate(target, offset, length)
                    if (count != 0 || inflater.finished()) return count
                    require(inflater.needsInput() && !inflater.needsDictionary()) { "Un canal ZIP del PSD está dañado." }
                }
                return 0
            }
            repeat(height) { y ->
                var offset = 0
                while (offset < row.size) {
                    val count = inflate(row, offset, row.size - offset)
                    require(count > 0) { "Un canal ZIP del PSD contiene menos píxeles de los esperados." }
                    offset += count
                }
                if (predict) for (i in 1 until row.size) row[i] = (row[i] + row[i - 1]).toByte()
                accept(y)
            }
            require(inflate(ByteArray(1), 0, 1) == 0 && inflater.finished()) {
                "Un canal ZIP del PSD contiene más píxeles de los esperados."
            }
            val unused = inflater.remaining
            require(unused.toLong() + section.remaining <= 1L &&
                (unused == 0 || compressed[lastInputSize - 1] == 0.toByte())) {
                "Un canal ZIP del PSD contiene datos sobrantes."
            }
            section.zeroPadding(max = 1L)
        } catch (error: DataFormatException) {
            throw IllegalArgumentException("Un canal ZIP del PSD está dañado.", error)
        } finally {
            inflater.end()
        }
    }

    private fun validateComposite(data: Section, channels: Int, width: Int, height: Int) {
        when (val compression = data.u16()) {
            0 -> data.skip(channels.toLong() * width * height)
            1 -> {
                val lengths = IntArray(channels * height) { data.u16() }
                val row = ByteArray(width)
                for (length in lengths) decodePackBits(data.section(length.toLong(), "fila compuesta PackBits"), row)
            }
            else -> throw IllegalArgumentException("Compresión PSD de imagen compuesta no compatible ($compression).")
        }
        require(data.atEnd()) { "Sobran datos después de la imagen compuesta PSD." }
    }

    private inline fun readTags(data: Section, alignment: Int, consume: (String, Section) -> Unit) {
        while (data.remaining >= 12L) {
            val signature = data.code()
            require(signature == "8BIM" || signature == "8B64") { "Firma de bloque adicional PSD inválida." }
            val key = data.code()
            val length = data.u32()
            val block = data.section(length, "bloque adicional $key")
            consume(key, block)
            block.skip(block.remaining)
            // Length may already include padding, or the next tag may be packed without it.
            val padding = ((alignment - length % alignment) % alignment).toInt()
            repeat(padding) {
                if (data.remaining > 0 && data.peek() == 0) data.u8()
            }
        }
        data.zeroPadding(max = 3L)
    }

    private val RASTER_METADATA_TAGS = setOf(
        "lyid", "lyvr", "lnsr", "lclr", "fxrp", "shmd", "lmgm", "vmgm", "lyiD",
    )

    private val UNSUPPORTED_GLOBAL_TAGS = setOf("Layr", "Lr16", "Lr32", "LMsk", "FMsk", "OCIO")

    private val BLEND_MODES = mapOf(
        "norm" to LayerBlendMode.NORMAL,
        "mul " to LayerBlendMode.MULTIPLY,
        "scrn" to LayerBlendMode.SCREEN,
        "over" to LayerBlendMode.OVERLAY,
        "lddg" to LayerBlendMode.ADD,
        "dark" to LayerBlendMode.DARKEN,
        "lite" to LayerBlendMode.LIGHTEN,
        "sLit" to LayerBlendMode.SOFT_LIGHT,
        "hLit" to LayerBlendMode.HARD_LIGHT,
        "diff" to LayerBlendMode.DIFFERENCE,
        "div " to LayerBlendMode.COLOR_DODGE,
        "idiv" to LayerBlendMode.COLOR_BURN,
    )

    /** One cursor shared by nested sections. No section can read or skip into its sibling. */
    private class Source(input: BufferedInputStream) {
        val stream = DataInputStream(input)
        val scratch = ByteArray(SCRATCH_BYTES)
        var position = 0L

        fun root() = Section(this, MAX_PARSED_BYTES, "archivo")
    }

    private class Section(private val source: Source, private val end: Long, private val label: String) {
        val remaining get() = end - source.position

        private fun reserve(size: Long) {
            require(size in 0L..remaining) { "Longitud inválida en la sección PSD «$label»." }
            source.position += size
        }

        fun section(size: Long, name: String): Section {
            require(size in 0L..remaining) { "Longitud inválida en la sección PSD «$name»." }
            return Section(source, source.position + size, name)
        }

        fun u8(): Int {
            reserve(1)
            return source.stream.readUnsignedByte()
        }

        fun u16(): Int {
            reserve(2)
            return source.stream.readUnsignedShort()
        }

        fun i16(): Int = u16().toShort().toInt()

        fun int(): Int {
            reserve(4)
            return source.stream.readInt()
        }

        fun u32(): Long = int().toLong() and 0xffffffffL

        fun code(): String = String(bytes(4), Charsets.ISO_8859_1)

        fun bytes(size: Int) = ByteArray(size).also { read(it) }

        fun read(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
            reserve(length.toLong())
            source.stream.readFully(bytes, offset, length)
        }

        fun skip(size: Long) {
            reserve(size)
            var left = size
            while (left > 0L) {
                val chunk = minOf(left, source.scratch.size.toLong()).toInt()
                source.stream.readFully(source.scratch, 0, chunk)
                left -= chunk
            }
        }

        fun zeroPadding(count: Int) {
            repeat(count) { require(u8() == 0) { "Relleno inválido en la sección PSD «$label»." } }
        }

        fun zeroPadding(max: Long) {
            require(remaining in 0L..max) { "Sobran datos en la sección PSD «$label»." }
            zeroPadding(remaining.toInt())
        }

        fun peek(): Int {
            source.stream.mark(1)
            val byte = source.stream.read()
            source.stream.reset()
            return byte
        }

        fun atEnd() = peek() == -1
    }
}
