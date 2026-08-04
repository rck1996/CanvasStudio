package com.orbyte.canvasstudio.drawing.pipeline

/**
 * Renderer-independent brush output. It deliberately carries no Canvas, Paint, Bitmap or GPU
 * resource so the stable Bitmap backend and a future experimental backend receive the same data.
 */
data class BrushDab(
    val x: Float,
    val y: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotationRadians: Float,
    val opacity: Float,
    val flow: Float,
    val color: Int,
    val grainX: Float,
    val grainY: Float,
    val randomSeed: Int,
)

