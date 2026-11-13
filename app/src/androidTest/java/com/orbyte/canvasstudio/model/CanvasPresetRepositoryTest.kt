package com.orbyte.canvasstudio.model

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasPresetRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun canvasFormatsPersistWithoutChangingTheBundledPresets() {
        reset()
        try {
            assertTrue(CanvasPresetRepository.upsert(context, "Mi cómic", 2400, 3200, 300))
            val found = CanvasPresetRepository.load(context).single()
            assertEquals("Mi cómic", found.title)
            assertEquals(2400, found.width)
            assertEquals(3200, found.height)
            assertEquals(300, found.dpi)
            assertTrue(canvasPresets.none { it.title == "Mi cómic" })
            assertTrue(CanvasPresetRepository.remove(context, "mi CÓMIC"))
            assertTrue(CanvasPresetRepository.load(context).isEmpty())
        } finally { reset() }
    }

    @Test
    fun validatesDimensionsAndUpdatesNamesWithoutDuplicates() {
        reset()
        try {
            assertFalse(CanvasPresetRepository.upsert(context, "Ilustración", 1024, 1024, 300))
            assertFalse(CanvasPresetRepository.upsert(context, " ", 1024, 1024, 300))
            assertFalse(CanvasPresetRepository.upsert(context, "Gigante", 8192, 8192, 300))
            assertFalse(CanvasPresetRepository.upsert(context, "DPI inválidos", 1024, 1024, 650))
            assertTrue(CanvasPresetRepository.upsert(context, "Banner", 1200, 800, 150))
            assertTrue(CanvasPresetRepository.upsert(context, "banner", 1600, 900, 200))
            assertEquals(1, CanvasPresetRepository.load(context).size)
            assertEquals(1600, CanvasPresetRepository.load(context).single().width)
        } finally { reset() }
    }

    @Test
    fun userPresetsAreBoundedAndMalformedStorageDegradesToEmpty() {
        reset()
        try {
            repeat(CanvasPresetRepository.MAX_CUSTOM_PRESETS) {
                assertTrue(CanvasPresetRepository.upsert(context, "Formato $it", 512, 512, 300))
            }
            assertFalse(CanvasPresetRepository.upsert(context, "Formato extra", 512, 512, 300))
            assertEquals(CanvasPresetRepository.MAX_CUSTOM_PRESETS, CanvasPresetRepository.load(context).size)
            context.getSharedPreferences("canvas_studio_canvas_presets", Context.MODE_PRIVATE)
                .edit().putString("custom_presets_v1", "corrupt JSON").commit()
            assertTrue(CanvasPresetRepository.load(context).isEmpty())
        } finally { reset() }
    }

    private fun reset() {
        context.getSharedPreferences("canvas_studio_canvas_presets", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
