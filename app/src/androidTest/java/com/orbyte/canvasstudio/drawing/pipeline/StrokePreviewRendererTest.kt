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
    fun identityFastPathMatchesMappedGeometryWithSameBrushDynamics() {
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
            StrokePreviewRenderer().draw(
                Canvas(reference), points.map { it.copy(x = it.x - 8f, y = it.y + 4f) }, settings,
                DrawingTool.BRUSH, listOf(Matrix().apply { setTranslate(8f, -4f) }),
            )
            assertTrue(optimized.sameAs(reference))
        } finally {
            optimized.recycle()
            reference.recycle()
        }
    }

    @Test
    fun previewHasNoOpacityFloorAndRespectsDisabledSizePressure() {
        val renderer = StrokePreviewRenderer()
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        val base = BrushSettings(sizePx = 28f, color = Color.BLUE, pressureSize = false, opacity = 1f)
        fun sample(pressure: Float, settings: BrushSettings): Pair<Int, Int> {
            bitmap.eraseColor(Color.TRANSPARENT)
            val points = listOf(
                StrokePoint(16f, 32f, pressure, 0f, 0L),
                StrokePoint(112f, 32f, pressure, 0f, 120L),
            )
            renderer.draw(Canvas(bitmap), points, settings, DrawingTool.BRUSH, listOf(Matrix()))
            return Color.alpha(bitmap.getPixel(64, 32)) to (0 until bitmap.height).count {
                Color.alpha(bitmap.getPixel(64, it)) > 0
            }
        }
        try {
            val soft = sample(.12f, base)
            assertEquals(soft, sample(.95f, base))
            assertTrue(sample(.12f, base.copy(pressureSize = true)).second < soft.second)
            val lowFlow = sample(.12f, base.copy(flow = .04f)).first
            assertTrue(lowFlow in 1 until soft.first / 4)
            assertEquals(0 to 0, sample(.8f, base.copy(color = Color.TRANSPARENT)))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun pendingPreviewJoinsCommittedStrokeAndSkipsAlreadyRasterizedSegments() {
        val bitmap = Bitmap.createBitmap(96, 32, Bitmap.Config.ARGB_8888)
        try {
            val renderer = StrokePreviewRenderer()
            val settings = BrushSettings(sizePx = 6f, color = Color.BLACK, opacity = 1f)
            val points = listOf(8f, 32f, 56f, 80f).mapIndexed { index, x ->
                StrokePoint(x, 16f, 1f, 0f, index.toLong())
            }
            renderer.draw(
                Canvas(bitmap), points, settings, DrawingTool.BRUSH, listOf(Matrix()),
                firstPointIndex = 2,
            )
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(32, 16))
            assertTrue(Color.alpha(bitmap.getPixel(56, 16)) > 0)
            assertTrue(Color.alpha(bitmap.getPixel(76, 16)) > 0)
        } finally {
            bitmap.recycle()
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
