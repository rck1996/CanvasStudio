package com.orbyte.canvasstudio.drawing.input

import android.graphics.Matrix
import android.view.MotionEvent
import com.orbyte.canvasstudio.drawing.normalizedStylusTilt

/**
 * Reuses a primitive XY buffer to transform a MotionEvent's historical samples
 * with one Matrix.mapPoints call. Preserves chronological order and raw pressure,
 * tilt, orientation and timestamps; no StrokePoint objects are allocated here.
 *
 * Main-thread confined. Consumers must never retain or modify the scratch.
 */
internal class StrokeInputBatch {
    private var coordinates = FloatArray(32)

    fun forEachHistorical(
        event: MotionEvent,
        pointerIndex: Int,
        inverseTransform: Matrix,
        consume: (x: Float, y: Float, pressure: Float, tilt: Float,
                  orientation: Float, timeMillis: Long) -> Unit,
    ) {
        val count = event.historySize
        if (count == 0) return
        val needed = count * 2
        if (coordinates.size < needed) {
            coordinates = FloatArray(maxOf(coordinates.size * 2, needed))
        }
        for (index in 0 until count) {
            val offset = index * 2
            coordinates[offset] = event.getHistoricalX(pointerIndex, index)
            coordinates[offset + 1] = event.getHistoricalY(pointerIndex, index)
        }
        inverseTransform.mapPoints(coordinates, 0, coordinates, 0, count)
        for (index in 0 until count) {
            val offset = index * 2
            consume(
                coordinates[offset],
                coordinates[offset + 1],
                event.getHistoricalPressure(pointerIndex, index).coerceIn(0f, 1f),
                normalizedStylusTilt(
                    event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, index),
                ),
                event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, index),
                event.getHistoricalEventTime(index),
            )
        }
    }
}
