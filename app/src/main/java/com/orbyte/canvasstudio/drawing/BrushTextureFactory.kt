package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class MaterialTip { CHARCOAL, DRY_PAINT }

/** Original procedural tip. The jagged silhouette belongs to the tip, not to a blur filter. */
internal fun createMaterialTip(material: MaterialTip, size: Int = 128): Bitmap {
    val pixels = IntArray(size * size)
    fun noise(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263 + material.ordinal * 1013904223
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 65535) / 65535f
    }
    repeat(size) { y ->
        repeat(size) { x ->
            val u = (x + .5f) * 2f / size - 1f
            val v = (y + .5f) * 2f / size - 1f
            val grain = noise(x, y)
            val cluster = if (material == MaterialTip.CHARCOAL) {
                val finer = noise(x / 2, y / 2)
                finer * .62f + noise(x / 5, y / 5) * .38f
            } else noise(x / 3, y / 3)
            val contour = .90f + sin(u * 21f + v * 9f) * .055f + sin(v * 28f - u * 8f) * .045f
            val distance = sqrt(u * u + v * v)
            val edge = ((contour - distance + (cluster - .5f) * .15f) * 10f).coerceIn(0f, 1f)
            val materialCoverage = when (material) {
                MaterialTip.CHARCOAL -> {
                    val tooth = (grain * .66f + cluster * .34f - .20f).coerceIn(0f, .70f) / .70f
                    .18f + tooth * .79f
                }
                MaterialTip.DRY_PAINT -> {
                    val fiber = sin(v * 37f + u * 2.2f + sin(u * 8f) * .45f)
                    (.54f + fiber * .29f + grain * .22f).coerceIn(.03f, 1f)
                }
            }
            pixels[y * size + x] = Color.argb((edge * materialCoverage * 255f).toInt(), 255, 255, 255)
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

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
                    // Irregular sediment at three scales, with no hard grid
                    // and no large periodic wave crossing the painted patch.
                    val pools = periodicGrain(u, v, 5, 17)
                    val tooth = periodicGrain(u, v, 21, 43)
                    val sediment = periodicGrain(u, v, 54, 97)
                    (pools * .36f + tooth * .37f + sediment * .27f - .20f) / .60f
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

private fun periodicGrain(u: Float, v: Float, cells: Int, seed: Int): Float {
    val x = u * cells
    val y = v * cells
    val ix = x.toInt()
    val iy = y.toInt()
    val fx = x - ix
    val fy = y - iy
    val sx = fx * fx * (3f - 2f * fx)
    val sy = fy * fy * (3f - 2f * fy)
    val a = grainCell(ix, iy, cells, seed)
    val b = grainCell(ix + 1, iy, cells, seed)
    val c = grainCell(ix, iy + 1, cells, seed)
    val d = grainCell(ix + 1, iy + 1, cells, seed)
    val top = a + (b - a) * sx
    val bottom = c + (d - c) * sx
    return top + (bottom - top) * sy
}

private fun grainCell(x: Int, y: Int, period: Int, seed: Int): Float {
    var hash = x % period * 374761393 + y % period * 668265263 + seed * 1013904223
    hash = (hash xor (hash ushr 13)) * 1274126177
    return ((hash xor (hash ushr 16)) and 65535) / 65535f
}
