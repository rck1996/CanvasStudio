package com.orbyte.canvasstudio.model

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectVersionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun snapshotsAreThrottledAndPruned() {
        val projectId = "version-test-${System.nanoTime()}"
        val project = ProjectRepository.projectDirectory(context, projectId)
        File(project, "project.properties").writeText("id=$projectId\nwidth=64\nheight=64\n")

        assertTrue(ProjectVersionStore.maybeSnapshot(context, projectId, project, 1_000L, 0L, 2))
        assertTrue(ProjectVersionStore.maybeSnapshot(context, projectId, project, 2_000L, 0L, 2))
        assertTrue(ProjectVersionStore.maybeSnapshot(context, projectId, project, 3_000L, 0L, 2))
        assertEquals(listOf(3_000L, 2_000L), ProjectVersionStore.list(context, projectId).map { it.createdEpoch })
        assertFalse(ProjectVersionStore.maybeSnapshot(context, projectId, project, 3_500L, 1_000L, 2))

        ProjectRepository.deleteProject(context, projectId)
    }

    @Test
    fun restoresACompleteVersion() {
        val projectId = "restore-test-${System.nanoTime()}"
        val project = ProjectRepository.projectDirectory(context, projectId)
        File(project, "project.properties").writeText("id=$projectId\nwidth=64\nheight=64\n")
        File(project, "marker.txt").writeText("original")
        assertTrue(ProjectVersionStore.maybeSnapshot(context, projectId, project, 1_000L, 0L, 3))

        File(project, "marker.txt").writeText("changed")
        val version = ProjectVersionStore.list(context, projectId).single()
        assertTrue(ProjectVersionStore.restore(context, version))
        assertEquals("original", File(project, "marker.txt").readText())

        ProjectRepository.deleteProject(context, projectId)
    }
}
