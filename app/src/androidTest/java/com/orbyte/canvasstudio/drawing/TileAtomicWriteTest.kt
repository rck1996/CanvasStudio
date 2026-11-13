package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TileAtomicWriteTest {
    @Test
    fun overwritesTileAndDeletesFullyTransparentTile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "tile-atomic-${System.nanoTime()}").apply { mkdirs() }
        val key = TileStorage.Key(0, 0)
        val red = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val empty = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(TileStorage.WriteResult.WRITTEN, TileStorage.saveTileBitmapAtomically(red, key, directory))
            assertEquals(TileStorage.WriteResult.WRITTEN, TileStorage.saveTileBitmapAtomically(blue, key, directory))
            val loaded = requireNotNull(TileStorage.loadTile(File(directory, key.fileName)))
            try {
                assertEquals(Color.BLUE, loaded.getPixel(0, 0))
            } finally {
                loaded.recycle()
            }
            assertEquals(TileStorage.WriteResult.DELETED, TileStorage.saveTileBitmapAtomically(empty, key, directory))
            assertFalse(File(directory, key.fileName).exists())
            assertTrue(TileStorage.existingKeys(directory).isEmpty())
        } finally {
            red.recycle()
            blue.recycle()
            empty.recycle()
            directory.deleteRecursively()
        }
    }
}
