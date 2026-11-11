package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickShapeIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun heldStraightLineBecomesShapeInsteadOfRegularStroke() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) {
                assertEquals(DrawingTool.LINE, it.tool)
                shapes++
            }
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = true, curved = false)
        assertEquals(1, shapes)
        assertEquals(0, freehand)
    }

    @Test
    fun ordinaryReleaseRemainsAStrokeWithQuickShapeEnabled() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) shapes++
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = false, curved = false)
        assertEquals(0, shapes)
        assertEquals(1, freehand)
    }

    @Test
    fun curvedHeldGestureRemainsFreehand() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) shapes++
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = true, curved = true)
        assertEquals(0, shapes)
        assertEquals(1, freehand)
    }

    private fun drawStroke(view: DrawingView, held: Boolean, curved: Boolean) {
        val start = 10_000L
        dispatch(view, start, start, MotionEvent.ACTION_DOWN, 160f, 260f)
        repeat(12) { index ->
            val progress = (index + 1f) / 12f
            val y = if (curved) 260f + 90f * kotlin.math.sin(progress * Math.PI).toFloat()
                    else 260f
            dispatch(
                view,
                start,
                start + 16L * (index + 1),
                MotionEvent.ACTION_MOVE,
                160f + 520f * progress,
                y,
            )
        }
        dispatch(
            view, start,
            if (held) start + 820L else start + 210L,
            MotionEvent.ACTION_UP,
            680f, 260f,
        )
    }

    private fun dispatch(
        view: DrawingView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
