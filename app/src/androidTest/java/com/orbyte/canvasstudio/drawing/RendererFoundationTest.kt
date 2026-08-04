package com.orbyte.canvasstudio.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.brush.BrushEvaluator
import com.orbyte.canvasstudio.drawing.input.StrokeSampler
import com.orbyte.canvasstudio.drawing.raster.BitmapCanvasTileRasterBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RendererFoundationTest {
    @Test
    fun samplerRejectsJitterButPreservesLongPressureSamples() {
        val settings = BrushSettings(sizePx = 48f, spacing = .1f, stabilization = 0f)
        val previous = StrokePoint(100f, 100f, .2f, 0f, 10L, 0f)

        assertFalse(
            StrokeSampler.sample(previous, 100.1f, 100.1f, .3f, .1f, .2f, 11L, settings, true) != null,
        )
        val sampled = StrokeSampler.sample(
            previous,
            180f,
            120f,
            .84f,
            .44f,
            .61f,
            20L,
            settings,
            true,
        )
        requireNotNull(sampled)
        assertEquals(.84f, sampled.pressure, .001f)
        assertEquals(.44f, sampled.tilt, .001f)
        assertEquals(.61f, sampled.orientation, .001f)
    }

    @Test
    fun evaluatorIsDeterministicAndPressureAffectsSize() {
        val settings = BrushSettings(sizePx = 40f, minSize = .2f)
        val soft = BrushEvaluator.evaluate(20f, 30f, .15f, 0f, 0f, 0f, 0f, 5, settings, DrawingTool.BRUSH)
        val firm = BrushEvaluator.evaluate(20f, 30f, .9f, 0f, 0f, 0f, 0f, 5, settings, DrawingTool.BRUSH)
        val again = BrushEvaluator.evaluate(20f, 30f, .9f, 0f, 0f, 0f, 0f, 5, settings, DrawingTool.BRUSH)

        assertTrue(firm.radiusX > soft.radiusX)
        assertEquals(firm, again)
    }

    @Test
    fun bitmapBackendKeepsCanvasOutputAndReportsTouchedTiles() {
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "renderer-foundation/backend",
        ).apply { deleteRecursively(); mkdirs() }
        val surface = SparseTileSurface(1024, 1024, directory, 2L * 1024L * 1024L)
        try {
            val bounds = RectF(480f, 480f, 544f, 544f)
            val result = BitmapCanvasTileRasterBackend().rasterize(surface, bounds, false) { canvas: Canvas ->
                canvas.drawRect(bounds, Paint().apply { color = Color.BLUE })
            }
            assertTrue(result.changed)
            assertEquals(4, result.touchedTiles)
        } finally {
            surface.recycle()
            directory.deleteRecursively()
        }
    }
}
