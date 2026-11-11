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
    fun identityFastPathRetainsExactPressureSegmentPixels() {
        val optimized = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val reference = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val settings = BrushSettings(sizePx = 13f, color = Color.BLUE, opacity = .8f)
        val points = listOf(
            StrokePoint(13f, 19f, .35f, 0f, 1L),
            StrokePoint(25f, 27f, .5f, 0f, 2L),
            StrokePoint(38f, 53f, .8f, 0f, 3L),
            StrokePoint(63f, 70f, .6f, 0f, 4L),
        )
        try {
            StrokePreviewRenderer().draw(
                Canvas(optimized), points, settings,
                DrawingTool.BRUSH, listOf(Matrix()),
            )
            val legacyCanvas = Canvas(reference)
            val legacyMatrix = Matrix()
            val legacyEndpoints = FloatArray(4)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = settings.color
                alpha = (settings.opacity.coerceIn(.15f, 1f) * 220f).toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            var previous = points.first()
            for (index in 1 until points.size) {
                val current = points[index]
                legacyEndpoints[0] = previous.x
                legacyEndpoints[1] = previous.y
                legacyEndpoints[2] = current.x
                legacyEndpoints[3] = current.y
                legacyMatrix.mapPoints(legacyEndpoints)
                paint.strokeWidth = settings.sizePx *
                    ((previous.pressure + current.pressure) * 0.5f)
                        .coerceIn(settings.minSize, 1f)
                legacyCanvas.drawLine(
                    legacyEndpoints[0], legacyEndpoints[1],
                    legacyEndpoints[2], legacyEndpoints[3], paint,
                )
                previous = current
            }
            for (y in 0 until 96) for (x in 0 until 96) {
                assertEquals(
                    "Identity preview regression at $x,$y",
                    reference.getPixel(x, y), optimized.getPixel(x, y),
                )
            }
        } finally {
            optimized.recycle()
            reference.recycle()
        }
    }

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
