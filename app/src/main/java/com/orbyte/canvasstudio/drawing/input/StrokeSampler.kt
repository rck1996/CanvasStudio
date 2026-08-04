package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.inputSamplingDistance
import kotlin.math.hypot

/** Stable, allocation-light sampling policy shared by the Canvas backend and future backends. */
internal object StrokeSampler {
    fun sample(
        previous: StrokePoint,
        rawX: Float,
        rawY: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
        time: Long,
        settings: BrushSettings,
        stampBased: Boolean,
    ): StrokePoint? {
        val response = (1f - settings.stabilization.coerceIn(0f, 0.92f)) * 0.86f + 0.08f
        val x = previous.x + (rawX - previous.x) * response
        val y = previous.y + (rawY - previous.y) * response
        if (hypot(x - previous.x, y - previous.y) < inputSamplingDistance(settings, stampBased)) return null
        return StrokePoint(x, y, pressure, tilt, time, orientation)
    }
}

