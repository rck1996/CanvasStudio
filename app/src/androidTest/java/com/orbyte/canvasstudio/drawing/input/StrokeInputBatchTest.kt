package com.orbyte.canvasstudio.drawing.input

import android.graphics.Matrix
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeInputBatchTest {
    @Test
    fun historyIsMappedOnceAndPreservesInputTimestamp() {
        val event = MotionEvent.obtain(100L, 110L, MotionEvent.ACTION_MOVE, 10f, 20f, 0)
        try {
            event.addBatch(120L, 12f, 22f, 0.8f, 1f, 0)
            val matrix = Matrix().apply { setTranslate(-5f, 7f) }
            val received = mutableListOf<Triple<Float, Float, Long>>()
            StrokeInputBatch().forEachHistorical(event, 0, matrix) { x, y, _, _, _, time ->
                received += Triple(x, y, time)
            }
            assertEquals(1, received.size)
            assertEquals(5f, received[0].first, 0.001f)
            assertEquals(27f, received[0].second, 0.001f)
            assertEquals(110L, received[0].third)
        } finally {
            event.recycle()
        }
    }

    @Test
    fun longHistoryUsesThePenIndexAndPreservesAllAxesInOrder() {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 11; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 5; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        fun coords(i: Int) = arrayOf(
            MotionEvent.PointerCoords().apply { x = 900f; y = 800f; pressure = .95f },
            MotionEvent.PointerCoords().apply {
                x = 60f + i
                y = 100f + i
                pressure = .2f + i * .01f
                orientation = .1f + i * .01f
                setAxisValue(MotionEvent.AXIS_TILT, (Math.PI / 4).toFloat())
            },
        )
        val event = MotionEvent.obtain(
            100L, 110L, MotionEvent.ACTION_MOVE, 2, properties, coords(0),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            // 24 historical entries exceed StrokeInputBatch's initial XY capacity.
            for (i in 1..24) event.addBatch(110L + i * 3L, coords(i), 0)
            val transform = Matrix().apply { setTranslate(-10f, 12f) }
            val pen = requireNotNull(StylusInputController.stylusPointerIndex(event))
            assertEquals(1, pen)
            var count = 0
            val batch = StrokeInputBatch()
            batch.forEachHistorical(event, pen, transform) { x, y, pressure, tilt, orientation, time ->
                assertEquals(50f + count, x, .001f)
                assertEquals(112f + count, y, .001f)
                assertEquals(.2f + count * .01f, pressure, .001f)
                assertEquals(.5f, tilt, .001f)
                assertEquals(.1f + count * .01f, orientation, .001f)
                assertEquals(110L + count * 3L, time)
                count++
            }
            assertEquals(24, count)
            val noHistory = MotionEvent.obtainNoHistory(event)
            try {
                batch.forEachHistorical(noHistory, pen, transform) { _, _, _, _, _, _ -> count++ }
                assertEquals(24, count) // A reused buffer must not leak old samples.
            } finally {
                noHistory.recycle()
            }
        } finally {
            event.recycle()
        }
    }
}
