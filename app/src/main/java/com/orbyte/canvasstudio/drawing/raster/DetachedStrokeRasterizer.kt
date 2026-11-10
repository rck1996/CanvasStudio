package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.orbyte.canvasstudio.drawing.BrushRasterSession
import com.orbyte.canvasstudio.drawing.BrushStrokeGeometry
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import com.orbyte.canvasstudio.drawing.StrokeCommand
import com.orbyte.canvasstudio.drawing.StrokeCommandPainter
import com.orbyte.canvasstudio.drawing.TileStorage
import java.io.Closeable

/**
 * Off-UI *experimental* CPU stroke rasterizer. No live tile is ever changed
 * from a worker. Preparation draws only into owned detached mutable bitmaps;
 * publication atomically adopts those bitmaps if the destination has not
 * changed since preparation began.
 *
 * An instance and its BrushRasterSession belong to a single worker thread.
 * Pair with OrderedRasterCommandQueue: prepare off-main, then call
 * PreparedBatch.publish() on the owning main thread and close in finally.
 *
 * Not yet enabled on user strokes: document command history, undo, pen preview,
 * save barriers and layer lifetime must be integrated at the same commit point.
 */
internal class DetachedStrokeRasterizer : Closeable {
    private val brushSession = BrushRasterSession()
    private val painter = StrokeCommandPainter(brushSession)
    private val alphaCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    fun prepare(
        surface: SparseTileSurface,
        command: StrokeCommand,
        preserveAlpha: Boolean = false,
    ): PreparedBatch? {
        // Exact wet color pickup is history-dependent: defer to the verified
        // synchronous CPU path until a reproducible pickup snapshot exists.
        if (command.settings.renderProfile.colorPickup > .001f) return null
        if (command.points.isEmpty()) return null
        val extent = BrushStrokeGeometry.maximumBrushExtent(command.settings)
        val left = command.points.minOf { it.x } - extent
        val top = command.points.minOf { it.y } - extent
        val right = command.points.maxOf { it.x } + extent
        val bottom = command.points.maxOf { it.y } + extent
        val bounds = RectF(left, top, right, bottom)
        val keys = TileStorage.keysForBounds(bounds, surface.width, surface.height)
        if (keys.size > MAX_BATCH_TILES) return null

        val expectedRevision = surface.rasterRevision()
        val output = ArrayList<Pair<TileStorage.Key, Bitmap>>(keys.size)
        val documentBounds = RectF(0f, 0f, surface.width.toFloat(), surface.height.toFloat())
        try {
            for (key in keys) {
                val existing = surface.snapshotTile(key)
                // Alpha lock can never create a new opaque pixel in an empty tile.
                if (preserveAlpha && existing == null) continue
                val rect = TileStorage.tileRect(key, surface.width, surface.height)
                val result = if (existing != null) {
                    try {
                        existing.copy(Bitmap.Config.ARGB_8888, true)
                    } finally {
                        existing.recycle()
                    }
                } else Bitmap.createBitmap(
                    rect.width(), rect.height(), Bitmap.Config.ARGB_8888,
                )

                try {
                    if (preserveAlpha) {
                        val scratch = Bitmap.createBitmap(
                            rect.width(), rect.height(), Bitmap.Config.ARGB_8888,
                        )
                        try {
                            renderIntoTile(
                                scratch, rect.left, rect.top, command, documentBounds,
                                clipToTile = false,
                            )
                            Canvas(result).drawBitmap(scratch, 0f, 0f, alphaCompositePaint)
                        } finally {
                            scratch.recycle()
                        }
                    } else {
                        renderIntoTile(
                            result, rect.left, rect.top, command, documentBounds,
                            clipToTile = true,
                        )
                    }
                    output += key to result
                } catch (failure: Throwable) {
                    result.recycle()
                    throw failure
                }
            }
            return PreparedBatch(surface, expectedRevision, output)
        } catch (failure: Throwable) {
            output.forEach { (_, bitmap) -> if (!bitmap.isRecycled) bitmap.recycle() }
            throw failure
        }
    }

    private fun renderIntoTile(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        command: StrokeCommand,
        documentBounds: RectF,
        clipToTile: Boolean,
    ) {
        val tileBounds = RectF(
            left.toFloat(), top.toFloat(),
            (left + bitmap.width).toFloat(), (top + bitmap.height).toFloat(),
        )
        val canvas = Canvas(bitmap)
        val saved = canvas.save()
        try {
            canvas.translate(-left.toFloat(), -top.toFloat())
            canvas.clipRect(tileBounds)
            painter.draw(
                canvas = canvas,
                command = command,
                documentBounds = documentBounds,
                // Matching the legacy Canvas replay semantics:
                // alpha-locked strokes redraw full commands into scratch;
                // normal replay culls segments outside the current tile.
                renderClipBounds = if (clipToTile) tileBounds else null,
            )
        } finally {
            canvas.restoreToCount(saved)
            canvas.setBitmap(null)
        }
    }

    override fun close() = brushSession.close()

    class PreparedBatch internal constructor(
        private val target: SparseTileSurface,
        private val expectedRevision: Long,
        private val patches: List<Pair<TileStorage.Key, Bitmap>>,
    ) : Closeable {
        private var transferred = false
        private var closed = false
        val tileCount: Int get() = patches.size

        /**
         * Returns false when an intervening UI edit invalidated the snapshot.
         * The caller must then use the legacy path or enqueue a fresh batch.
         */
        fun publish(): Boolean {
            check(!closed) { "Batch already closed" }
            if (transferred) return true
            val published = target.publishDetachedTiles(expectedRevision, patches)
            if (published) transferred = true
            return published
        }

        override fun close() {
            if (closed) return
            closed = true
            if (!transferred) {
                patches.forEach { (_, tile) -> if (!tile.isRecycled) tile.recycle() }
            }
        }
    }

    private companion object {
        // 24 tiles x 1 MiB input + 1 MiB output: restrict transient memory
        // until streaming partial publication and pooled scratch are implemented.
        const val MAX_BATCH_TILES = 24
    }
}
