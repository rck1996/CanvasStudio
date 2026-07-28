package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeCanvasExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun eightKDocumentWithSixSparseLayersExportsOnHighMemoryTablet() {
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(7680, 4320)
                brushSettings = BrushSettings(sizePx = 96f, pressureSize = false)
                repeat(5) { addLayer() }
                drawDot(this, 800f, 700f)
            }
        }
        val beforeBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val bitmap = view.exportCompositeBitmap(includePaper = false)
        val afterBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        try {
            assertEquals(7680, bitmap.width)
            assertEquals(4320, bitmap.height)
            assertTrue("La exportación 8K no debe exceder 180 MiB adicionales", afterBytes - beforeBytes < 180L * 1024L * 1024L)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawDot(view: DrawingView, x: Float, y: Float) {
        val downTime = 30_000L
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(downTime, downTime + 16L, MotionEvent.ACTION_UP, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
