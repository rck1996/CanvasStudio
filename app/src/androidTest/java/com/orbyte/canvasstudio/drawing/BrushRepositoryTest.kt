package com.orbyte.canvasstudio.drawing

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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBrushLibraryFormat() {
        BrushRepository.importJson("""{"format":"OtherApp","version":1,"brushes":[]}""")
    }
}
