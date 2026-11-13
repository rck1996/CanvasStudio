package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.StrokePoint
import kotlin.math.hypot

/** Unfiltered gesture geometry and an endpoint dwell, in screen-space tolerances. */
internal class QuickShapeContact(
    first: StrokePoint,
    val pixelsPerUnit: Float,
    private val holdSlopPx: Float,
) {
    private val samples = ArrayList<StrokePoint>(64).apply { add(first) }
    private var anchor = first
    private var anchorSampleCount = 1
    private var spacingPx = .9f
    private var travelPx = 0f

    val lastMotionTimeMillis: Long get() = anchor.timestampMillis
    val canHold: Boolean get() = samples.size >= 3 && travelPx >= 24f

    fun move(x: Float, y: Float, timeMillis: Long, previewVisible: Boolean = false): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val distance = hypot(x - anchor.x, y - anchor.y) * pixelsPerUnit
        // A visible fit is stickier than the initial hold; only a deliberate
        // move in contact (never the UP coordinates) can dismiss it.
        if (previewVisible && distance <= holdSlopPx * 3.5f) return false
        val previous = samples.last()
        val sampleDistance = hypot(x - previous.x, y - previous.y) * pixelsPerUnit
        val point = if (sampleDistance >= spacingPx || distance > holdSlopPx) {
            StrokePoint(x, y, 1f, 0f, timeMillis)
        } else return false
        if (sampleDistance >= spacingPx) {
            samples.add(point)
            travelPx += sampleDistance
            if (samples.size == 384) {
                for (i in 1..190) samples[i] = samples[i * 2]
                samples[191] = point
                samples.subList(192, samples.size).clear()
                anchorSampleCount = ((anchorSampleCount + 1) / 2).coerceAtMost(191)
                spacingPx *= 1.4f
            }
        }
        if (distance <= holdSlopPx) return false
        anchor = point
        anchorSampleCount = samples.size
        return true
    }

    fun canRelease(x: Float, y: Float): Boolean =
        hypot(x - anchor.x, y - anchor.y) * pixelsPerUnit <= holdSlopPx * 2f

    fun recognize(now: Long): Result? {
        val hold = now - anchor.timestampMillis
        if (!canHold || hold < QuickShapeRecognizer.HOLD_MILLIS) return null
        // Retain sub-slop samples along the outline for corners, without letting
        // samples from the stationary pause deform its endpoint.
        val outline = samples.subList(0, anchorSampleCount)
        val points = if (outline.last() == anchor) outline else outline + anchor
        QuickShapeRecognizer.detectLine(points, anchor.x, anchor.y, hold, pixelsPerUnit)
            ?.let { return Result.Straight(it) }
        // A slowing pen can finish a corner inside the hold slop without
        // resetting the clock. Include its final position once, not the
        // stationary micro-movements that follow it.
        val settled = samples.last()
        val angularPoints = if (settled != anchor &&
            hypot(settled.x - anchor.x, settled.y - anchor.y) * pixelsPerUnit <= holdSlopPx
        ) points + settled else points
        val angular = QuickShapeRecognizer.detectPolygon(angularPoints, pixelsPerUnit)
            ?: if (angularPoints !== points) QuickShapeRecognizer.detectPolygon(points, pixelsPerUnit) else null
        angular?.let { return Result.Angular(it) }
        return QuickShapeRecognizer.detectOval(points, anchor.x, anchor.y, hold, pixelsPerUnit)
            ?.let { Result.Rounded(it) }
    }

    sealed interface Result {
        data class Straight(val line: QuickShapeRecognizer.Line) : Result
        data class Rounded(val oval: QuickShapeRecognizer.Oval) : Result
        data class Angular(val polygon: QuickShapeRecognizer.Polygon) : Result
    }
}
