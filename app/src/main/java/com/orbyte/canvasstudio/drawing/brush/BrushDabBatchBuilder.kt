package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** Builds bounded, backend-neutral dab batches from the same sampled stroke used by Canvas. */
internal object BrushDabBatchBuilder {
    const val MAX_DABS_PER_STROKE = 50_000
    private const val MAX_DABS_PER_SEGMENT = 128

    fun build(
        points: List<StrokePoint>,
        settings: BrushSettings,
        tool: DrawingTool,
    ): List<BrushDab> {
        if (points.isEmpty()) return emptyList()
        val result = ArrayList<BrushDab>(minOf(points.size * 3, MAX_DABS_PER_STROKE))
        fun append(point: StrokePoint, speed: Float, progress: Float, stampIndex: Int, rotation: Float) {
            if (result.size >= MAX_DABS_PER_STROKE) return
            result += BrushEvaluator.evaluate(
                x = point.x,
                y = point.y,
                pressure = point.pressure * taper(settings, progress),
                tilt = point.tilt,
                orientation = when (settings.presetId) {
                    "technical-ink" -> rotation
                    else -> point.orientation
                },
                speedFactor = speed,
                progress = progress,
                stampIndex = stampIndex,
                settings = settings,
                drawingTool = tool,
            )
        }
        append(points.first(), 0f, 0f, 0, 0f)
        val spacing = max(1.25f, settings.sizePx * settings.spacing.coerceIn(.025f, .4f))
        val segments = (points.size - 1).coerceAtLeast(1)
        for (segmentIndex in 0 until points.lastIndex) {
            if (result.size >= MAX_DABS_PER_STROKE) break
            val from = points[segmentIndex]
            val to = points[segmentIndex + 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = hypot(dx, dy)
            val elapsed = (to.timestampMillis - from.timestampMillis).coerceAtLeast(1L)
            val speed = (distance / elapsed / 2.4f).coerceIn(0f, 1f)
            val count = ceil(distance / spacing).toInt().coerceIn(1, MAX_DABS_PER_SEGMENT)
            repeat(count) { localIndex ->
                val t = (localIndex + 1f) / count
                append(
                    point = StrokePoint(
                        x = from.x + dx * t,
                        y = from.y + dy * t,
                        pressure = from.pressure + (to.pressure - from.pressure) * t,
                        tilt = from.tilt + (to.tilt - from.tilt) * t,
                        timestampMillis = (from.timestampMillis + (to.timestampMillis - from.timestampMillis) * t).toLong(),
                        orientation = interpolateAngle(from.orientation, to.orientation, t),
                    ),
                    speed = speed,
                    progress = (segmentIndex + t) / segments,
                    stampIndex = segmentIndex * MAX_DABS_PER_SEGMENT + localIndex,
                    rotation = atan2(dy.toDouble(), dx.toDouble()).toFloat(),
                )
            }
        }
        return result
    }

    private fun interpolateAngle(from: Float, to: Float, amount: Float): Float {
        var delta = (to - from) % (Math.PI.toFloat() * 2f)
        if (delta > Math.PI) delta -= Math.PI.toFloat() * 2f
        if (delta < -Math.PI) delta += Math.PI.toFloat() * 2f
        return from + delta * amount
    }

    private fun taper(settings: BrushSettings, progress: Float): Float {
        val safe = progress.coerceIn(0f, 1f)
        val start = settings.taperStart.coerceIn(0f, .48f)
        val end = settings.taperEnd.coerceIn(0f, .48f)
        val startFactor = if (start <= .001f) 1f else (safe / start).coerceIn(.06f, 1f)
        val endFactor = if (end <= .001f) 1f else ((1f - safe) / end).coerceIn(.06f, 1f)
        return minOf(startFactor, endFactor)
    }
}
