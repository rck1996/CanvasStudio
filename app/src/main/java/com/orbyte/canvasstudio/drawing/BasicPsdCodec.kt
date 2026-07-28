package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal PSD v1 composite codec.
 *
 * It intentionally exchanges the merged RGBA image instead of claiming full PSD layer
 * compatibility. The document's editable layer stack remains available through OpenRaster.
 */
object BasicPsdCodec {
    private const val SIGNATURE = 0x38425053 // 8BPS
    private const val COLOR_MODE_RGB = 3
    private const val MAX_PIXELS = 40_000_000L

    fun write(bitmap: Bitmap, output: OutputStream) {
        require(bitmap.width.toLong() * bitmap.height <= MAX_PIXELS) {
            "El PSD básico admite hasta 32 megapíxeles."
        }
        DataOutputStream(BufferedOutputStream(output)).use { data ->
            data.writeInt(SIGNATURE)
            data.writeShort(1)
            repeat(6) { data.writeByte(0) }
            data.writeShort(4)
            data.writeInt(bitmap.height)
            data.writeInt(bitmap.width)
            data.writeShort(8)
            data.writeShort(COLOR_MODE_RGB)
            data.writeInt(0) // Color mode data
            data.writeInt(0) // Image resources
            data.writeInt(0) // Layer and mask information
            data.writeShort(0) // Raw planar image data

            val pixels = IntArray(bitmap.width)
            repeat(4) { channel ->
                repeat(bitmap.height) { y ->
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                    pixels.forEach { pixel ->
                        data.writeByte(
                            when (channel) {
                                0 -> pixel ushr 16
                                1 -> pixel ushr 8
                                2 -> pixel
                                else -> pixel ushr 24
                            } and 0xff,
                        )
                    }
                }
            }
        }
    }

    fun read(input: InputStream): Bitmap {
        DataInputStream(BufferedInputStream(input)).use { data ->
            require(data.readInt() == SIGNATURE) { "El archivo no es un PSD válido." }
            require(data.readUnsignedShort() == 1) { "Solo se admite PSD, no PSB." }
            skipFully(data, 6)
            val channels = data.readUnsignedShort()
            val height = data.readInt()
            val width = data.readInt()
            val depth = data.readUnsignedShort()
            val colorMode = data.readUnsignedShort()
            require(channels >= 3 && width > 0 && height > 0 && depth == 8 && colorMode == COLOR_MODE_RGB) {
                "El PSD debe ser RGB de 8 bits."
            }
            require(width.toLong() * height <= MAX_PIXELS) {
                "El PSD básico admite hasta 32 megapíxeles."
            }
            repeat(3) {
                val sectionLength = data.readInt().toLong() and 0xffffffffL
                skipFully(data, sectionLength)
            }
            val compression = data.readUnsignedShort()
            require(compression == 0 || compression == 1) {
                "Compresión PSD no compatible."
            }

            val rowLengths = if (compression == 1) {
                Array(channels) { IntArray(height) { data.readUnsignedShort() } }
            } else {
                emptyArray()
            }
            val pixels = IntArray(width * height) { 0xff000000.toInt() }
            val row = ByteArray(width)
            repeat(channels) { channel ->
                repeat(height) { y ->
                    if (compression == 0) {
                        data.readFully(row)
                    } else {
                        decodePackBits(data, rowLengths[channel][y], row)
                    }
                    if (channel < 4) {
                        val offset = y * width
                        repeat(width) { x ->
                            val value = row[x].toInt() and 0xff
                            val current = pixels[offset + x]
                            pixels[offset + x] = when (channel) {
                                0 -> (current and 0xff00ffff.toInt()) or (value shl 16)
                                1 -> (current and 0xffff00ff.toInt()) or (value shl 8)
                                2 -> (current and 0xffffff00.toInt()) or value
                                else -> (current and 0x00ffffff) or (value shl 24)
                            }
                        }
                    }
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }

    private fun decodePackBits(data: DataInputStream, encodedLength: Int, target: ByteArray) {
        val encoded = ByteArray(encodedLength)
        data.readFully(encoded)
        var source = 0
        var destination = 0
        while (source < encoded.size && destination < target.size) {
            val header = encoded[source++].toInt()
            when {
                header in 0..127 -> {
                    val count = (header + 1).coerceAtMost(target.size - destination)
                    require(source + count <= encoded.size) { "Fila RLE PSD dañada." }
                    encoded.copyInto(target, destination, source, source + count)
                    source += count
                    destination += count
                }
                header in -127..-1 -> {
                    require(source < encoded.size) { "Fila RLE PSD dañada." }
                    val value = encoded[source++]
                    val count = (1 - header).coerceAtMost(target.size - destination)
                    target.fill(value, destination, destination + count)
                    destination += count
                }
            }
        }
        require(destination == target.size) { "Fila RLE PSD incompleta." }
    }

    private fun skipFully(data: DataInputStream, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = data.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                require(data.read() >= 0) { "PSD truncado." }
                remaining--
            }
        }
    }
}
