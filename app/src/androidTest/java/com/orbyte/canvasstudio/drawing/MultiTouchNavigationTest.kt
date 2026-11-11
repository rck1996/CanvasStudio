package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiTouchNavigationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun secondFingerDoesNotClearCommittedArtwork() {
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(1024, 1024)
                brushSettings = BrushSettings(sizePx = 56f, pressureSize = false)
            }
        }
        onMain {
            drawCommittedStroke(view)
            val baseline = view.exportCompositeBitmap(includePaper = false)

            dispatchSingle(view, 20_000L, MotionEvent.ACTION_DOWN, 280f, 360f)
            val afterFirstFinger = view.exportCompositeBitmap(includePaper = false)
            assertTrue("El primer dedo no debe vaciar ni alterar los tiles", baseline.sameAs(afterFirstFinger))

            dispatchTwoFingerPointerDown(view, 20_000L, 20_016L)
            val afterSecondFinger = view.exportCompositeBitmap(includePaper = false)
            assertTrue("El segundo dedo no debe reconstruir ni ocultar el dibujo", baseline.sameAs(afterSecondFinger))

            baseline.recycle()
            afterFirstFinger.recycle()
            afterSecondFinger.recycle()
        }
    }

    @Test
    fun rejectedPalmDoesNotCancelTheActiveStylusStroke() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        onMain {
            val view = DrawingView(context).apply {
                configureDocument(1024, 1024)
                quickMenuTouchHoldEnabled = false
            }
            var committed = 0
            view.onInteraction = { if (it is DrawingInteractionEvent.StrokeCommitted) committed++ }
            val pen = MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            val palm = MotionEvent.PointerProperties().apply { id = 9; toolType = MotionEvent.TOOL_TYPE_FINGER }
            fun send(action: Int, time: Long, pointers: Array<MotionEvent.PointerProperties>, flags: Int = 0) {
                val coords = Array(pointers.size) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = if (index == 0) 200f + (time - 1000L) else 600f
                        y = if (index == 0) 300f else 500f
                        pressure = 1f; size = 1f
                    }
                }
                val event = MotionEvent.obtain(1000L, time, action, pointers.size, pointers, coords,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 1000L, arrayOf(pen))
            send(MotionEvent.ACTION_MOVE, 1020L, arrayOf(pen))
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                1030L, arrayOf(pen, palm))
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                1040L, arrayOf(pen, palm), MotionEvent.FLAG_CANCELED)
            send(MotionEvent.ACTION_MOVE, 1060L, arrayOf(pen))
            send(MotionEvent.ACTION_UP, 1080L, arrayOf(pen))
            assertEquals("Palm rejection must preserve the pen stroke", 1, committed)
        }
    }

    private fun drawCommittedStroke(view: DrawingView) {
        val downTime = 10_000L
        dispatchSingle(view, downTime, MotionEvent.ACTION_DOWN, 160f, 220f)
        repeat(12) { index ->
            val progress = (index + 1f) / 12f
            dispatchSingle(
                view,
                downTime + 8L * (index + 1),
                MotionEvent.ACTION_MOVE,
                160f + 600f * progress,
                220f + 180f * progress,
                downTime,
            )
        }
        dispatchSingle(view, downTime + 120L, MotionEvent.ACTION_UP, 760f, 400f, downTime)
    }

    private fun dispatchSingle(
        view: DrawingView,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long = eventTime,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun dispatchTwoFingerPointerDown(view: DrawingView, downTime: Long, eventTime: Long) {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = 280f
                y = 360f
                pressure = 1f
                size = 1f
            },
            MotionEvent.PointerCoords().apply {
                x = 720f
                y = 640f
                pressure = 1f
                size = 1f
            },
        )
        val action = MotionEvent.ACTION_POINTER_DOWN or
            (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            2,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        ).also { event ->
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
