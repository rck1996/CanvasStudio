package com.orbyte.canvasstudio.drawing.pipeline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.brush.BrushEvaluator
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Non-authoritative in-progress preview. Owns its temporary paint and coordinate
 * buffer, never writes tiles, and is confined to the view's drawing thread.
 *
 * Final output is always committed by the document renderer; the preview is
 * intentionally approximate for textured brushes and must never be persisted.
 * Match the raster's input dynamics and nominal coverage without replaying dabs.
 */
internal class StrokePreviewRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val endpoints = FloatArray(4)
    private var cachedPoints: List<StrokePoint>? = null
    private var cachedSettings: BrushSettings? = null
    private var cachedTool: DrawingTool? = null
    private var cachedCount = 0
    private var widths = FloatArray(64)
    private var alphas = IntArray(64)

    fun draw(
        canvas: Canvas,
        points: List<StrokePoint>,
        settings: BrushSettings,
        tool: DrawingTool,
        symmetryMatrices: List<Matrix>,
        firstPointIndex: Int = 0,
    ) {
        if (firstPointIndex !in points.indices) return
        paint.color = if (tool == DrawingTool.ERASER) Color.WHITE else settings.color
        if (tool != DrawingTool.ERASER && Color.alpha(settings.color) == 0) return
        val footprint = when (settings.kind) {
            BrushKind.WATERCOLOR -> .78f + settings.renderProfile.wetness.coerceIn(0f, 1f) * .18f
            BrushKind.BRISTLE -> .50f
            BrushKind.OIL -> if (settings.tipProfile.shape == com.orbyte.canvasstudio.drawing.BrushTipShape.CHISEL) {
                settings.tipProfile.roundness.coerceIn(.22f, .74f)
            } else .65f
            BrushKind.INK, BrushKind.PENCIL, BrushKind.MARKER -> settings.tipProfile.roundness.coerceIn(.08f, 1f)
            else -> 1f
        }
        if (cachedPoints !== points || cachedSettings != settings || cachedTool != tool || cachedCount > points.size) {
            cachedPoints = points
            cachedSettings = settings
            cachedTool = tool
            cachedCount = 0
        }
        // Points are append-only for an active stroke. Evaluate each segment once,
        // not again at every display frame or for each symmetry copy.
        if (points.size > widths.size) {
            val capacity = maxOf(points.size, widths.size * 2)
            widths = widths.copyOf(capacity)
            alphas = alphas.copyOf(capacity)
        }
        for (index in cachedCount until points.size) {
            evaluateSegment(index, points[(index - 1).coerceAtLeast(0)], points[index], settings, tool, footprint)
        }
        cachedCount = points.size
        symmetryMatrices.forEach { matrix ->
            val identityTransform = matrix.isIdentity
            var previous = points[firstPointIndex]
            if (points.size == 1) {
                endpoints[0] = previous.x
                endpoints[1] = previous.y
                endpoints[2] = previous.x
                endpoints[3] = previous.y
                if (!identityTransform) matrix.mapPoints(endpoints)
                useSegmentPaint(0)
                if (paint.alpha != 0) canvas.drawPoint(endpoints[0], endpoints[1], paint)
            } else {
                for (index in firstPointIndex + 1 until points.size) {
                    val current = points[index]
                    endpoints[0] = previous.x
                    endpoints[1] = previous.y
                    endpoints[2] = current.x
                    endpoints[3] = current.y
                    if (!identityTransform) matrix.mapPoints(endpoints)
                    useSegmentPaint(index)
                    if (paint.alpha != 0) canvas.drawLine(
                        endpoints[0], endpoints[1], endpoints[2], endpoints[3], paint,
                    )
                    previous = current
                }
            }
        }
    }

    private fun useSegmentPaint(index: Int) {
        paint.strokeWidth = widths[index]
        paint.alpha = alphas[index]
    }

    private fun evaluateSegment(
        index: Int,
        from: StrokePoint,
        to: StrokePoint,
        settings: BrushSettings,
        tool: DrawingTool,
        footprint: Float,
    ) {
        val elapsed = (to.timestampMillis - from.timestampMillis).coerceAtLeast(1L)
        val speed = (hypot(to.x - from.x, to.y - from.y) / elapsed / 2.4f).coerceIn(0f, 1f)
        val dab = BrushEvaluator.evaluate(
            x = to.x, y = to.y, pressure = (from.pressure + to.pressure) * .5f,
            tilt = (from.tilt + to.tilt) * .5f, orientation = to.orientation,
            speedFactor = speed, progress = 0f, stampIndex = 0, settings = settings, drawingTool = tool,
        )
        widths[index] = (dab.radiusX * 2f * footprint).coerceAtLeast(.2f)
        alphas[index] = if (tool == DrawingTool.ERASER) 90
            else (dab.opacity * Color.alpha(settings.color)).roundToInt()
    }
}
