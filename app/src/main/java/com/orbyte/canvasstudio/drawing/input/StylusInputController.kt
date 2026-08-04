package com.orbyte.canvasstudio.drawing.input

import android.view.MotionEvent
import com.orbyte.canvasstudio.drawing.normalizedStylusTilt

/** Extracts pen data from MotionEvent without knowing about layers, brushes or rasterization. */
internal object StylusInputController {
    data class Sample(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val tilt: Float,
        val orientation: Float,
        val eventTimeMillis: Long,
    )

    fun stylusPointerIndex(event: MotionEvent): Int? =
        (0 until event.pointerCount).firstOrNull { index ->
            when (event.getToolType(index)) {
                MotionEvent.TOOL_TYPE_STYLUS,
                MotionEvent.TOOL_TYPE_ERASER,
                -> true
                else -> false
            }
        }

    fun isEraser(event: MotionEvent, pointerIndex: Int): Boolean =
        event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER

    fun currentSample(event: MotionEvent, pointerIndex: Int): Sample = Sample(
        x = event.getX(pointerIndex),
        y = event.getY(pointerIndex),
        pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f),
        tilt = normalizedStylusTilt(event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)),
        orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex),
        eventTimeMillis = event.eventTime,
    )

    fun historicalSample(event: MotionEvent, pointerIndex: Int, historyIndex: Int): Sample = Sample(
        x = event.getHistoricalX(pointerIndex, historyIndex),
        y = event.getHistoricalY(pointerIndex, historyIndex),
        pressure = event.getHistoricalPressure(pointerIndex, historyIndex).coerceIn(0f, 1f),
        tilt = normalizedStylusTilt(
            event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, historyIndex),
        ),
        orientation = event.getHistoricalAxisValue(
            MotionEvent.AXIS_ORIENTATION,
            pointerIndex,
            historyIndex,
        ),
        eventTimeMillis = event.getHistoricalEventTime(historyIndex),
    )
}
