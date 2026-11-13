package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.FeatheredBrushTip
import kotlin.math.floor

/** These materials carry dab phase through the deferred Canvas commit. */
internal fun usesDistanceDabs(settings: BrushSettings, tool: DrawingTool): Boolean =
    tool == DrawingTool.BRUSH && (
        FeatheredBrushTip.forSettings(settings) != null ||
            (settings.sizePx >= 48f && settings.kind in BROAD_STAMP_BRUSHES)
        )

private val BROAD_STAMP_BRUSHES = setOf(BrushKind.BRISTLE, BrushKind.WATERCOLOR, BrushKind.OIL)

internal data class DabSpan(
    val firstFraction: Double,
    val fractionStep: Double,
    val count: Int,
    val firstSeed: Int,
) {
    fun fractionAt(index: Int): Float = (firstFraction + index * fractionStep).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Carries fractional dab spacing across samples. A zero-length event deposits
 * nothing, and changing the local spacing keeps the current fractional phase.
 * One seed sequence per stroke keeps texture jitter independent of segmentation.
 */
internal class DistanceDabCursor {
    private var phase = 0.0
    private var nextSeed = 1 // the initial contact has seed 0

    fun advance(distance: Float, requestedStep: Float): DabSpan? {
        if (!distance.isFinite() || distance <= 0f || !requestedStep.isFinite() || requestedStep <= 0f) return null
        val units = minOf(distance.toDouble() / requestedStep, MAX_DABS_PER_SEGMENT.toDouble())
        val first = (1.0 - phase) / units
        val advanced = phase + units
        val count = floor(advanced + BOUNDARY_EPSILON).toInt().coerceAtMost(MAX_DABS_PER_SEGMENT)
        phase = (advanced - count).coerceIn(0.0, 1.0)
        if (count == 0) return null
        return DabSpan(first, 1.0 / units, count, nextSeed).also { nextSeed += count }
    }

    private companion object {
        const val MAX_DABS_PER_SEGMENT = 48
        const val BOUNDARY_EPSILON = 1e-9
    }
}
