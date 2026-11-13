package com.orbyte.canvasstudio.drawing

import android.graphics.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StrokeBatchTransformTest {
    private fun point(x: Float, y: Float, index: Int) =
        StrokePoint(x, y, .4f + index * .1f, .25f, index * 16L, .15f)

    @Test fun matrixBatchPreservesPressureOrientationAndTimes() {
        val input = listOf(point(2f, 4f, 0), point(6f, 8f, 1), point(9f, 13f, 2))
        val scratch = StrokeBatchTransform()
        val translated = scratch.map(input, 0, input.size, Matrix().apply {
            setTranslate(12f, -3f)
        })
        assertEquals(14f, translated[0].x, .001f)
        assertEquals(1f, translated[0].y, .001f)
        assertEquals(21f, translated[2].x, .001f)
        assertEquals(10f, translated[2].y, .001f)
        assertEquals(input[1].pressure, translated[1].pressure, .001f)
        assertEquals(input[1].tilt, translated[1].tilt, .001f)
        assertEquals(input[1].orientation, translated[1].orientation, .001f)
        assertEquals(input[1].timestampMillis, translated[1].timestampMillis)
        assertEquals(2f, input.first().x, .001f)
    }

    @Test fun reusedScratchHandlesSmallerRangeAndNewMatrix() {
        val input = listOf(point(2f, 4f, 0), point(6f, 8f, 1), point(9f, 13f, 2))
        val scratch = StrokeBatchTransform()
        scratch.map(input, 0, 3, Matrix())
        val rotated = scratch.map(input, 1, 3, Matrix().apply { setScale(-1f, 1f) })
        assertEquals(2, rotated.size)
        assertEquals(-6f, rotated[0].x, .001f)
        assertEquals(-9f, rotated[1].x, .001f)
        assertEquals(8f, rotated[0].y, .001f)
        assertEquals(13f, rotated[1].y, .001f)
        assertSame(rotated, scratch.map(input, 1, 3, Matrix()))
    }

    @Test fun boundsEqualUnionOfExpandedStrokeSegments() {
        val source = listOf(
            point(5f, 10f, 0), point(30f, 0f, 1),
            point(-8f, 70f, 2), point(25f, 35f, 3),
        )
        val scratch = StrokeBatchTransform()
        scratch.map(source, 0, source.size, Matrix())
        val bounds = scratch.bounds(12f)
        assertEquals(-20f, bounds.left, .001f)
        assertEquals(-12f, bounds.top, .001f)
        assertEquals(42f, bounds.right, .001f)
        assertEquals(82f, bounds.bottom, .001f)
        assertSame(bounds, scratch.bounds(12f))
    }
}
