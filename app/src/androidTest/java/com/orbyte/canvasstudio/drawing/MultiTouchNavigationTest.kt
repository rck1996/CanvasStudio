package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
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

            send(MotionEvent.ACTION_DOWN, 1200L, arrayOf(pen))
            send(MotionEvent.ACTION_MOVE, 1220L, arrayOf(pen))
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                1230L, arrayOf(pen, palm))
            send(MotionEvent.ACTION_POINTER_UP, 1240L, arrayOf(pen, palm))
            assertEquals("Lifting the pen must commit while the palm is still down", 2, committed)
            send(MotionEvent.ACTION_UP, 1250L, arrayOf(palm))
            assertEquals("The remaining contact must not commit a second stroke", 2, committed)
        }
    }

    @Test
    fun penTakesOwnershipWhenPalmIsAlreadyOnCanvas() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            brushSettings = BrushSettings(sizePx = 34f, pressureSize = false)
            quickMenuTouchHoldEnabled = false
        }
        var committed = 0
        view.onInteraction = { if (it is DrawingInteractionEvent.StrokeCommitted) committed++ }
        val palm = MotionEvent.PointerProperties().apply { id = 12; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val pen = MotionEvent.PointerProperties().apply { id = 19; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        fun send(action: Int, delta: Long, penX: Float? = null, palmDown: Boolean = true) {
            val properties = buildList { if (palmDown) add(palm); if (penX != null) add(pen) }.toTypedArray()
            val coords = properties.map { property ->
                MotionEvent.PointerCoords().apply {
                    x = if (property.id == palm.id) 730f else requireNotNull(penX)
                    y = if (property.id == palm.id) 620f else 220f
                    pressure = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(2000L, 2000L + delta, action, properties.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        try {
            send(MotionEvent.ACTION_DOWN, 0L)
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 35L, 160f)
            send(MotionEvent.ACTION_MOVE, 50L, 250f)
            send(MotionEvent.ACTION_MOVE, 64L, 360f)
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 76L, 360f)
            assertEquals("Pen-up must commit while the first contact is still down", 1, committed)
            send(MotionEvent.ACTION_UP, 99L)
            assertEquals(1, committed)
            assertTrue(Color.alpha(view.debugPixelForTest(160f, 220f)) > 0)
            assertEquals(0, Color.alpha(view.debugPixelForTest(730f, 620f)))
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun canceledPenGestureLeavesCommittedPixelsIntactAndNextStrokeWorks() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            brushSettings = BrushSettings(sizePx = 36f, pressureSize = false)
            quickMenuTouchHoldEnabled = false
            quickShapeEnabled = false
        }
        drawCommittedStroke(view)
        val baseline = view.exportCompositeBitmap(includePaper = false)
        var committed = 0
        view.onInteraction = { if (it is DrawingInteractionEvent.StrokeCommitted) committed++ }
        val pen = MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        fun send(action: Int, down: Long, time: Long, x: Float, flags: Int = 0) {
            val point = MotionEvent.PointerCoords().apply { this.x = x; y = 670f; pressure = 1f }
            val event = MotionEvent.obtain(
                down, time, action, 1, arrayOf(pen), arrayOf(point),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, flags,
            )
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        try {
            send(MotionEvent.ACTION_DOWN, 30_000L, 30_000L, 120f)
            send(MotionEvent.ACTION_MOVE, 30_000L, 30_020L, 240f)
            val flag = if (Build.VERSION.SDK_INT >= 33) MotionEvent.FLAG_CANCELED else 0
            send(MotionEvent.ACTION_CANCEL, 30_000L, 30_030L, 240f, flag)
            val afterCancel = view.exportCompositeBitmap(includePaper = false)
            try {
                assertEquals(0, committed)
                assertTrue("Cancel must not touch already committed pixels", baseline.sameAs(afterCancel))
            } finally {
                afterCancel.recycle()
            }

            send(MotionEvent.ACTION_DOWN, 31_000L, 31_000L, 120f)
            send(MotionEvent.ACTION_MOVE, 31_000L, 31_020L, 240f)
            send(MotionEvent.ACTION_UP, 31_000L, 31_040L, 240f)
            val afterNext = view.exportCompositeBitmap(includePaper = false)
            try {
                assertEquals(1, committed)
                assertTrue("The next pen stroke should leave visible pixels", !baseline.sameAs(afterNext))
            } finally {
                afterNext.recycle()
            }
        } finally {
            baseline.recycle()
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
