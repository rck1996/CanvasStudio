package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Conservative QuickShape line recognizer. It only accepts an intentionally held
 * endpoint and a low-deviation, non-retracing stroke. This never guesses from an
 * ordinary released stroke, and it does not allocate a raster or a bitmap.
 */
internal object QuickShapeRecognizer {
    const val HOLD_MILLIS = 420L

    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
    )

    fun detectLine(
        points: List<StrokePoint>,
        releaseX: Float,
        releaseY: Float,
        heldDurationMillis: Long,
    ): Line? {
        if (points.size < 4) return null
        if (!releaseX.isFinite() || !releaseY.isFinite()) return null
        val first = points.first()
        val endpoint = points.last()
        if (heldDurationMillis < HOLD_MILLIS) return null
        val length = hypot(releaseX - first.x, releaseY - first.y)
        if (!length.isFinite() || length < 30f) return null
        // StrokeSampler smooths incoming points, so its final point may lag
        // behind the real pen coordinate. Permit that bounded lag, while the
        // view separately verifies that the raw pointer has truly been held.
        val filteredLength = hypot(endpoint.x - first.x, endpoint.y - first.y)
        val smoothingLag = hypot(releaseX - endpoint.x, releaseY - endpoint.y)
        if (!filteredLength.isFinite() || smoothingLag > (12f + length * .055f).coerceAtMost(60f)) return null

        // Reject wide arcs and retracing/zigzags. Use cross-product distance,
        // avoiding expensive curve fitting, and bound length/endpoint ratio.
        var walked = 0f
        var maxDeviation = 0f
        for (index in 1 until points.size) {
            val prev = points[index - 1]
            val curr = points[index]
            if (!curr.x.isFinite() || !curr.y.isFinite()) return null
            walked += hypot(curr.x - prev.x, curr.y - prev.y)
            val cross = abs(
                (releaseX - first.x) * (curr.y - first.y) -
                (releaseY - first.y) * (curr.x - first.x),
            )
            maxDeviation = max(maxDeviation, cross / length)
        }
        if (walked > length * 1.18f) return null
        if (maxDeviation > max(4f, length * 0.055f)) return null
        return Line(first.x, first.y, releaseX, releaseY)
    }
}
