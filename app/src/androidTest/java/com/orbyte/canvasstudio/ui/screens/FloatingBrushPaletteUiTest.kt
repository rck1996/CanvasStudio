package com.orbyte.canvasstudio.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.drawing.premiumBrushes
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FloatingBrushPaletteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosePresetWithoutBlockingTheCanvas() {
        val presets = premiumBrushes.take(3)
        var chosen: String? = null
        compose.setContent {
            CanvasStudioTheme {
                FloatingBrushPalette(
                    brushes = presets,
                    selectedPresetId = presets.first().id,
                    favoriteBrushIds = emptySet(),
                    recentBrushIds = emptyList(),
                    maxPanelHeight = 440.dp,
                    onPresetSelected = { chosen = it.id },
                    onOpenAdvanced = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("floating_brush_palette").assertExists()
        val desired = presets[1]
        compose.onNodeWithContentDescription("Pincel ${desired.name}, ${desired.category}")
            .performClick()
        compose.runOnIdle { assertEquals(desired.id, chosen) }
    }

    @Test fun advancedLibraryStillOpensFromFloatingPalette() {
        val presets = premiumBrushes.take(2)
        var advanced = false
        compose.setContent {
            CanvasStudioTheme {
                FloatingBrushPalette(
                    brushes = presets,
                    selectedPresetId = presets.first().id,
                    favoriteBrushIds = emptySet(),
                    recentBrushIds = emptyList(),
                    maxPanelHeight = 440.dp,
                    onPresetSelected = {},
                    onOpenAdvanced = { advanced = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("floating_brush_advanced").performClick()
        compose.runOnIdle { assertTrue(advanced) }
    }

    @Test fun draggingHeaderMovesPaletteWithoutClosingIt() {
        val presets = premiumBrushes.take(2)
        var horizontalMotion = 0f
        compose.setContent {
            CanvasStudioTheme {
                FloatingBrushPalette(
                    brushes = presets,
                    selectedPresetId = presets.first().id,
                    favoriteBrushIds = emptySet(),
                    recentBrushIds = emptyList(),
                    maxPanelHeight = 440.dp,
                    onPresetSelected = {},
                    onOpenAdvanced = {},
                    onDismiss = {},
                    onDragBy = { distance -> horizontalMotion += distance.x },
                )
            }
        }
        compose.onNodeWithTag("floating_brush_drag_handle").performTouchInput {
            swipe(
                start = Offset(width * .15f, height * .5f),
                end = Offset(width * .70f, height * .5f),
                durationMillis = 320L,
            )
        }
        compose.runOnIdle { assertTrue("Header must accept a drag", horizontalMotion > 8f) }
        compose.onNodeWithTag("floating_brush_palette").assertExists()
    }

    @Test fun searchShowsEmptyStateRatherThanBlockingOrClosingEditor() {
        val presets = premiumBrushes.take(2)
        compose.setContent {
            CanvasStudioTheme {
                FloatingBrushPalette(
                    brushes = presets,
                    selectedPresetId = presets.first().id,
                    favoriteBrushIds = emptySet(),
                    recentBrushIds = emptyList(),
                    maxPanelHeight = 440.dp,
                    onPresetSelected = {},
                    onOpenAdvanced = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("floating_brush_search").performTextInput("zzzzNoBrush")
        compose.onNodeWithTag("floating_brush_palette").assertExists()
        compose.onNodeWithTag("floating_brush_advanced").assertExists()
    }
}
