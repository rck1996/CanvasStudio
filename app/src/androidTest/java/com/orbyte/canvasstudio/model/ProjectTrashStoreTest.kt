package com.orbyte.canvasstudio.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectTrashStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun movingToTrashHidesProjectAndRestoringPreservesArtworkAndVersions() {
        val id = ProjectRepository.newProjectId()
        val directory = seed(id)
        assertTrue(ProjectVersionStore.maybeSnapshot(context, id, directory, 10_000L, 0L))
        try {
            assertTrue(ProjectTrashStore.moveToTrash(context, id))
            assertFalse(directory.exists())
            assertFalse(ProjectRepository.loadLocalProjects(context).any { it.id == id })
            assertEquals("Artwork", ProjectTrashStore.list(context).first { it.id == id }.title)
            assertEquals(1, ProjectVersionStore.list(context, id).size)
            assertTrue(ProjectTrashStore.restore(context, id))
            assertTrue(ProjectRepository.loadLocalProjects(context).any { it.id == id })
            assertEquals("paint data", File(directory, "layers/paint.bin").readText())
            assertEquals(1, ProjectVersionStore.list(context, id).size)
            assertFalse(ProjectTrashStore.list(context).any { it.id == id })
        } finally {
            ProjectRepository.deleteProject(context, id)
            ProjectTrashStore.deleteForever(context, id)
        }
    }

    @Test
    fun cannotRestoreOverExistingProject() {
        val id = ProjectRepository.newProjectId()
        val project = seed(id)
        try {
            assertTrue(ProjectTrashStore.moveToTrash(context, id))
            project.mkdirs()
            File(project, "project.properties").writeText(metadata(id, "New project"))
            assertFalse(ProjectTrashStore.restore(context, id))
            assertEquals("New project", ProjectRepository.loadLocalProjects(context).first { it.id == id }.title)
            assertTrue(ProjectTrashStore.list(context).any { it.id == id })
        } finally {
            ProjectRepository.deleteProject(context, id)
            ProjectTrashStore.deleteForever(context, id)
        }
    }

    @Test
    fun permanentDeleteRemovesTrashedProjectAndItsVersions() {
        val id = ProjectRepository.newProjectId()
        val project = seed(id)
        assertTrue(ProjectVersionStore.maybeSnapshot(context, id, project, 10_000L, 0L))
        try {
            assertTrue(ProjectTrashStore.moveToTrash(context, id))
            assertTrue(ProjectTrashStore.deleteForever(context, id))
            assertFalse(ProjectTrashStore.list(context).any { it.id == id })
            assertTrue(ProjectVersionStore.list(context, id).isEmpty())
            assertFalse(ProjectTrashStore.restore(context, id))
        } finally {
            ProjectRepository.deleteProject(context, id)
            ProjectTrashStore.deleteForever(context, id)
        }
    }

    @Test
    fun cannotTrashProjectDuringActiveSave() {
        val id = ProjectRepository.newProjectId()
        seed(id)
        val transaction = ProjectSaveTransaction.begin(context, id)
        try {
            assertFalse(ProjectTrashStore.moveToTrash(context, id))
            assertTrue(ProjectRepository.loadLocalProjects(context).any { it.id == id })
        } finally {
            transaction.abort()
            ProjectRepository.deleteProject(context, id)
        }
    }

    private fun seed(id: String): File =
        ProjectRepository.projectDirectory(context, id).apply {
            File(this, "project.properties").writeText(metadata(id, "Artwork"))
            val layers = File(this, "layers").apply { mkdirs() }
            File(layers, "paint.bin").writeText("paint data")
        }

    private fun metadata(id: String, title: String) =
        "id=$id\nwidth=512\nheight=512\ntitle=$title\n"
}
