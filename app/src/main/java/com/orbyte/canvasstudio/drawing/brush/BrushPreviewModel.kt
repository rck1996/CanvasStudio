package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.BrushRasterSession
import com.orbyte.canvasstudio.drawing.BrushStrokeGeometry
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokeCommand
import com.orbyte.canvasstudio.drawing.StrokeCommandPainter
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import com.orbyte.canvasstudio.drawing.sanitized
import com.orbyte.canvasstudio.drawing.toSettings
import kotlin.math.ceil

/** A shared pressure, tilt and speed sample for dab inspection and the library's raster preview. */
internal object BrushPreviewModel {
    internal const val MAX_BITMAP_SIDE = 768
    private val points = BrushFixture.points(BrushFixture.Scenario.PREVIEW)

    fun dabs(preset: BrushPreset): List<BrushDab> = BrushDabBatchBuilder.build(
        points = points,
        settings = preset.toSettings(Color.WHITE).sanitized(),
        tool = DrawingTool.BRUSH,
    )

    /** Replays on transparent paper; call off the main thread. */
    fun rasterize(preset: BrushPreset): Bitmap {
        val settings = preset.toSettings(Color.WHITE).sanitized()
        // Leave room for soft halos and wide tips, then trim unused transparent space.
        val extent = BrushStrokeGeometry.maximumBrushExtent(settings) * 1.5f
        val bounds = RectF(
            points.minOf { it.x } - extent,
            points.minOf { it.y } - extent,
            points.maxOf { it.x } + extent,
            points.maxOf { it.y } + extent,
        )
        val scale = minOf(1f, MAX_BITMAP_SIDE / maxOf(bounds.width(), bounds.height()))
        val bitmap = Bitmap.createBitmap(
            ceil(bounds.width() * scale).toInt().coerceIn(1, MAX_BITMAP_SIDE),
            ceil(bounds.height() * scale).toInt().coerceIn(1, MAX_BITMAP_SIDE),
            Bitmap.Config.ARGB_8888,
        )
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-bounds.left, -bounds.top)
            BrushRasterSession().use { brushes ->
                StrokeCommandPainter(brushes).draw(
                    canvas,
                    StrokeCommand(id = "brush-preview", points = points, tool = DrawingTool.BRUSH, settings = settings),
                    bounds,
                )
            }
            return cropToInk(bitmap).also { if (it !== bitmap) bitmap.recycle() }
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun cropToInk(bitmap: Bitmap): Bitmap {
        val row = IntArray(bitmap.width)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (x in row.indices) {
                if (row[x] ushr 24 == 0) continue
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = y
            }
        }
        if (right < 0) return bitmap
        // Retain one transparent pixel where possible for filtered downsampling.
        left = (left - 1).coerceAtLeast(0)
        top = (top - 1).coerceAtLeast(0)
        right = (right + 1).coerceAtMost(bitmap.width - 1)
        bottom = (bottom + 1).coerceAtMost(bitmap.height - 1)
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}
