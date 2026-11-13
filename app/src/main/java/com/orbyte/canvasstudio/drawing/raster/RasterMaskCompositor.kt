package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/** One thread owns each compositor. Stored mask pixels always mean coverage to hide. */
internal class RasterMaskCompositor {
    private val subtract = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val subtractInverse = Paint(subtract)
    private val opaque = Paint().apply { color = Color.WHITE }

    fun apply(canvas: Canvas, bounds: RectF, alpha: Int, inverted: Boolean, drawMask: (Paint) -> Unit) {
        if (alpha == 0) return
        if (!inverted) {
            subtract.alpha = alpha
            drawMask(subtract)
            return
        }
        // Inversion includes missing/transparent tiles. Subtracting the authored mask
        // from an opaque viewport/band gives 1 - coverage without rewriting tiles.
        subtractInverse.alpha = alpha
        val saved = canvas.saveLayer(bounds, subtractInverse)
        try {
            canvas.drawRect(bounds, opaque)
            subtract.alpha = 255
            drawMask(subtract)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
