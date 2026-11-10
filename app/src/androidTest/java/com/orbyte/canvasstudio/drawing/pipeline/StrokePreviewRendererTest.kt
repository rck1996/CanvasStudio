package com.orbyte.canvasstudio.drawing.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokePreviewRendererTest {
    @Test
    fun translatedSymmetryDrawsInExpectedRegion() {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            val renderer = StrokePreviewRenderer()
            val points = listOf(
                StrokePoint(4f, 8f, 1f, 0f, 1L),
                StrokePoint(12f, 8f, 1f, 0f, 2L),
            )
            renderer.draw(
                Canvas(bitmap), points,
                BrushSettings(sizePx = 4f, color = Color.RED),
                DrawingTool.BRUSH,
                listOf(Matrix(), Matrix().apply { setTranslate(30f, 0f) }),
            )
            assertTrue(Color.alpha(bitmap.getPixel(8, 8)) > 0)
            assertTrue(Color.alpha(bitmap.getPixel(38, 8)) > 0)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(25, 8))
        } finally {
            bitmap.recycle()
        }
    }
}
