package com.orbyte.canvasstudio.model

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectSaveTransactionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun modifiedLayersAreInvisibleUntilCommit() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val layerFile = File(live, "layers/layer-one/tiles/0_0.png")
        try {
            val transaction = ProjectSaveTransaction.begin(context, id)
            val staged = File(transaction.workingDirectory, "layers/layer-one/tiles/0_0.png")
            atomicReplace(staged, "updated raster")
            atomicReplace(File(transaction.workingDirectory, "project.properties"), metadata(id, "New"))
            assertEquals("original raster", layerFile.readText())
            assertEquals("Original", ProjectRepository.loadLocalProjects(context).first { it.id == id }.title)
            transaction.commit()
            transaction.abort() // No-op after commit.
            assertEquals("updated raster", layerFile.readText())
            assertEquals("New", ProjectRepository.loadLocalProjects(context).first { it.id == id }.title)
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun abortDiscardsAStagedDeletionWithoutTouchingTheOriginal() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val transaction = ProjectSaveTransaction.begin(context, id)
        val stagedTile = File(transaction.workingDirectory, "layers/layer-one/tiles/0_0.png")
        assertTrue(stagedTile.delete())
        transaction.abort()
        try {
            assertEquals(
                "original raster",
                File(live, "layers/layer-one/tiles/0_0.png").readText(),
            )
            assertFalse(File(live.parentFile, "$id.save-stage").exists())
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun interruptedUnpublishedStageIsDiscardedWhenGalleryLoads() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val stage = File(live.parentFile, "$id.save-stage")
        assertTrue(stage.mkdirs())
        File(stage, "partial.txt").writeText("incomplete")
        try {
            assertTrue(ProjectRepository.loadLocalProjects(context).any { it.id == id })
            assertFalse(stage.exists())
            assertEquals("original raster", File(live, "layers/layer-one/tiles/0_0.png").readText())
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun interruptedDirectorySwapRestoresTheLastPublishedProject() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val backup = File(live.parentFile, "$id.before-save")
        val stage = File(live.parentFile, "$id.save-stage")
        assertTrue(live.renameTo(backup))
        assertTrue(stage.mkdirs())
        File(stage, "incomplete.txt").writeText("half-written")
        try {
            val recovered = ProjectRepository.loadLocalProjects(context).first { it.id == id }
            assertEquals("Original", recovered.title)
            assertFalse(stage.exists())
            assertFalse(backup.exists())
            assertEquals("original raster", File(live, "layers/layer-one/tiles/0_0.png").readText())
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun completedPublishSurvivesCleanupInterruption() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val backup = File(live.parentFile, "$id.before-save")
        val earlier = File(live.parentFile, "$id.save-stage")
        assertTrue(earlier.mkdirs())
        File(earlier, "project.properties").writeText(metadata(id, "Latest"))
        File(earlier, "layers").mkdirs()
        assertTrue(live.renameTo(backup))
        assertTrue(earlier.renameTo(live))
        try {
            assertEquals("Latest", ProjectRepository.loadLocalProjects(context).first { it.id == id }.title)
            assertFalse(backup.exists())
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun invalidStagedMetadataCannotReplaceExistingProject() {
        val id = ProjectRepository.newProjectId()
        val live = seededProject(id)
        val transaction = ProjectSaveTransaction.begin(context, id)
        atomicReplace(File(transaction.workingDirectory, "project.properties"), "garbled")
        val rejected = runCatching { transaction.commit() }.isFailure
        transaction.abort()
        try {
            assertTrue(rejected)
            assertEquals("original raster", File(live, "layers/layer-one/tiles/0_0.png").readText())
            assertEquals("Original", ProjectRepository.loadLocalProjects(context).first { it.id == id }.title)
        } finally {
            ProjectRepository.deleteProject(context, id)
        }
    }

    private fun seededProject(id: String): File {
        val directory = ProjectRepository.projectDirectory(context, id)
        File(directory, "project.properties").writeText(metadata(id, "Original"))
        val tiles = File(directory, "layers/layer-one/tiles").apply { mkdirs() }
        File(tiles, "0_0.png").writeText("original raster")
        return directory
    }

    private fun metadata(id: String, title: String) =
        "id=$id\nwidth=1024\nheight=1024\ntitle=$title\n"

    private fun atomicReplace(file: File, content: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(content.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }
}
