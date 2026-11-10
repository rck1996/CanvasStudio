package com.orbyte.canvasstudio.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickDialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun directDragUpdatesBrushSizeWithoutOpeningBrushStudio() {
        var progress by mutableFloatStateOf(.15f)
        compose.setContent {
            CanvasStudioTheme {
                QuickDial(
                    label = "Tamaño",
                    progress = progress,
                    value = "${(progress * 100f).toInt()} px",
                    onDecrease = { progress = (progress - .05f).coerceAtLeast(0f) },
                    onIncrease = { progress = (progress + .05f).coerceAtMost(1f) },
                    onProgressChanged = { progress = it },
                )
            }
        }
        compose.onNodeWithTag("quick_slider_size").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue("Size should update while dragging", progress > .75f) }
    }

    @Test
    fun opacitySliderIsExposedAsAnAccessibleControl() {
        var progress by mutableFloatStateOf(.30f)
        compose.setContent {
            CanvasStudioTheme {
                QuickDial(
                    label = "Opacidad",
                    progress = progress,
                    value = "${(progress * 100f).toInt()}%",
                    onDecrease = {},
                    onIncrease = {},
                    onProgressChanged = { progress = it },
                )
            }
        }
        compose.onNodeWithTag("quick_slider_opacity").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(progress > .75f) }
    }
}
