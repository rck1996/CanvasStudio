package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import kotlin.math.max
import kotlin.math.min

/**
 * Off-main-thread composite of an immutable layer-configuration snapshot.
 *
 * Each SparseTileSurface owns its tile locks. The compositor owns its paints;
 * it never reads or mutates DrawingView, touches the screen, or persists tiles.
 * A background save can use this without drawing 720px thumbnails on the UI.
 *
 * The snapshot contains references to live raster surfaces rather than copies
 * of pixels. Saving still uses save-generation checks and per-tile snapshots.
 */
internal class PreviewCompositor {
    data class Layer(
        val surface: SparseTileSurface,
        val maskSurface: SparseTileSurface?,
        val visible: Boolean,
        val opacity: Float,
        val blendMode: LayerBlendMode,
        val clipping: Boolean,
        val maskEnabled: Boolean,
    )

    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clippingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    fun render(width: Int, height: Int, layers: List<Layer>, maxSize: Int = 720): Bitmap {
        require(width > 0 && height > 0 && maxSize > 0)
        val scale = min(1f, maxSize / max(width, height).toFloat())
        val preview = Bitmap.createBitmap(
            max(1, (width * scale).toInt()),
            max(1, (height * scale).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        try {
            val canvas = Canvas(preview)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            val documentBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            layers.forEachIndexed { index, layer ->
                if (!layer.visible) return@forEachIndexed
                val mask = layer.maskSurface.takeIf { layer.maskEnabled }
                val clippingBase = layers.getOrNull(index - 1).takeIf { layer.clipping }
                if (mask == null && clippingBase == null) {
                    configureLayerPaint(layer)
                    layer.surface.drawAll(canvas, layerPaint)
                    return@forEachIndexed
                }

                val checkpoint = canvas.saveLayer(documentBounds, null)
                try {
                    configureLayerPaint(layer)
                    layer.surface.drawAll(canvas, layerPaint)
                    if (mask != null) {
                        maskPaint.alpha = 255
                        mask.drawAll(canvas, maskPaint)
                    }
                    if (clippingBase != null && clippingBase.visible) {
                        clippingPaint.alpha = (clippingBase.opacity * 255f).toInt().coerceIn(0, 255)
                        clippingBase.surface.drawAll(canvas, clippingPaint)
                        if (clippingBase.maskEnabled) {
                            clippingBase.maskSurface?.drawAll(canvas, maskPaint)
                        }
                    }
                } finally {
                    canvas.restoreToCount(checkpoint)
                }
            }
            return preview
        } catch (error: Throwable) {
            preview.recycle()
            throw error
        }
    }

    private fun configureLayerPaint(layer: Layer) {
        layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            layerPaint.xfermode = null
            layerPaint.blendMode = when (layer.blendMode) {
                LayerBlendMode.NORMAL -> null
                LayerBlendMode.MULTIPLY -> BlendMode.MULTIPLY
                LayerBlendMode.SCREEN -> BlendMode.SCREEN
                LayerBlendMode.OVERLAY -> BlendMode.OVERLAY
                LayerBlendMode.ADD -> BlendMode.PLUS
                LayerBlendMode.DARKEN -> BlendMode.DARKEN
                LayerBlendMode.LIGHTEN -> BlendMode.LIGHTEN
                LayerBlendMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
                LayerBlendMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
                LayerBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
                LayerBlendMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
                LayerBlendMode.COLOR_BURN -> BlendMode.COLOR_BURN
            }
        } else {
            // Do not call Paint.setBlendMode on API 26-28.
            layerPaint.xfermode = when (layer.blendMode) {
                LayerBlendMode.NORMAL -> null
                LayerBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                LayerBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                LayerBlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                LayerBlendMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                LayerBlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                LayerBlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                LayerBlendMode.SOFT_LIGHT,
                LayerBlendMode.HARD_LIGHT,
                LayerBlendMode.DIFFERENCE,
                LayerBlendMode.COLOR_DODGE,
                LayerBlendMode.COLOR_BURN -> null
            }
        }
    }
}
