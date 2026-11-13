package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.BrushRasterSession
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.BrushStrokeGeometry
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokeCommand
import com.orbyte.canvasstudio.drawing.StrokeCommandPainter
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.input.StrokeSampler
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import com.orbyte.canvasstudio.drawing.sanitized
import com.orbyte.canvasstudio.drawing.toSettings
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A shared pressure, tilt and speed sample for dab inspection and the library's raster preview. */
internal object BrushPreviewModel {
    enum class Pressure(val fixedValue: Float?) {
        NATURAL(null), SOFT(.22f), FIRM(.85f),
    }

    internal const val MAX_BITMAP_SIDE = 768
    private val rawPoints = BrushFixture.points(BrushFixture.Scenario.PREVIEW)
    // A wide, fixed document frame centered on the preview stroke at (470, 160).
    private val swatchBounds = RectF(-170f, -40f, 1110f, 360f)

    fun dabs(preset: BrushPreset): List<BrushDab> {
        val settings = preset.toSettings(Color.WHITE).sanitized()
        return BrushDabBatchBuilder.build(samplePreviewStroke(settings), settings, DrawingTool.BRUSH)
    }

    /** The fixture is raw pen input; feed it through the same filter as live strokes. */
    internal fun samplePreviewStroke(
        settings: BrushSettings,
        pressure: Pressure = Pressure.NATURAL,
    ): List<StrokePoint> = buildList {
        // All current BrushKind values are stamp brushes in the Canvas backend.
        val fixedPressure = pressure.fixedValue
        val first = rawPoints.first()
        add(if (fixedPressure == null) first else first.copy(pressure = fixedPressure))
        for (index in 1 until rawPoints.size) {
            val point = rawPoints[index]
            val accepted = StrokeSampler.sample(
                last(), point.x, point.y, fixedPressure ?: point.pressure, point.tilt,
                point.orientation, point.timestampMillis, settings, stampBased = true,
            )
            if (accepted != null) add(accepted)
        }
    }

    /** Replays on transparent paper; call off the main thread. */
    fun rasterize(preset: BrushPreset): Bitmap {
        val settings = preset.toSettings(Color.WHITE).sanitized()
        val points = samplePreviewStroke(settings)
        // Leave room for soft halos and wide tips, then trim unused transparent space.
        val extent = BrushStrokeGeometry.maximumBrushExtent(settings) * 1.5f
        val bounds = RectF(
            points.minOf { it.x } - extent,
            points.minOf { it.y } - extent,
            points.maxOf { it.x } + extent,
            points.maxOf { it.y } + extent,
        )
        return render(settings, points, bounds, trimToInk = true)
    }

    /**
     * Replays the preview stroke at a common document scale on a transparent 768 × 240 bitmap.
     * The frame never follows the ink: transparent margins are kept, and any marks outside
     * the fixed document bounds are clipped. Call off the main thread.
     */
    fun rasterizeSwatch(
        preset: BrushPreset,
        pressure: Pressure = Pressure.NATURAL,
        color: Int = Color.WHITE,
    ): Bitmap {
        val settings = preset.toSettings(color).sanitized()
        return render(settings, samplePreviewStroke(settings, pressure), swatchBounds, trimToInk = false)
    }

    private fun render(
        settings: BrushSettings,
        points: List<StrokePoint>,
        bounds: RectF,
        trimToInk: Boolean,
    ): Bitmap {
        val scale = minOf(1f, MAX_BITMAP_SIDE / maxOf(bounds.width(), bounds.height()))
        val bitmap = Bitmap.createBitmap(
            framePixels(bounds.width() * scale, trimToInk),
            framePixels(bounds.height() * scale, trimToInk),
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
            return if (trimToInk) {
                cropToInk(bitmap).also { if (it !== bitmap) bitmap.recycle() }
            } else {
                bitmap
            }
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun framePixels(value: Float, trimToInk: Boolean): Int =
        (if (trimToInk) ceil(value).toInt() else value.roundToInt()).coerceIn(1, MAX_BITMAP_SIDE)

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
