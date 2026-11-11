package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileCanvasScopeTest {
    private val paint = Paint().apply { color = Color.RED }

    @Test
    fun documentCoordinatesAndTileClippingMatchFreshCanvas() {
        val expected = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val referenceCanvas = Canvas(expected)
            referenceCanvas.translate(-512f, -1024f)
            referenceCanvas.clipRect(512f, 1024f, 528f, 1040f)
            referenceCanvas.drawRect(508f, 1020f, 520f, 1032f, paint)
            val scope = TileCanvasScope()
            scope.drawTile(actual, 512, 1024) { canvas, bounds ->
                assertEquals(RectF(512f, 1024f, 528f, 1040f), RectF(bounds))
                canvas.drawRect(508f, 1020f, 520f, 1032f, paint)
            }
            for (y in 0 until 16) for (x in 0 until 16) {
                assertEquals("Pixel $x,$y", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        } finally {
            expected.recycle()
            actual.recycle()
        }
    }

    @Test
    fun exceptionCannotLeakClipOrBitmapToNextTile() {
        val first = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            val scope = TileCanvasScope()
            var threw = false
            try {
                scope.drawTile(first, 512, 512) { canvas, _ ->
                    canvas.drawColor(Color.BLUE)
                    throw IllegalStateException("Test failure")
                }
            } catch (_: IllegalStateException) {
                threw = true
            }
            assertTrue(threw)
            scope.drawTile(second, 0, 0) { canvas, _ ->
                canvas.drawRect(0f, 0f, 8f, 8f, paint)
            }
            assertEquals(Color.RED, second.getPixel(4, 4))
            assertEquals(Color.BLUE, first.getPixel(4, 4))
        } finally {
            first.recycle()
            second.recycle()
        }
    }
}
