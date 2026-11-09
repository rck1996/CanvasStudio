package com.orbyte.canvasstudio.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

@RunWith(AndroidJUnit4::class)
class ProjectRepositoryRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun restoresValidBackupWhenPrimaryMetadataIsCorrupt() {
        val projectId = "recovery-backup-test"
        val directory = cleanProject(projectId)
        File(directory, "project.properties").writeBytes(byteArrayOf(0, 1, 2, 3))
        writeMetadata(File(directory, "project.properties.bak"), projectId, "Recovered backup")

        val recovered = ProjectRepository.metadataFile(context, projectId)

        assertEquals("project.properties", recovered.name)
        assertTrue(recovered.isFile)
        assertEquals("Recovered backup", readMetadata(recovered).getProperty("title"))
        ProjectRepository.deleteProject(context, projectId)
    }

    @Test
    fun completesValidTemporaryMetadataAfterInterruptedSave() {
        val projectId = "recovery-temporary-test"
        val directory = cleanProject(projectId)
        writeMetadata(File(directory, "project.properties.tmp"), projectId, "Recovered temporary")

        val recovered = ProjectRepository.metadataFile(context, projectId)

        assertEquals("project.properties", recovered.name)
        assertEquals("Recovered temporary", readMetadata(recovered).getProperty("title"))
        assertTrue(!File(directory, "project.properties.tmp").exists())
        ProjectRepository.deleteProject(context, projectId)
    }

    @Test
    fun duplicateProjectsReceiveUniqueIdsAndPreserveOriginal() {
        val id = "duplicate-source-test"
        val source = cleanProject(id)
        writeMetadata(File(source, "project.properties"), id, "Original")
        File(source, "artwork.txt").writeText("original artwork")

        val first = requireNotNull(ProjectRepository.duplicateProject(context, id))
        val second = requireNotNull(ProjectRepository.duplicateProject(context, id))
        try {
            assertTrue(first.id != second.id && first.id != id && second.id != id)
            assertEquals(first.id, ProjectRepository.projectDirectory(context, first.id).name)
            assertEquals("Original", readMetadata(File(source, "project.properties")).getProperty("title"))
            assertEquals("original artwork", File(ProjectRepository.projectDirectory(context, first.id), "artwork.txt").readText())
            assertEquals("original artwork", File(ProjectRepository.projectDirectory(context, second.id), "artwork.txt").readText())
        } finally {
            ProjectRepository.deleteProject(context, first.id)
            ProjectRepository.deleteProject(context, second.id)
            ProjectRepository.deleteProject(context, id)
        }
    }

    @Test
    fun incompleteCopiesAndRestoreBackupsStayHidden() {
        val id = "partial-visible-test"
        val root = ProjectRepository.projectsRoot(context)
        val partial = File(root, "$id.partial").apply { mkdirs() }
        val backup = File(root, "$id.before-restore").apply { mkdirs() }
        writeMetadata(File(partial, "project.properties"), "$id.partial", "Partial")
        writeMetadata(File(backup, "project.properties"), "$id.before-restore", "Backup")
        try {
            assertTrue(ProjectRepository.loadLocalProjects(context).none {
                it.id == "$id.partial" || it.id == "$id.before-restore"
            })
        } finally {
            partial.deleteRecursively()
            backup.deleteRecursively()
        }
    }

    private fun cleanProject(projectId: String): File {
        ProjectRepository.deleteProject(context, projectId)
        return ProjectRepository.projectDirectory(context, projectId)
    }

    private fun writeMetadata(file: File, id: String, title: String) {
        val properties = Properties().apply {
            setProperty("id", id)
            setProperty("title", title)
            setProperty("width", "2048")
            setProperty("height", "1536")
            setProperty("dpi", "300")
        }
        FileOutputStream(file).use { output -> properties.store(output, "recovery test") }
    }

    private fun readMetadata(file: File): Properties = Properties().apply {
        file.inputStream().use { input -> load(input) }
    }
}
