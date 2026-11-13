package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * A single bitmap-backed Canvas for a sparse surface's serialized tile mutations.
 *
 * Tile coordinates remain in document space. The owner must serialize access
 * (SparseTileSurface uses its surface lock), and must not retain the Canvas or
 * mutable bounds outside the callback. No bitmap or clip state leaks across tiles.
 */
internal class TileCanvasScope {
    private val canvas = Canvas()
    private val documentBounds = RectF()

    fun drawTile(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        draw: (Canvas, RectF) -> Unit,
    ) {
        check(!bitmap.isRecycled) { "Cannot rasterize a recycled tile" }
        canvas.setBitmap(bitmap)
        val checkpoint = canvas.save()
        try {
            documentBounds.set(
                left.toFloat(),
                top.toFloat(),
                (left + bitmap.width).toFloat(),
                (top + bitmap.height).toFloat(),
            )
            canvas.translate(-left.toFloat(), -top.toFloat())
            canvas.clipRect(documentBounds)
            draw(canvas, documentBounds)
        } finally {
            canvas.restoreToCount(checkpoint)
            canvas.setBitmap(null)
        }
    }

    fun composite(destination: Bitmap, source: Bitmap, paint: Paint) {
        check(!destination.isRecycled && !source.isRecycled)
        canvas.setBitmap(destination)
        try {
            canvas.drawBitmap(source, 0f, 0f, paint)
        } finally {
            canvas.setBitmap(null)
        }
    }
}
