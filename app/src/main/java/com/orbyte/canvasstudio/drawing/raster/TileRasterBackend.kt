package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Canvas
import android.graphics.RectF
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import com.orbyte.canvasstudio.drawing.TileStorage

/**
 * Explicit seam between brush evaluation and the production Canvas/Bitmap tile renderer.
 * A future GPU backend must preserve these local-coordinate and alpha-lock semantics.
 */
internal interface TileRasterBackend {
    fun rasterize(
        surface: SparseTileSurface,
        bounds: RectF,
        preserveAlpha: Boolean,
        draw: (Canvas) -> Unit,
    ): TileRasterResult
}

internal data class TileRasterResult(
    val changed: Boolean,
    val touchedTiles: Int,
)

/** Wraps the proven sparse Canvas backend without changing image output. */
internal class BitmapCanvasTileRasterBackend : TileRasterBackend {
    override fun rasterize(
        surface: SparseTileSurface,
        bounds: RectF,
        preserveAlpha: Boolean,
        draw: (Canvas) -> Unit,
    ): TileRasterResult {
        val changed = if (preserveAlpha) {
            surface.drawPreservingAlpha(bounds, draw)
        } else {
            surface.draw(bounds, draw)
        }
        return TileRasterResult(
            changed = changed,
            touchedTiles = if (changed) TileStorage.keysForBounds(bounds, surface.width, surface.height).size else 0,
        )
    }
}
