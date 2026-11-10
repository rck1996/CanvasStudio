package com.orbyte.canvasstudio.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbyte.canvasstudio.model.LocalProjectVersion
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProjectVersionHistoryUiTest {
    @get:Rule val compose = createComposeRule()

    private val project = ProjectCard(
        id = "history-test",
        title = "Dibujo de prueba",
        width = 1024,
        height = 768,
        modifiedLabel = "Guardado local",
        preview = PreviewStyle.SKETCH,
        isLocal = true,
    )
    private val versions = listOf(
        LocalProjectVersion(project.id, 1_000L, File("/nonexistent/version-1000")),
        LocalProjectVersion(project.id, 2_000L, File("/nonexistent/version-2000")),
    )

    @Test
    fun choosesAnOlderVersionOnlyAfterExplicitConfirmation() {
        var restored: LocalProjectVersion? = null
        compose.setContent {
            CanvasStudioTheme {
                ProjectVersionHistoryDialog(
                    project = project,
                    versions = versions,
                    onDismiss = {},
                    onRestore = { restored = it },
                )
            }
        }
        compose.onNodeWithTag("restore_version_1000").performClick()
        compose.runOnIdle { assertNull(restored) }
        compose.onNodeWithTag("confirm_restore_version").performClick()
        compose.runOnIdle { assertEquals(versions[0], restored) }
    }

    @Test
    fun cancellingRestoreLeavesTheProjectUntouched() {
        var restored: LocalProjectVersion? = null
        compose.setContent {
            CanvasStudioTheme {
                ProjectVersionHistoryDialog(
                    project = project,
                    versions = versions,
                    onDismiss = {},
                    onRestore = { restored = it },
                )
            }
        }
        compose.onNodeWithTag("restore_version_2000").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertNull(restored) }
    }
}
