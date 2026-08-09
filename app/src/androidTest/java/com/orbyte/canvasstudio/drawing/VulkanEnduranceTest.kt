package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.raster.RendererMode
import com.orbyte.canvasstudio.testing.LongRunningTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@LongRunningTest
@RunWith(AndroidJUnit4::class)
class VulkanEnduranceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun continuousTenMinuteSessionExercisesBackendSwitchViewHistoryAndSave() {
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(2048, 1024)
                setRendererMode(RendererMode.VULKAN_EXPERIMENTAL)
                brushSettings = allBuiltInBrushes.single { it.id == "technical-ink" }.toSettings().copy(sizePx = 24f)
                debugPerformanceMetricsEnabled = true
            }
        }
        val started = SystemClock.elapsedRealtime()
        var strokes = 0
        instrumentation.runOnMainSync {
            while (SystemClock.elapsedRealtime() - started < TEN_MINUTES_MS) {
                val y = 80f + (strokes % 15) * 55f
                view.debugDrawStrokeForTest(line(80f, y, 1960f, y + (strokes % 7 - 3) * 11f))
                strokes += 1
                if (strokes % 20 == 0) {
                    view.zoomBy(if ((strokes / 20) % 2 == 0) 1.04f else .96f)
                    view.rotateBy(if ((strokes / 20) % 2 == 0) 2f else -2f)
                }
                if (strokes % 40 == 0) { view.undo(); view.redo() }
                if (strokes % 80 == 0) {
                    view.setRendererMode(RendererMode.CANVAS_BITMAP)
                    view.debugDrawStrokeForTest(line(100f, 940f, 1900f, 940f))
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
        Log.i(
            "CanvasStudioVulkanStress",
            "TEN_MINUTES strokes=$strokes elapsedMs=${SystemClock.elapsedRealtime() - started} metrics=${view.debugPerformanceMetrics()} vulkan=${view.debugVulkanStats()}",
        )
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = listOf(
        StrokePoint(x1, y1, .2f, 0f, 0L, .15f),
        StrokePoint((x1 + x2) * .5f, (y1 + y2) * .5f, .6f, 0f, 12L, .45f),
        StrokePoint(x2, y2, .6f, 0f, 24L, .8f),
    )

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val TEN_MINUTES_MS = 10L * 60L * 1000L
    }
}
