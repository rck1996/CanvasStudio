package com.orbyte.canvasstudio.ui.workspace

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

internal sealed interface ColorWheelSelection {
    data class Hue(val degrees: Float) : ColorWheelSelection
    data class SaturationValue(val saturation: Float, val value: Float) : ColorWheelSelection
}

/** Canvas-independent hit testing shared by tap and drag color-wheel gestures. */
internal fun colorWheelSelection(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): ColorWheelSelection? {
    val radius = min(width, height) / 2f
    if (radius <= 0f || !x.isFinite() || !y.isFinite()) return null
    val dx = x - width / 2f
    val dy = y - height / 2f
    if (dx * dx + dy * dy > (radius * 0.62f) * (radius * 0.62f)) {
        val degrees = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI + 360.0) % 360.0).toFloat()
        return ColorWheelSelection.Hue(degrees)
    }
    val half = radius * 0.42f
    return ColorWheelSelection.SaturationValue(
        saturation = ((dx + half) / (half * 2f)).coerceIn(0f, 1f),
        value = ((half - dy) / (half * 2f)).coerceIn(0f, 1f),
    )
}
