package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicPsdCodecTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsRleRunExceedingImageWidth() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { data ->
            data.writeInt(0x38425053) // 8BPS
            data.writeShort(1)
            repeat(6) { data.writeByte(0) }
            data.writeShort(3)
            data.writeInt(1) // height
            data.writeInt(1) // width
            data.writeShort(8)
            data.writeShort(3) // RGB
            repeat(3) { data.writeInt(0) }
            data.writeShort(1) // PackBits
            repeat(3) { data.writeShort(2) }
            data.writeByte(-2) // Three decoded pixels for a one-pixel row.
            data.writeByte(0x7f)
            repeat(2) { data.writeByte(0); data.writeByte(0) }
        }
        BasicPsdCodec.read(ByteArrayInputStream(bytes.toByteArray()))
    }

    @Test
    fun rgbaCompositeRoundTripPreservesDimensionsAndPixels() {
        val sourcePixels = intArrayOf(
            0xffff0000.toInt(),
            0x8000ff00.toInt(),
            0x400000ff,
            0x10ffffff,
        )
        val source = Bitmap.createBitmap(sourcePixels, 2, 2, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream()

        BasicPsdCodec.write(source, encoded)
        val decoded = BasicPsdCodec.read(ByteArrayInputStream(encoded.toByteArray()))
        val decodedPixels = IntArray(4)
        decoded.getPixels(decodedPixels, 0, 2, 0, 0, 2, 2)

        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertArrayEquals(sourcePixels, decodedPixels)
        source.recycle()
        decoded.recycle()
    }
}
