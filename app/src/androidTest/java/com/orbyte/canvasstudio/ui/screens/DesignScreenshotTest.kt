package com.orbyte.canvasstudio.ui.screens

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.defaultProjects
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production Compose screens rendered by the real Android 15 tablet emulator.
 * These are screenshots, not generated or hand-crafted mockups.
 */
@RunWith(AndroidJUnit4::class)
class DesignScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureGallery() {
        compose.setContent {
            CanvasStudioTheme {
                GalleryScreen(
                    projects = defaultProjects,
                    onNewCanvas = {},
                    onOpenProject = {},
                    onDuplicateProject = {},
                    onDeleteProject = {},
                    onRestoreLatestVersion = {},
                )
            }
        }
        compose.onNodeWithText("Tu estudio local de ilustración y pintura digital").assertExists()
        capture("01-galeria")
    }

    @Test
    fun captureEditorAndInspector() {
        showEditor()
        compose.onNodeWithContentDescription("Panel Capas, seleccionado").assertExists()
        capture("02-editor-capas")
    }

    @Test
    fun captureFloatingBrushPalette() {
        showEditor()
        compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
        compose.onNodeWithTag("floating_brush_palette").assertExists()
        capture("03-paleta-pinceles")
    }

    @Test
    fun captureColorInspector() {
        showEditor()
        compose.onNodeWithContentDescription("Panel Color").performClick()
        capture("04-editor-color")
    }

    private fun showEditor() {
        compose.setContent {
            CanvasStudioTheme {
                EditorScreen(
                    document = EditorDocument(
                        id = "design-emulator-screenshot",
                        title = "Ilustración montaña",
                        width = 2048,
                        height = 1536,
                        dpi = 300,
                        preview = PreviewStyle.MOUNTAIN,
                        isLocal = false,
                    ),
                    onBackToGallery = {},
                    onOpenTutorial = {},
                )
            }
        }
        compose.waitForIdle()
        // Demo artwork is seeded by the same asynchronous rasterizer as the app.
        Thread.sleep(1200L)
        compose.waitForIdle()
    }

    private fun capture(baseName: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Emulator did not return a screenshot", screenshot)
        screenshot!!
        val targetContext = instrumentation.targetContext
        val directory = File(
            requireNotNull(targetContext.getExternalFilesDir(null)),
            "screenshots",
        )
        assertTrue("Could not create screenshot directory", directory.isDirectory || directory.mkdirs())
        val file = File(directory, "$baseName.png")
        file.outputStream().use { stream ->
            assertTrue("Could not encode screenshot $baseName", screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Screenshot file is empty", file.length() > 20_000L)
        screenshot.recycle()
    }
}
