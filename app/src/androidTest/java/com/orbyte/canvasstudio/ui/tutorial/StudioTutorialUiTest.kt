package com.orbyte.canvasstudio.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class StudioTutorialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun horizontalLayoutExposesCourseControlsAndAccessiblePracticeCanvas() {
        compose.setContent {
            CanvasStudioTheme {
                Box(Modifier.size(1200.dp, 700.dp)) {
                    StudioTutorialContent(StudioTutorialState(track = TutorialTrack.FULL_COURSE), {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("tutorial_root").fetchSemanticsNode()
        compose.onNodeWithTag("track_quick").fetchSemanticsNode()
        compose.onNodeWithTag("track_full").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Progreso del tutorial").fetchSemanticsNode()
    }

    @Test fun verticalLargeFontLayoutKeepsRecoveryActionsReachable() {
        compose.setContent {
            CanvasStudioTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    Box(Modifier.size(500.dp, 600.dp)) {
                        StudioTutorialContent(StudioTutorialState(), {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("tutorial_root").fetchSemanticsNode()
        compose.onNodeWithTag("tutorial_footer").fetchSemanticsNode()
        compose.onNodeWithTag("tutorial_hint").fetchSemanticsNode()
        compose.onNodeWithTag("tutorial_demo").fetchSemanticsNode()
        compose.onNodeWithTag("tutorial_skip").fetchSemanticsNode()
    }

    @Test fun completionConfirmationIsSemanticAndDoesNotAdvanceAutomatically() {
        val state = StudioTutorialState(
            current = StudioTutorialModule.SELECTION,
            progressByModule = mapOf(StudioTutorialModule.SELECTION to StudioModuleProgress(TutorialProgressStatus.COMPLETED, 1, 1L, 2)),
            confirmation = completionMessage(StudioTutorialModule.SELECTION),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent { CanvasStudioTheme { StudioTutorialContent(state, {}, {}, {}) } }
        compose.onNodeWithTag("completion_confirmation").fetchSemanticsNode()
        assertTrue(compose.onNodeWithText("Seleccion").fetchSemanticsNode().boundsInRoot.width > 100f)
        compose.onNodeWithText("Observando el resultado...").fetchSemanticsNode()
        compose.mainClock.advanceTimeBy(900L)
        compose.onNodeWithTag("completion_continue").fetchSemanticsNode()
        compose.onNodeWithText("Practicar").fetchSemanticsNode()
        compose.onNodeWithText("Repetir").fetchSemanticsNode()
    }

    @Test fun demoIsClearlyNonCompletingAndDismissible() {
        val state = StudioTutorialState(demoVisible = true)
        compose.setContent { CanvasStudioTheme { StudioTutorialContent(state, {}, {}, {}) } }
        compose.onNodeWithTag("demo_overlay").fetchSemanticsNode()
        compose.onNodeWithText("La demostracion no completa la leccion. Cierra e intentalo tu.").fetchSemanticsNode()
        compose.onNodeWithText("Ahora lo intento").fetchSemanticsNode()
    }
}
