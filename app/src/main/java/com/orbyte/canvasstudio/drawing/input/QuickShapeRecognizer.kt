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
        releaseTimeMillis: Long,
        releaseTolerancePx: Float = 6f,
    ): Line? {
        if (points.size < 4) return null
        if (!releaseX.isFinite() || !releaseY.isFinite()) return null
        val first = points.first()
        val endpoint = points.last()
        val length = hypot(endpoint.x - first.x, endpoint.y - first.y)
        if (!length.isFinite() || length < 30f) return null

        // Find when the pen first became stationary at its final location; small
        // pressure jitter during the hold must not restart the timer.
        val tolerance = releaseTolerancePx.coerceIn(2f, 30f)
        if (hypot(releaseX - endpoint.x, releaseY - endpoint.y) > tolerance) return null
        var stationarySince = endpoint.timestampMillis
        for (index in points.lastIndex downTo 0) {
            val point = points[index]
            if (hypot(point.x - releaseX, point.y - releaseY) > tolerance) break
            stationarySince = point.timestampMillis
        }
        if (releaseTimeMillis - stationarySince < HOLD_MILLIS) return null

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
                (endpoint.x - first.x) * (curr.y - first.y) -
                (endpoint.y - first.y) * (curr.x - first.x),
            )
            maxDeviation = max(maxDeviation, cross / length)
        }
        if (walked > length * 1.18f) return null
        if (maxDeviation > max(4f, length * 0.055f)) return null
        return Line(first.x, first.y, releaseX, releaseY)
    }
}
