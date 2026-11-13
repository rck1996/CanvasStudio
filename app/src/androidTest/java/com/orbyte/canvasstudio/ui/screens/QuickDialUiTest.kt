package com.orbyte.canvasstudio.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
        // Begin over the actual thumb instead of using swipeRight(), whose
        // default 10%-width starting point is left of the thumb at 15%.
        compose.onNodeWithTag("quick_slider_size").performTouchInput {
            swipe(
                start = Offset(width * .15f, height * .5f),
                end = Offset(width * .93f, height * .5f),
                durationMillis = 500L,
            )
        }
        compose.runOnIdle { assertTrue("Size should update while dragging the thumb", progress > .70f) }
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
        // Accessibility is a separate contract from touch gestures:
        // TalkBack and switch access must be able to set the progress directly.
        val slider = compose.onNodeWithTag("quick_slider_opacity")
        slider.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(.85f)
        }
        compose.runOnIdle { assertTrue("Accessible opacity progress was not applied", progress > .80f) }
    }
}
