package com.orbyte.canvasstudio.ui.workspace

import kotlin.math.ln
import kotlin.math.pow

/**
 * Perceptual (logarithmic) brush-size slider.
 *
 * A linear 2..180 px control makes small pen sizes hard to select; logarithmic travel
 * devotes more of the slider to the sizes commonly used for line work.
 */
internal object QuickBrushSizing {
    private const val MIN_SIZE = 2f
    private const val MAX_SIZE = 180f
    private const val MIN_OPACITY = 0.05f

    fun sizeProgress(sizePx: Float): Float =
        (ln((sizePx.coerceIn(MIN_SIZE, MAX_SIZE) / MIN_SIZE).toDouble()) /
            ln((MAX_SIZE / MIN_SIZE).toDouble())).toFloat().coerceIn(0f, 1f)

    fun sizeFromProgress(progress: Float): Float =
        (MIN_SIZE * (MAX_SIZE / MIN_SIZE).pow(progress.coerceIn(0f, 1f)))
            .coerceIn(MIN_SIZE, MAX_SIZE)

    fun opacityProgress(opacity: Float): Float =
        ((opacity - MIN_OPACITY) / (1f - MIN_OPACITY)).coerceIn(0f, 1f)

    fun opacityFromProgress(progress: Float): Float =
        MIN_OPACITY + progress.coerceIn(0f, 1f) * (1f - MIN_OPACITY)
}
