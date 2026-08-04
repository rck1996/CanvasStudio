package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.raster.RendererMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VulkanStressTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun vulkanRetainsSentinelsAfter200ThickTileCrossingStrokes() {
        val view = onMain { configuredView("technical-ink", 116f) }
        val sentinelColor = Color.rgb(230, 40, 80)
        val result = onMain {
            view.brushSettings = view.brushSettings.copy(color = sentinelColor)
            view.debugDrawStrokeForTest(line(120f, 120f, 900f, 120f, 1f, 0f))
            val sentinel = view.debugPixelForTest(500f, 120f)
            repeat(200) { index ->
                val y = 220f + (index % 12) * 58f
                view.brushSettings = view.brushSettings.copy(color = Color.rgb(20 + index % 180, 80, 190))
                view.debugDrawStrokeForTest(line(70f, y, 1970f, y + 42f, .72f, 0f))
            }
            Triple(view.debugPixelForTest(500f, 120f), sentinel, view.debugVulkanStats()?.batches ?: 0L)
        }
        assertTrue("El sentinel Vulkan desapareció", Color.alpha(result.first) >= Color.alpha(result.second))
        assertTrue(result.third > 0L)
    }

    @Test fun vulkanCompletes500LongGraphiteStrokesWithUndoRedo() {
        val view = onMain { configuredView("graphite-shader", 72f) }
        val started = SystemClock.elapsedRealtime()
        val result = onMain {
            view.debugDrawStrokeForTest(line(90f, 90f, 1950f, 90f, .86f, .8f))
            val sentinel = view.debugPixelForTest(600f, 90f)
            repeat(500) { index ->
                val y = 180f + (index % 14) * 54f
                val pressure = .18f + (index % 9) / 10f
                val tilt = (index % 10) / 10f
                view.debugDrawStrokeForTest(line(50f, y, 1990f, y + (index % 5 - 2) * 18f, pressure, tilt))
            }
            view.undo(); view.redo()
            Triple(view.debugPixelForTest(600f, 90f), sentinel, view.debugRendererFallbackCount())
        }
        val duration = SystemClock.elapsedRealtime() - started
        assertTrue("El sentinel de grafito desapareció", Color.alpha(result.first) >= Color.alpha(result.second))
        assertTrue("Fallback Vulkan inesperado", result.third == 0L)
        Log.i("CanvasStudioVulkanStress", "VULKAN_500 durationMs=$duration stats=${view.debugVulkanStats()} history=${view.debugPerformanceMetrics()}")
    }

    @Test fun continuousTenMinuteSessionExercisesBackendSwitchViewHistoryAndSave() {
        val view = onMain { configuredView("technical-ink", 24f).apply { debugPerformanceMetricsEnabled = true } }
        val started = SystemClock.elapsedRealtime()
        var strokes = 0
        instrumentation.runOnMainSync {
            while (SystemClock.elapsedRealtime() - started < TEN_MINUTES_MS) {
                val y = 80f + (strokes % 15) * 55f
                view.debugDrawStrokeForTest(line(80f, y, 1960f, y + (strokes % 7 - 3) * 11f, .65f, 0f))
                strokes += 1
                if (strokes % 20 == 0) {
                    view.zoomBy(if ((strokes / 20) % 2 == 0) 1.04f else .96f)
                    view.rotateBy(if ((strokes / 20) % 2 == 0) 2f else -2f)
                }
                if (strokes % 40 == 0) { view.undo(); view.redo() }
                if (strokes % 80 == 0) {
                    view.setRendererMode(RendererMode.CANVAS_BITMAP)
                    view.debugDrawStrokeForTest(line(100f, 940f, 1900f, 940f, .6f, 0f))
                    view.setRendererMode(RendererMode.VULKAN_EXPERIMENTAL)
                }
            }
        }
        val saved = CountDownLatch(1)
        var saveSucceeded = false
        onMain {
            view.onProjectSaved = { success -> saveSucceeded = success; saved.countDown() }
            view.saveProject("vulkan-ten-minute", "Vulkan 10 minute", 300)
        }
        assertTrue(saved.await(90, TimeUnit.SECONDS) && saveSucceeded)
        assertTrue(onMain { DrawingView(context).loadProject("vulkan-ten-minute") })
        assertTrue(strokes > 100)
        Log.i("CanvasStudioVulkanStress", "TEN_MINUTES strokes=$strokes elapsedMs=${SystemClock.elapsedRealtime() - started} metrics=${view.debugPerformanceMetrics()} vulkan=${view.debugVulkanStats()}")
    }

    private fun configuredView(presetId: String, size: Float): DrawingView = DrawingView(context).apply {
        configureDocument(2048, 1024)
        setRendererMode(RendererMode.VULKAN_EXPERIMENTAL)
        // Vulkan remains frozen on its historical graphite/ink fixtures in this phase.
        brushSettings = allBuiltInBrushes.single { it.id == presetId }.toSettings().copy(sizePx = size)
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, pressure: Float, tilt: Float) = listOf(
        StrokePoint(x1, y1, maxOf(.04f, pressure * .3f), tilt, 0L, .15f),
        StrokePoint((x1 + x2) * .5f, (y1 + y2) * .5f, pressure, tilt, 12L, .45f),
        StrokePoint(x2, y2, pressure, tilt, 24L, .8f),
    )

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object { const val TEN_MINUTES_MS = 10L * 60L * 1000L }
}
