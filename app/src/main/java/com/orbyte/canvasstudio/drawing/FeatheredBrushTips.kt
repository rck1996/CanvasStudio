package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Coverage masks for felt and loaded flat paint; cached and tinted by the raster session. */
internal enum class FeatheredBrushTip(
    val length: Float,
    private val corner: Float,
    private val longitudinalFeather: Float,
    private val transverseFeather: Float,
) {
    ROUND_FELT(1f, 1f, .32f, .11f),
    FLAT_FELT(.92f, .22f, .38f, .12f),
    LOADED_OIL(.86f, .22f, .44f, .17f);

    fun createMask(): Bitmap {
        val pixels = IntArray(SIZE * SIZE) { i ->
            // Leave the edges transparent for the shader's CLAMP sampling.
            val x = abs((i % SIZE + .5f - SIZE / 2f) / PIXEL_RADIUS)
            val y = abs((i / SIZE + .5f - SIZE / 2f) / PIXEL_RADIUS)
            val qx = x - (1f - corner)
            val qy = y - (1f - corner)
            val distance = hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - corner
            val across = y / max(x + y, .001f)
            val feather = longitudinalFeather + (transverseFeather - longitudinalFeather) * across
            val t = (-distance / feather).coerceIn(0f, 1f)
            val coverage = t * t * (3f - 2f * t)
            Color.argb((coverage * 255f).roundToInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    companion object {
        const val SIZE = 128
        const val PIXEL_RADIUS = 61f

        fun forSettings(settings: BrushSettings): FeatheredBrushTip? = when {
            settings.tipAssetPath != null -> null
            settings.kind == BrushKind.MARKER -> when (settings.tipProfile.shape) {
                BrushTipShape.CHISEL -> FLAT_FELT
                BrushTipShape.ROUND, BrushTipShape.OVAL -> ROUND_FELT
                else -> null
            }
            settings.kind == BrushKind.OIL && settings.tipProfile.shape == BrushTipShape.CHISEL -> LOADED_OIL
            else -> null
        }
    }
}
