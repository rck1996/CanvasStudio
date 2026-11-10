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
}
