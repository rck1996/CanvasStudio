package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushRenderProfile
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.StrokePoint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WetPigmentHistoryTest {
    @Test
    fun pigmentTravelsAcrossATileAndRegionalReplayIgnoresLaterPaintOnItsSourceTile() = onMain {
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(1024, 512)
            debugPerformanceMetricsEnabled = true
        }
        val source = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val dry = BrushSettings(
            kind = BrushKind.INK, color = Color.GREEN, sizePx = 34f,
            pressureSize = false, taperStart = 0f, taperEnd = 0f, stabilization = 0f,
        )
        try {
            Canvas(source).drawRect(300f, 200f, 494f, 260f, Paint().apply { color = Color.RED })
            view.importBitmapAsLayer(source, "Pigmento inicial")
            view.brushSettings = dry.copy(
                color = Color.BLUE, sizePx = 30f,
                renderProfile = BrushRenderProfile(wetness = .8f, colorPickup = .85f, drag = .65f),
            )
            view.debugDrawStrokeForTest(line(310, 750, 230f))
            val mixed = view.debugPixelForTest(535f, 230f)
            val faded = view.debugPixelForTest(720f, 230f)
            assertTrue("Red from the left tile should reach the right tile", Color.red(mixed) > Color.red(faded) + 20)

            // Modify the left tile after the wet stroke. Undoing a later mark
            // on the right must not recompute pigment from this newer green.
            view.brushSettings = dry
            view.debugDrawStrokeForTest(line(335, 470, 230f))
            val expected = view.exportCompositeBitmap(includePaper = false)
            try {
                view.debugDrawStrokeForTest(line(600, 700, 230f))
                val withLastStroke = view.exportCompositeBitmap(includePaper = false)
                try {
                    view.undo()
                    assertCompositeEquals(view, expected)
                    view.redo()
                    assertCompositeEquals(view, withLastStroke)
                    assertTrue(view.debugPerformanceMetrics().indexFallbacks == 0L)
                } finally {
                    withLastStroke.recycle()
                }
            } finally {
                expected.recycle()
            }
        } finally {
            source.recycle()
            view.configureDocument(256, 256)
        }
    }

    private fun assertCompositeEquals(view: DrawingView, expected: Bitmap) {
        val actual = view.exportCompositeBitmap(includePaper = false)
        try { assertTrue("Regional replay changed the committed mix", expected.sameAs(actual)) }
        finally { actual.recycle() }
    }

    private fun line(start: Int, end: Int, y: Float): List<StrokePoint> =
        (start..end step 5).mapIndexed { index, x -> StrokePoint(x.toFloat(), y, 1f, 0f, index * 8L) }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        result!!.getOrThrow()
    }
}
