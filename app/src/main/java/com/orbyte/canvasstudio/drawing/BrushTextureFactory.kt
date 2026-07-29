package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class BrushTextureKey(
    val source: BrushGrainSource,
    val depthStep: Int,
    val contrastStep: Int,
)

internal fun brushTextureKey(profile: BrushGrainProfile): BrushTextureKey = BrushTextureKey(
    source = profile.source,
    depthStep = (profile.depth.coerceIn(0f, 1f) * 10f).toInt(),
    contrastStep = (profile.contrast.coerceIn(0f, 1f) * 10f).toInt(),
)

/**
 * Builds a seamless alpha texture. Trigonometric basis functions meet at both
 * borders, preventing the square seams produced by a repeated non-periodic tile.
 */
internal fun createBrushGrainBitmap(key: BrushTextureKey, size: Int = 128): Bitmap {
    val pixels = IntArray(size * size)
    val depth = key.depthStep / 10f
    val contrast = key.contrastStep / 10f
    repeat(size) { y ->
        repeat(size) { x ->
            val u = x.toFloat() / size
            val v = y.toFloat() / size
            val tau = (2.0 * PI).toFloat()
            val base = (
                sin((u * 3f + v * 2f) * tau + .7f) * .24f +
                    cos((u * 7f - v * 5f) * tau + 1.9f) * .18f +
                    sin((u * 13f + v * 11f) * tau + 2.6f) * .11f +
                    .5f
                )
            val material = when (key.source) {
                BrushGrainSource.NONE -> 1f
                BrushGrainSource.PAPER_FINE -> base
                BrushGrainSource.PAPER_ROUGH -> {
                    base * .65f +
                        (sin((u * 4f - v * 3f) * tau) * .5f + .5f) * .35f
                }
                BrushGrainSource.CANVAS -> {
                    val warp = sin(u * 18f * tau) * cos(v * 17f * tau)
                    base * .44f + (warp * .5f + .5f) * .56f
                }
                BrushGrainSource.BRISTLE -> {
                    val strand = sin((u * 22f + sin(v * tau) * .35f) * tau)
                    base * .28f + (strand * .5f + .5f) * .72f
                }
                BrushGrainSource.WATERCOLOR -> {
                    val bloom = sin((u * 2f + v * 3f) * tau + 1.2f) *
                        cos((u * 3f - v * 2f) * tau)
                    base * .35f + (bloom * .5f + .5f) * .65f
                }
            }.coerceIn(0f, 1f)
            val shaped = ((material - .5f) * (1f + contrast * 2.2f) + .5f)
                .coerceIn(0f, 1f)
            val coverage = if (key.source == BrushGrainSource.NONE) {
                1f
            } else {
                (1f - depth * (.12f + shaped * .78f)).coerceIn(.08f, 1f)
            }
            pixels[y * size + x] = Color.argb((coverage * 255f).toInt(), 255, 255, 255)
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
