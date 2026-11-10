package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CanvasFormatUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dimensionFieldCanBeClearedAndReplaced() {
        var width by mutableIntStateOf(4096)
        compose.setContent {
            CanvasStudioTheme {
                NumberField("Ancho", width, Modifier.width(180.dp)) { width = it }
            }
        }
        compose.onNodeWithTag("canvas_field_ancho").performTextClearance()
        compose.onNodeWithTag("canvas_field_ancho").performTextInput("2048")
        compose.runOnIdle { assertEquals(2048, width) }
    }

    @Test
    fun portraitLandscapeSwapChangesCreatedCanvasDimensions() {
        var created: EditorDocument? = null
        compose.setContent {
            CanvasStudioTheme {
                NewCanvasDialog(onDismiss = {}, onCreate = { created = it })
            }
        }
        compose.onNodeWithTag("canvas_swap_orientation").performClick()
        compose.onNodeWithText("Crear lienzo").performClick()
        compose.runOnIdle {
            assertNotNull(created)
            assertEquals(2732, created?.width)
            assertEquals(4096, created?.height)
        }
    }
}
