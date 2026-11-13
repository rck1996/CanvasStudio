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

    fun stylusPointerIndex(event: MotionEvent): Int? {
        // Pointer array order does not identify the pen that lifted. Returning
        // the first pen on a multi-pen release can give the wrong end position.
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                if (isPen(event, event.actionIndex)) return event.actionIndex
        }
        // For a finger/palm release, keep the pen discoverable. The gesture
        // owner decides whether that release finishes anything.
        for (index in 0 until event.pointerCount) {
            if (isPen(event, index)) return index
        }
        return null
    }

    fun isPen(event: MotionEvent, index: Int): Boolean = when (event.getToolType(index)) {
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> true
        else -> false
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
