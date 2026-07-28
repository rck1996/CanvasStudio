package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("canvas_studio_brushes", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun favoritesAndRecentsPersistWithoutDuplicates() {
        BrushRepository.saveFavorites(context, setOf("pencil", "ink"))
        assertEquals(setOf("pencil", "ink"), BrushRepository.loadFavorites(context))

        var recents = emptyList<String>()
        repeat(BrushRepository.MAX_RECENT_BRUSHES + 4) { recents = BrushRepository.recordRecent(context, "brush-$it", recents) }
        recents = BrushRepository.recordRecent(context, "brush-8", recents)

        assertEquals(BrushRepository.MAX_RECENT_BRUSHES, recents.size)
        assertEquals("brush-8", recents.first())
        assertEquals(recents.distinct(), BrushRepository.loadRecents(context))
    }

    @Test
    fun exportedBrushesRoundTripAndReceiveSafeLocalIds() {
        val original = premiumBrushes.take(2)
        val exported = BrushRepository.exportJson(original)
        val imported = BrushRepository.importJson(exported)

        assertEquals(2, imported.size)
        assertEquals(original.map { it.name }, imported.map { it.name })
        assertTrue(imported.all { it.id.startsWith("custom-") })
        assertTrue(imported.all { it.category == "Personalizados" })
        assertFalse(imported.map { it.id }.distinct().size != imported.size)
    }

    @Test
    fun importedBitmapTipBecomesACompactAlphaMask() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 80 until 120) {
                for (x in 180 until 220) setPixel(x, y, Color.BLACK)
            }
        }
        val input = java.io.File(context.cacheDir, "brush-tip-test.png")
        input.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()

        val importedPath = BrushRepository.importTipAsset(context, Uri.fromFile(input))
        val imported = BitmapFactory.decodeFile(importedPath)
        var roundTripPath: String? = null
        try {
            assertTrue(imported.width <= 256)
            assertTrue(imported.height <= 256)
            assertEquals(0, Color.alpha(imported.getPixel(0, 0)))
            assertEquals(255, Color.alpha(imported.getPixel(imported.width / 2, imported.height / 2)))
            val exported = BrushRepository.exportJsonWithAssets(
                listOf(premiumBrushes.first().copy(tipAssetPath = importedPath)),
            )
            roundTripPath = BrushRepository.importJsonWithAssets(context, exported).single().tipAssetPath
            assertTrue(roundTripPath?.let { java.io.File(it) }?.isFile == true)
        } finally {
            imported.recycle()
            input.delete()
            java.io.File(importedPath).delete()
            roundTripPath?.let { java.io.File(it) }?.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBrushLibraryFormat() {
        BrushRepository.importJson("""{"format":"OtherApp","version":1,"brushes":[]}""")
    }
}
