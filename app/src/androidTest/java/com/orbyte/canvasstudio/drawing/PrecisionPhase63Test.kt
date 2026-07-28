package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrecisionPhase63Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun invertedSelectionPaintsOutsideAndPreservesInside() {
        val view = DrawingView(context)
        val bitmap = try {
            instrumentation.runOnMainSync {
                view.configureDocument(512, 512)
                view.tool = DrawingTool.SELECT_RECTANGLE
                gesture(view, 100f, 100f, 300f, 300f, 1_000L)
                view.invertSelection()
                view.tool = DrawingTool.BRUSH
                view.brushSettings = BrushSettings(
                    sizePx = 52f,
                    opacity = 1f,
                    flow = 1f,
                    hardness = 1f,
                    color = Color.BLACK,
                    pressureSize = false,
                    pressureOpacity = false,
                    kind = BrushKind.MARKER,
                )
                gesture(view, 160f, 200f, 240f, 200f, 2_000L)
                gesture(view, 360f, 400f, 440f, 400f, 3_000L)
            }
            instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = false)
            }
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(128, 128) }
        }

        try {
            assertTrue("La zona interior invertida fue modificada", bitmap.alphaNear(200, 200) < 8)
            assertTrue("La zona exterior invertida no recibio el trazo", bitmap.alphaNear(400, 400) > 32)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun featheredSelectionProducesASoftPersistentEdge() {
        val view = DrawingView(context)
        val bitmap = try {
            instrumentation.runOnMainSync {
                view.configureDocument(512, 512)
                view.tool = DrawingTool.SELECT_RECTANGLE
                gesture(view, 140f, 100f, 360f, 300f, 4_000L)
                view.setSelectionFeather(24f)
                view.tool = DrawingTool.BRUSH
                view.brushSettings = BrushSettings(
                    sizePx = 80f,
                    opacity = 1f,
                    flow = 1f,
                    hardness = 1f,
                    color = Color.BLACK,
                    pressureSize = false,
                    pressureOpacity = false,
                    kind = BrushKind.MARKER,
                )
                gesture(view, 80f, 200f, 420f, 200f, 5_000L)
            }
            instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = false)
            }
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(128, 128) }
        }

        try {
            val inside = Color.alpha(bitmap.getPixel(190, 200))
            val edge = Color.alpha(bitmap.getPixel(132, 200))
            val outside = Color.alpha(bitmap.getPixel(90, 200))
            assertTrue("El centro suavizado no quedo opaco", inside > 160)
            assertTrue(
                "El borde no presenta transicion alfa: centro=$inside borde=$edge exterior=$outside",
                edge in 8 until inside,
            )
            assertTrue("El suavizado alcanzo demasiado lejos", outside < 8)
        } finally {
            bitmap.recycle()
        }
    }

    private fun gesture(
        view: DrawingView,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        downTime: Long,
    ) {
        dispatch(view, downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY)
        dispatch(view, downTime, downTime + 16L, MotionEvent.ACTION_MOVE, endX, endY)
        dispatch(view, downTime, downTime + 32L, MotionEvent.ACTION_UP, endX, endY)
    }

    private fun dispatch(
        view: DrawingView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun Bitmap.alphaNear(centerX: Int, centerY: Int): Int {
        var maximum = 0
        for (y in centerY - 20..centerY + 20 step 4) {
            for (x in centerX - 20..centerX + 20 step 4) {
                maximum = maxOf(maximum, Color.alpha(getPixel(x, y)))
            }
        }
        return maximum
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
