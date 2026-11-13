package com.orbyte.canvasstudio.drawing.input

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StylusInputControllerTest {
    @Test
    fun penReleaseUsesActionPointerEvenWhenItIsNotTheFirstPen() {
        val event = pointerUp(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
        try {
            val index = requireNotNull(StylusInputController.stylusPointerIndex(event))
            assertEquals(event.actionIndex, index)
            assertEquals(19, event.getPointerId(index))
            assertEquals(true, StylusInputController.isEraser(event, index))
            assertEquals(260f, StylusInputController.currentSample(event, index).x, .001f)
        } finally {
            event.recycle()
        }
    }

    @Test
    fun palmReleaseDoesNotHideRemainingPen() {
        val event = pointerUp(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER)
        try {
            assertEquals(0, requireNotNull(StylusInputController.stylusPointerIndex(event)))
        } finally {
            event.recycle()
        }
    }

    private fun pointerUp(firstTool: Int, secondTool: Int): MotionEvent = MotionEvent.obtain(
        100L, 120L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
        2,
        arrayOf(
            MotionEvent.PointerProperties().apply { id = 7; toolType = firstTool },
            MotionEvent.PointerProperties().apply { id = 19; toolType = secondTool },
        ),
        arrayOf(
            MotionEvent.PointerCoords().apply { x = 80f; y = 100f; pressure = 1f },
            MotionEvent.PointerCoords().apply { x = 260f; y = 300f; pressure = 1f },
        ),
        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
    )
}
