package com.orbyte.canvasstudio.drawing

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative document-space stroke bounding boxes, shared by interactive
 * drawing and immutable command replay. These deliberately over-approximate
 * brush texture, scatter, blur and dual-brush extents to avoid missing dabs.
 */
internal object BrushStrokeGeometry {
    fun segmentBounds(from: StrokePoint, to: StrokePoint, settings: BrushSettings): RectF {
        val radius = maximumBrushExtent(settings)
        return RectF(
            min(from.x, to.x) - radius,
            min(from.y, to.y) - radius,
            max(from.x, to.x) + radius,
            max(from.y, to.y) + radius,
        )
    }

    fun maximumBrushExtent(settings: BrushSettings): Float {
        val tilt = max(
            settings.tiltResponse,
            settings.dynamicsProfile.tiltSize,
        ).coerceIn(0f, 1f)
        val primaryRadius = settings.sizePx * .5f * (1f + tilt * .9f)
        val scatterExtent = primaryRadius * settings.scatter.coerceIn(0f, 1f)
        val particleExtent = if (
            settings.tipProfile.shape == BrushTipShape.PARTICLE ||
            settings.kind == BrushKind.CHARCOAL ||
            settings.kind == BrushKind.CHALK
        ) {
            primaryRadius * (1.3f + settings.scatter.coerceIn(0f, 1f))
        } else if (settings.kind == BrushKind.BRISTLE) {
            primaryRadius * 1.16f + scatterExtent
        } else if (settings.kind == BrushKind.OIL && settings.tipProfile.shape == BrushTipShape.CHISEL) {
            primaryRadius * 1.15f + scatterExtent
        } else {
            primaryRadius
        }
        val dual = settings.dualBrushProfile
        val dualExtent = if (dual.enabled) {
            primaryRadius * (
                dual.sizeScale.coerceIn(.1f, 2f) +
                    dual.offset.coerceIn(-1f, 1f).let(::abs) +
                    dual.scatter.coerceIn(0f, 1f)
                )
        } else {
            0f
        }
        val blurExtent = settings.sizePx * (1f - settings.hardness.coerceIn(0f, 1f)) * .24f
        val wetExtent = settings.sizePx * settings.renderProfile.bleed.coerceIn(0f, 1f) * .28f
        return max(max(primaryRadius + scatterExtent, particleExtent), dualExtent) +
            blurExtent + wetExtent + 10f
    }

    /** Extra margin for distance dabs whose full segment was previously painted into the tile. */
    fun distanceDabClipPadding(settings: BrushSettings): Float {
        val maxRadius = settings.sizePx * .95f
        val rotatedTipPadding = when {
            settings.tipAssetPath != null -> .42f // A rotated square bitmap can reach sqrt(2) radii.
            settings.kind == BrushKind.WATERCOLOR -> .05f
            else -> .25f // Offset and rotated oil/bristle ovals.
        }
        val dual = settings.dualBrushProfile
        val secondaryTipPadding = if (dual.enabled) {
            val shapePadding = when (dual.tip.shape) {
                BrushTipShape.BRISTLE -> .27f
                BrushTipShape.PARTICLE -> .78f
                else -> 0f
            }
            settings.scatter.coerceIn(0f, 1f) +
                dual.sizeScale.coerceIn(.1f, 2f) * shapePadding
        } else 0f
        // The secondary tip starts at the primary tip's scattered center.
        return maxRadius * max(rotatedTipPadding, secondaryTipPadding) + 1f
    }
}
