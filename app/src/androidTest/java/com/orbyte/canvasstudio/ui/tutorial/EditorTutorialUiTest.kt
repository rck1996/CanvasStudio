package com.orbyte.canvasstudio.ui.tutorial

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Rule
import org.junit.Test

class EditorTutorialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun realEditorOverlayStartsAtOneOfFourteenAndExposesRecoveryActions() {
        val session = session(StudioTutorialState(track = TutorialTrack.FULL_COURSE))
        compose.setContent {
            CanvasStudioTheme {
                Box(Modifier.size(1200.dp, 700.dp)) {
                    EditorTutorialOverlay(session, rememberTutorialFocusRegistry(), {}, {})
                }
            }
        }
        compose.onNodeWithTag("editor_tutorial_overlay").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_card").fetchSemanticsNode()
        compose.onNodeWithText("1/14 · Navegación del lienzo").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_hint").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_restart").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Salir del tutorial").fetchSemanticsNode()
    }

    @Test
    fun largeFontTabletLayoutKeepsCardScrollableAndControlsReachable() {
        val session = session(StudioTutorialState(track = TutorialTrack.FULL_COURSE))
        compose.setContent {
            CanvasStudioTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                    Box(Modifier.size(800.dp, 480.dp)) {
                        EditorTutorialOverlay(session, rememberTutorialFocusRegistry(), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("editor_tutorial_card").fetchSemanticsNode()
        compose.onNodeWithText("Minimizar").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_hint").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_restart").fetchSemanticsNode()
    }

    @Test
    fun verifiedResultIsShownBeforeContinueBecomesAvailable() {
        val completed = StudioTutorialState(
            track = TutorialTrack.FULL_COURSE,
            current = StudioTutorialModule.SELECTION,
            progressByModule = mapOf(
                StudioTutorialModule.SELECTION to StudioModuleProgress(
                    status = TutorialProgressStatus.COMPLETED,
                    step = 1,
                    completedAtEpochMs = 1L,
                    lessonVersion = StudioTutorialModule.SELECTION.lessonVersion,
                ),
            ),
            evidence = setOf(TutorialEvidence.SELECTION_VALID),
            confirmation = completionMessage(StudioTutorialModule.SELECTION),
        )
        val session = session(completed)
        compose.setContent {
            CanvasStudioTheme {
                EditorTutorialOverlay(session, rememberTutorialFocusRegistry(), {}, {})
            }
        }
        compose.onNodeWithText("Selección válida creada").fetchSemanticsNode()
        compose.onNodeWithTag("editor_tutorial_continue").fetchSemanticsNode()
    }

    private fun session(state: StudioTutorialState): EditorTutorialSession {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("editor-overlay-ui-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        return EditorTutorialSession(state, preferences)
    }
}
