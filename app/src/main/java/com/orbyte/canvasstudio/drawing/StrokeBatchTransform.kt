package com.orbyte.canvasstudio.drawing

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable UI-thread scratch for active ink rasterization. Consecutive samples
 * are transformed in one Matrix.mapPoints call rather than allocating a new
 * FloatArray per point. The view owns this scratch, so batches are never shared.
 */
internal class StrokeBatchTransform {
    private var coordinates = FloatArray(0)
    private val transformed = ArrayList<StrokePoint>()
    private val visibleBounds = RectF()

    fun map(
        source: List<StrokePoint>,
        firstInclusive: Int,
        endExclusive: Int,
        matrix: Matrix,
    ): List<StrokePoint> {
        require(firstInclusive >= 0 && endExclusive >= firstInclusive && endExclusive <= source.size)
        val count = endExclusive - firstInclusive
        if (coordinates.size < count * 2) {
            coordinates = FloatArray(count * 2)
        }
        transformed.clear()
        for (index in 0 until count) {
            val point = source[firstInclusive + index]
            coordinates[index * 2] = point.x
            coordinates[index * 2 + 1] = point.y
        }
        if (count > 0) matrix.mapPoints(coordinates, 0, coordinates, 0, count)
        for (index in 0 until count) {
            transformed.add(
                source[firstInclusive + index].copy(
                    x = coordinates[index * 2],
                    y = coordinates[index * 2 + 1],
                ),
            )
        }
        return transformed
    }

    /** Same union as per-segment expanded boxes, with one RectF per batch. */
    fun bounds(brushExtent: Float): RectF {
        if (transformed.isEmpty()) {
            visibleBounds.setEmpty()
            return visibleBounds
        }
        var left = transformed[0].x
        var top = transformed[0].y
        var right = left
        var bottom = top
        for (index in 1 until transformed.size) {
            val point = transformed[index]
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        val radius = brushExtent.coerceAtLeast(0f)
        visibleBounds.set(left - radius, top - radius, right + radius, bottom + radius)
        return visibleBounds
    }
}
