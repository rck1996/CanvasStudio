package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.mixPigmentColor
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The resulting pigment for each sampled point, captured before the stroke
 * writes pixels. Keep it in session history, never in the project format.
 * Regional replay must not sample later paint outside the rebuilt tile.
 */
class StrokePigmentTrace private constructor(private val colors: IntArray) {
    internal fun colorAt(pointIndex: Int): Int? =
        if (pointIndex in colors.indices) colors[pointIndex] else null

    internal companion object {
        val Empty = StrokePigmentTrace(IntArray(0))

        fun capture(
            points: List<StrokePoint>,
            settings: BrushSettings,
            sample: (StrokePoint) -> Int?,
        ): StrokePigmentTrace {
            val reservoir = if (usesWetPigment(DrawingTool.BRUSH, settings)) WetPigmentReservoir(settings) else null
            // Both wet and direct pickup must freeze the sampled pigment. A
            // regional replay cannot read pixels from not-yet-rebuilt tiles.
            var trace: IntArray? = null
            points.forEachIndexed { index, point ->
                val color = when {
                    reservoir != null -> reservoir.advance(point, sample(point))
                    index == 0 -> settings.color // The direct-pickup first dab used the loaded color.
                    else -> mixPigmentColor(settings.color, sample(point), settings.renderProfile.colorPickup)
                }
                if (color != settings.color && trace == null) {
                    trace = IntArray(points.size) { settings.color }
                }
                trace?.set(index, color)
            }
            return trace?.let(::StrokePigmentTrace) ?: Empty
        }
    }
}

internal fun usesWetPigment(tool: DrawingTool, settings: BrushSettings): Boolean =
    usesPigmentPickup(tool, settings) && settings.renderProfile.wetness > .001f

internal fun usesPigmentPickup(tool: DrawingTool, settings: BrushSettings): Boolean =
    tool == DrawingTool.BRUSH && settings.renderProfile.colorPickup > .001f

/**
 * A small stroke-local pigment reservoir. A wet tip approaches the local mix
 * with distance, rather than with the number of input events; on transparent
 * pixels it gradually returns to its loaded paint. Interpolate in linear RGB
 * and leave the brush's own alpha and flow under the raster engine's control.
 */
internal class WetPigmentReservoir(private val settings: BrushSettings) {
    private val loadedColor = settings.color
    private val loadedR = linear(loadedColor, 16)
    private val loadedG = linear(loadedColor, 8)
    private val loadedB = linear(loadedColor, 0)
    private var r = loadedR
    private var g = loadedG
    private var b = loadedB
    private var previous: StrokePoint? = null
    private val pickup = settings.renderProfile.colorPickup.coerceIn(0f, 1f)
    private val size = settings.sizePx.coerceIn(2f, 600f)
    private val carryDistance = (size * (
        .32f + settings.renderProfile.wetness.coerceIn(0f, 1f) * .88f +
            settings.renderProfile.drag.coerceIn(0f, 1f) * 1.5f
        )).coerceAtLeast(2f)

    fun advance(point: StrokePoint, sampledColor: Int?): Int {
        val distance = previous?.let { hypot(point.x - it.x, point.y - it.y) } ?: (size * .25f)
        previous = point
        val sample = sampledColor ?: 0
        val pigment = pickup * ((sample ushr 24) / 255f)
        if (pigment == 0f && r == loadedR && g == loadedG && b == loadedB) return loadedColor
        if (distance <= 0f || !distance.isFinite()) return toColor()
        val weight = 1f - exp(-distance / carryDistance)
        r += (loadedR + (linear(sample, 16) - loadedR) * pigment - r) * weight
        g += (loadedG + (linear(sample, 8) - loadedG) * pigment - g) * weight
        b += (loadedB + (linear(sample, 0) - loadedB) * pigment - b) * weight
        return toColor()
    }

    private fun toColor() =
        (loadedColor and 0xFF000000.toInt()) or (encode(r) shl 16) or (encode(g) shl 8) or encode(b)

    private companion object {
        const val LINEAR_STEPS = 4096
        val srgbToLinear = FloatArray(256) { (it / 255f).pow(2.2f) }
        val linearToSrgb = IntArray(LINEAR_STEPS + 1) {
            ((it.toFloat() / LINEAR_STEPS).pow(1f / 2.2f) * 255f).roundToInt()
        }

        fun linear(color: Int, shift: Int): Float = srgbToLinear[(color ushr shift) and 255]
        fun encode(value: Float): Int =
            linearToSrgb[(value.coerceIn(0f, 1f) * LINEAR_STEPS).roundToInt()]
    }
}
