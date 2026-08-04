package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.applyInputCurve
import com.orbyte.canvasstudio.drawing.calibratedPressure
import com.orbyte.canvasstudio.drawing.renderAlphaMultiplier
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import kotlin.math.max

/** Converts configured brush dynamics into a backend-neutral dab. */
internal object BrushEvaluator {
    fun evaluate(
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
        speedFactor: Float,
        progress: Float,
        stampIndex: Int,
        settings: BrushSettings,
        drawingTool: DrawingTool,
        color: Int = settings.color,
    ): BrushDab {
        val dynamics = settings.dynamicsProfile
        val legacyPressure = calibratedPressure(pressure, settings.pressureCurve)
        val sizePressure = applyInputCurve(legacyPressure, dynamics.sizePressure)
        val opacityPressure = applyInputCurve(legacyPressure, dynamics.opacityPressure)
        val flowPressure = applyInputCurve(legacyPressure, dynamics.flowPressure)
        val minimum = settings.minSize.coerceIn(0.02f, 1f)
        val pressureFactor = if (settings.pressureSize) minimum + sizePressure * (1f - minimum) else 1f
        val threshold = dynamics.tiltThreshold.coerceIn(0f, .95f)
        val resolvedTilt = ((tilt.coerceIn(0f, 1f) - threshold) / (1f - threshold)).coerceIn(0f, 1f)
        val tiltStrength = max(settings.tiltResponse, dynamics.tiltSize).coerceIn(0f, 1f)
        val tiltExpansion = 1f + resolvedTilt * tiltStrength * when (settings.kind) {
            com.orbyte.canvasstudio.drawing.BrushKind.PENCIL -> 1.45f
            com.orbyte.canvasstudio.drawing.BrushKind.MARKER -> .72f
            else -> .9f
        }
        val velocityWidth = 1f - max(settings.velocitySize, dynamics.velocitySize)
            .coerceIn(0f, 1f) * speedFactor.coerceIn(0f, 1f) * .62f
        val baseRadius = settings.sizePx * pressureFactor * velocityWidth * .5f
        val radiusX = baseRadius * tiltExpansion
        val tipRoundness = settings.tipProfile.roundness.coerceIn(.08f, 1f)
        val radiusY = baseRadius * when (settings.tipProfile.shape) {
            com.orbyte.canvasstudio.drawing.BrushTipShape.ROUND -> 1f
            com.orbyte.canvasstudio.drawing.BrushTipShape.OVAL,
            com.orbyte.canvasstudio.drawing.BrushTipShape.CHISEL,
            -> tipRoundness * (1f - resolvedTilt * tiltStrength * .22f)
            com.orbyte.canvasstudio.drawing.BrushTipShape.BRISTLE,
            com.orbyte.canvasstudio.drawing.BrushTipShape.PARTICLE,
            -> tipRoundness
        }
        val pressureOpacity = if (settings.pressureOpacity) .08f + opacityPressure * .92f else 1f
        val velocityOpacity = 1f - dynamics.velocityOpacity.coerceIn(0f, 1f) * speedFactor.coerceIn(0f, 1f) * .72f
        val tiltOpacity = 1f - dynamics.tiltOpacity.coerceIn(0f, 1f) * resolvedTilt * .45f
        val flow = if (settings.pressureOpacity) .18f + flowPressure * .82f else 1f
        val render = if (drawingTool == DrawingTool.ERASER) 1f else {
            renderAlphaMultiplier(settings.renderProfile) *
                (1f - settings.renderProfile.dilution.coerceIn(0f, 1f) * .48f)
        }
        val opacity = (settings.opacity * settings.flow * pressureOpacity * flow * velocityOpacity * tiltOpacity * render)
            .coerceIn(0f, 1f)
        return BrushDab(
            x = x,
            y = y,
            radiusX = radiusX.coerceAtLeast(.1f),
            radiusY = radiusY.coerceAtLeast(.1f),
            rotationRadians = orientation,
            opacity = opacity,
            flow = flow,
            color = color,
            grainX = x,
            grainY = y,
            randomSeed = stampIndex,
        )
    }
}
