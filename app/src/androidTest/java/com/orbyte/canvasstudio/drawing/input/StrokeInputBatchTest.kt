package com.orbyte.canvasstudio.drawing.input

import android.graphics.Matrix
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
}
