package com.orbyte.canvasstudio.drawing.pipeline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint

/**
 * Non-authoritative in-progress preview. Owns its temporary paint and coordinate
 * buffer, never writes tiles, and is confined to the view's drawing thread.
 *
 * Final output is always committed by the document renderer; the preview is
 * intentionally approximate for textured brushes and must never be persisted.
 */
internal class StrokePreviewRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val endpoints = FloatArray(4)

    fun draw(
        canvas: Canvas,
        points: List<StrokePoint>,
        settings: BrushSettings,
        tool: DrawingTool,
        symmetryMatrices: List<Matrix>,
    ) {
        if (points.isEmpty()) return
        paint.color = if (tool == DrawingTool.ERASER) Color.WHITE else settings.color
        paint.alpha = if (tool == DrawingTool.ERASER) 150 else {
            (settings.opacity.coerceIn(0.15f, 1f) * 220f).toInt()
        }
        symmetryMatrices.forEach { matrix ->
            var previous = points.first()
            if (points.size == 1) {
                endpoints[0] = previous.x
                endpoints[1] = previous.y
                endpoints[2] = previous.x
                endpoints[3] = previous.y
                matrix.mapPoints(endpoints)
                paint.strokeWidth = settings.sizePx *
                    previous.pressure.coerceIn(settings.minSize, 1f)
                canvas.drawPoint(endpoints[0], endpoints[1], paint)
            } else {
                for (index in 1 until points.size) {
                    val current = points[index]
                    endpoints[0] = previous.x
                    endpoints[1] = previous.y
                    endpoints[2] = current.x
                    endpoints[3] = current.y
                    matrix.mapPoints(endpoints)
                    paint.strokeWidth = settings.sizePx *
                        ((previous.pressure + current.pressure) * 0.5f)
                            .coerceIn(settings.minSize, 1f)
                    canvas.drawLine(
                        endpoints[0], endpoints[1], endpoints[2], endpoints[3], paint,
                    )
                    previous = current
                }
            }
        }
    }
}
