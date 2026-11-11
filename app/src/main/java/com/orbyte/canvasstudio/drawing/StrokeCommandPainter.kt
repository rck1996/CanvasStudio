package com.orbyte.canvasstudio.drawing

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Immutable StrokeCommand replay, detached from View state.
 *
 * Each instance owns its own mask bounds and receives an isolated
 * BrushRasterSession. Caller must own the instance on one thread. The final
 * bitmap result is pixel-compatible with the previous DrawingView replay.
 *
 * To run on a background worker, pass a detached destination Bitmap and a
 * document/command snapshot; publish modified tiles only after checking the
 * owning document generation and an ordered commit barrier.
 */
internal class StrokeCommandPainter(private val brushes: BrushRasterSession) {
    private val documentBounds = RectF()

    fun draw(canvas: Canvas, command: StrokeCommand, documentBounds: RectF, pickupColors: IntArray? = null, renderClipBounds: RectF? = null) {
        this.documentBounds.set(documentBounds)
        val brushExtent = if (renderClipBounds == null) 0f else {
            BrushStrokeGeometry.maximumBrushExtent(command.settings)
        }
        val clipPath = command.clipPoints.takeIf { it.isNotEmpty() }
            ?.let { pathFromPoints(it, close = true) }
        val clipSave = clipPath?.let {
            beginSelectionMask(
                canvas,
                it,
                command.clipInverted,
                command.clipFeatherPx,
            )
        }
        command.points.firstOrNull()?.takeIf { first ->
            renderClipBounds == null ||
                intersectsClip(first, first, brushExtent, renderClipBounds)
        }?.let { first ->
            brushes.drawBrushStamp(
                canvas = canvas,
                x = first.x,
                y = first.y,
                pressure = first.pressure * brushes.taperFactor(command.settings, 0f),
                tilt = first.tilt,
                drawingTool = command.tool,
                settings = command.settings,
                stampIndex = 0,
                angleRadians = 0f,
                orientation = first.orientation,
            )
        }
        val segmentCount = (command.points.size - 1).coerceAtLeast(0)
        for (index in 0 until segmentCount) {
            val from = command.points[index]
            val to = command.points[index + 1]
            if (renderClipBounds != null && !intersectsClip(from, to, brushExtent, renderClipBounds)) {
                continue
            }
            val progress = (index + 1f) / segmentCount.coerceAtLeast(1).toFloat()
            brushes.drawStrokeSegment(
                canvas,
                from,
                to,
                command.tool,
                command.settings,
                progress,
                index,
                pickupColors?.getOrNull(index),
            )
        }
        if (clipPath != null && clipSave != null) {
            endSelectionMask(
                canvas,
                clipSave,
                clipPath,
                command.clipInverted,
                command.clipFeatherPx,
            )
        }
    }

    private fun intersectsClip(from: StrokePoint, to: StrokePoint, extent: Float, clip: RectF): Boolean =
        min(from.x, to.x) - extent < clip.right &&
            max(from.x, to.x) + extent > clip.left &&
            min(from.y, to.y) - extent < clip.bottom &&
            max(from.y, to.y) + extent > clip.top

    private fun pathFromPoints(points: List<StrokePoint>, close: Boolean): Path = Path().apply {
        points.firstOrNull()?.let { first ->
            moveTo(first.x, first.y)
            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
            if (close) close()
        }
    }

    private fun applySelectionClip(canvas: Canvas, path: Path, inverted: Boolean) {
        if (inverted) canvas.clipOutPath(path) else canvas.clipPath(path)
    }

    private fun beginSelectionMask(
        canvas: Canvas,
        path: Path,
        inverted: Boolean,
        featherPx: Float,
    ): Int {
        val feather = featherPx.coerceAtLeast(0f)
        return if (feather <= 0.01f) {
            canvas.save().also { applySelectionClip(canvas, path, inverted) }
        } else {
            val clipBounds = Rect()
            canvas.getClipBounds(clipBounds)
            canvas.saveLayer(RectF(clipBounds), null)
        }
    }

    private fun endSelectionMask(
        canvas: Canvas,
        saveCount: Int,
        path: Path,
        inverted: Boolean,
        featherPx: Float,
    ) {
        val feather = featherPx.coerceAtLeast(0f)
        if (feather > 0.01f) {
            val maskPath = if (inverted) {
                path
            } else {
                Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(documentBounds, Path.Direction.CW)
                    addPath(path)
                }
            }
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            canvas.drawPath(maskPath, maskPaint)
            maskPaint.xfermode = null
            maskPaint.maskFilter = null
        }
        canvas.restoreToCount(saveCount)
    }


}
