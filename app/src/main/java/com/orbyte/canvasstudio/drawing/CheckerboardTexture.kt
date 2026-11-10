package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap

/** A reusable tile matching the original 48-pixel light/dark document grid. */
internal object CheckerboardTexture {
    const val CELL_PX = 48
    private const val PERIOD_PX = CELL_PX * 2

    fun create(light: Int, dark: Int): Bitmap {
        val pixels = IntArray(PERIOD_PX * PERIOD_PX) { index ->
            val column = (index % PERIOD_PX) / CELL_PX
            val row = (index / PERIOD_PX) / CELL_PX
            if ((column + row) % 2 == 0) light else dark
        }
        return Bitmap.createBitmap(PERIOD_PX, PERIOD_PX, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, PERIOD_PX, 0, 0, PERIOD_PX, PERIOD_PX)
        }
    }
}
