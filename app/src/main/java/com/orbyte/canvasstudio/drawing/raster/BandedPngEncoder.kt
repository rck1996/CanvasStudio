package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.min

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private const val PNG_IDAT_BUFFER_BYTES = 64 * 1024

private fun writePngInt(output: OutputStream, value: Int) {
    output.write(value ushr 24)
    output.write(value ushr 16)
    output.write(value ushr 8)
    output.write(value)
}

private fun writePngChunk(output: OutputStream, type: String, data: ByteArray, offset: Int = 0, length: Int = data.size) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    require(typeBytes.size == 4) { "Un chunk PNG debe tener un tipo de cuatro caracteres." }
    writePngInt(output, length)
    output.write(typeBytes)
    output.write(data, offset, length)
    val crc = CRC32().apply {
        update(typeBytes)
        update(data, offset, length)
    }
    writePngInt(output, crc.value.toInt())
}

/** Buffers compressed data into valid PNG IDAT chunks without closing the caller's stream. */
private class PngIdatOutputStream(private val output: OutputStream) : OutputStream() {
    private val buffer = ByteArray(PNG_IDAT_BUFFER_BYTES)
    private var size = 0
    private var finished = false

    override fun write(value: Int) {
        ensureOpen()
        if (size == buffer.size) flushChunk()
        buffer[size++] = value.toByte()
    }

    override fun write(source: ByteArray, offset: Int, length: Int) {
        ensureOpen()
        var sourceOffset = offset
        var remaining = length
        while (remaining > 0) {
            if (size == buffer.size) flushChunk()
            val copied = min(buffer.size - size, remaining)
            source.copyInto(buffer, destinationOffset = size, startIndex = sourceOffset, endIndex = sourceOffset + copied)
            size += copied
            sourceOffset += copied
            remaining -= copied
        }
    }

    override fun flush() = Unit

    override fun close() = finish()

    fun finish() {
        if (finished) return
        flushChunk()
        finished = true
    }

    private fun flushChunk() {
        if (size == 0) return
        writePngChunk(output, "IDAT", buffer, length = size)
        size = 0
    }

    private fun ensureOpen() {
        check(!finished) { "El codificador PNG ya fue finalizado." }
    }
}

/** Encodes straight RGBA scanlines from a reusable premultiplied Android bitmap. */
internal fun writeBandedPng(
    output: OutputStream,
    width: Int,
    height: Int,
    bandHeight: Int,
    renderBand: (Bitmap, Int, Int) -> Unit,
) {
    val band = Bitmap.createBitmap(width, min(height, bandHeight), Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width)
    val scanline = ByteArray(width * 4 + 1)
    val deflater = Deflater()
    try {
        output.write(PNG_SIGNATURE)
        val header = ByteArrayOutputStream(13)
        writePngInt(header, width)
        writePngInt(header, height)
        header.write(byteArrayOf(8, 6, 0, 0, 0))
        writePngChunk(output, "IHDR", header.toByteArray())

        val chunks = PngIdatOutputStream(output)
        val compressed = DeflaterOutputStream(chunks, deflater, PNG_IDAT_BUFFER_BYTES)
        var top = 0
        while (top < height) {
            val rows = min(band.height, height - top)
            renderBand(band, top, rows)
            repeat(rows) { row ->
                band.getPixels(pixels, 0, width, 0, row, width, 1)
                var offset = 1 // PNG filter type 0.
                for (argb in pixels) {
                    scanline[offset++] = (argb ushr 16).toByte()
                    scanline[offset++] = (argb ushr 8).toByte()
                    scanline[offset++] = argb.toByte()
                    scanline[offset++] = (argb ushr 24).toByte()
                }
                compressed.write(scanline)
            }
            top += rows
        }
        compressed.finish()
        chunks.finish()
        writePngChunk(output, "IEND", ByteArray(0))
    } finally {
        deflater.end()
        band.recycle()
    }
}
