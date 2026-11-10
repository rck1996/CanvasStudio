package com.orbyte.canvasstudio.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbyte.canvasstudio.model.TrashedProject
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises actual Compose gallery controls, not only storage helpers. */
class GalleryTrashUiTest {
    @get:Rule val compose = createComposeRule()

    private val deleted = TrashedProject(
        id = "project-gallery-ui-test",
        title = "Ilustración recuperable",
        deletedEpoch = 1_000L,
    )

    @Test
    fun galleryTrashDialogCanRestoreOneProject() {
        var restored: TrashedProject? = null
        compose.setContent {
            CanvasStudioTheme {
                GalleryScreen(
                    projects = emptyList(),
                    trashedProjects = listOf(deleted),
                    onNewCanvas = {},
                    onOpenProject = {},
                    onDuplicateProject = {},
                    onDeleteProject = {},
                    onRestoreLatestVersion = {},
                    onRestoreTrashedProject = { restored = it },
                )
            }
        }
        compose.onNodeWithText("Papelera (1)").performClick()
        compose.onNodeWithText(deleted.title).assertIsDisplayed()
        compose.onNodeWithText("Restaurar").performClick()
        compose.runOnIdle {
            assertEquals(deleted, restored)
        }
    }

    @Test
    fun permanentDeletionRequiresSecondConfirmation() {
        var permanentlyDeleted: TrashedProject? = null
        compose.setContent {
            CanvasStudioTheme {
                GalleryScreen(
                    projects = emptyList(),
                    trashedProjects = listOf(deleted),
                    onNewCanvas = {},
                    onOpenProject = {},
                    onDuplicateProject = {},
                    onDeleteProject = {},
                    onRestoreLatestVersion = {},
                    onDeleteTrashedProjectForever = { permanentlyDeleted = it },
                )
            }
        }
        compose.onNodeWithText("Papelera (1)").performClick()
        compose.onNodeWithText("Borrar").performClick()
        compose.runOnIdle { assertEquals(null, permanentlyDeleted) }
        compose.onNodeWithTag("gallery_trash_confirm_permanent_delete").performClick()
        compose.runOnIdle { assertEquals(deleted, permanentlyDeleted) }
    }
}
