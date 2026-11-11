package com.orbyte.canvasstudio.model

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

object ProjectRepository {
    private const val ROOT_FOLDER = "canvasstudio/projects"

    fun projectsRoot(context: Context): File = File(context.filesDir, ROOT_FOLDER).apply { mkdirs() }

    /** UUIDs prevent accidental collisions between rapid project creations. */
    fun newProjectId(): String = "project-${UUID.randomUUID()}"

    fun projectDirectory(context: Context, projectId: String): File {
        val id = safeId(projectId)
        ProjectSaveTransaction.recover(context, id)
        return File(projectsRoot(context), id).apply { mkdirs() }
    }

    fun previewPath(context: Context, projectId: String): String =
        File(projectDirectory(context, projectId), "preview.png").absolutePath

    fun metadataFile(context: Context, projectId: String): File =
        recoverMetadata(projectDirectory(context, projectId))

    fun loadLocalProjects(context: Context): List<ProjectCard> {
        ProjectSaveTransaction.recoverPending(context)
        val root = projectsRoot(context)
        return root.listFiles()
            .orEmpty()
            .filter { directory ->
                directory.isDirectory &&
                    !directory.name.endsWith(".partial") &&
                    !directory.name.endsWith(".restore") &&
                    !directory.name.endsWith(".before-restore") &&
                    !directory.name.endsWith(".save-stage") &&
                    !directory.name.endsWith(".before-save") &&
                    !directory.name.contains(".failed-save-") &&
                    recoverMetadata(directory).isFile
            }
            .mapNotNull(::readProjectCard)
            .sortedByDescending(ProjectCard::modifiedEpoch)
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        val id = safeId(projectId)
        if (ProjectSaveTransaction.isActive(id)) return false
        ProjectSaveTransaction.recover(context, id)
        val directory = File(projectsRoot(context), id)
        val deleted = !directory.exists() || directory.deleteRecursively()
        if (deleted) ProjectVersionStore.deleteAll(context, projectId)
        return deleted
    }

    fun duplicateProject(context: Context, sourceId: String, newTitle: String? = null): ProjectCard? {
        val safeSourceId = safeId(sourceId)
        if (ProjectSaveTransaction.isActive(safeSourceId)) return null
        ProjectSaveTransaction.recover(context, safeSourceId)
        val source = File(projectsRoot(context), safeSourceId)
        if (!source.isDirectory || !isUsableMetadata(recoverMetadata(source))) return null

        val destinationId = newProjectId()
        val root = projectsRoot(context)
        val staging = File(root, "$destinationId.partial")
        val destination = File(root, destinationId)
        if (staging.exists() || destination.exists()) return null

        // Only publish a copy after all files and its new metadata are ready.
        return runCatching {
            check(source.copyRecursively(staging, overwrite = false))
            val metadata = recoverMetadata(staging)
            val properties = Properties().apply {
                FileInputStream(metadata).use(::load)
                setProperty("id", destinationId)
                setProperty("title", newTitle ?: "${getProperty("title", "Proyecto")} copia")
                setProperty("modifiedEpoch", System.currentTimeMillis().toString())
            }
            val atomic = AtomicFile(File(staging, "project.properties"))
            var output: FileOutputStream? = null
            try {
                output = atomic.startWrite()
                properties.store(output, "Canvas Studio project")
                atomic.finishWrite(output)
            } catch (error: Exception) {
                output?.let(atomic::failWrite)
                throw error
            }
            // Do not carry the source project's old recovery metadata into its copy.
            File(staging, "project.properties.tmp").delete()
            File(staging, "project.properties.bak").delete()
            check(!destination.exists() && staging.renameTo(destination))
            checkNotNull(readProjectCard(destination))
        }.getOrElse {
            staging.deleteRecursively()
            destination.deleteRecursively()
            null
        }
    }

    private fun readProjectCard(directory: File): ProjectCard? = runCatching {
        val properties = Properties().apply {
            FileInputStream(recoverMetadata(directory)).use { input -> load(input) }
        }
        val id = properties.getProperty("id") ?: directory.name
        if (id != directory.name) return@runCatching null
        ProjectCard(
            id = id,
            title = properties.getProperty("title", "Proyecto sin título"),
            width = properties.getProperty("width", "2048").toInt(),
            height = properties.getProperty("height", "1536").toInt(),
            dpi = properties.getProperty("dpi", "300").toInt(),
            modifiedLabel = "Proyecto local",
            preview = PreviewStyle.SKETCH,
            isLocal = true,
            localPreviewPath = File(directory, "preview.png").takeIf(File::isFile)?.absolutePath,
            modifiedEpoch = properties.getProperty("modifiedEpoch", "0").toLong(),
        )
    }.getOrNull()

    private fun recoverMetadata(directory: File): File {
        val primary = File(directory, "project.properties")
        if (isUsableMetadata(primary)) return primary
        val temporary = File(directory, "project.properties.tmp")
        val backup = File(directory, "project.properties.bak")
        val recoverySource = when {
            isUsableMetadata(temporary) -> temporary
            isUsableMetadata(backup) -> backup
            else -> return primary
        }
        val recoveryCopy = File(directory, "project.properties.recover")
        return runCatching {
            recoverySource.copyTo(recoveryCopy, overwrite = true)
            if (primary.exists()) check(primary.delete())
            check(recoveryCopy.renameTo(primary))
            if (recoverySource == temporary) temporary.delete()
            primary
        }.getOrElse {
            recoveryCopy.delete()
            recoverySource
        }
    }

    private fun isUsableMetadata(file: File): Boolean = runCatching {
        if (!file.isFile) return@runCatching false
        val properties = Properties().apply {
            FileInputStream(file).use { input -> load(input) }
        }
        properties.getProperty("id")?.isNotBlank() == true &&
            properties.getProperty("width")?.toIntOrNull()?.let { it > 0 } == true &&
            properties.getProperty("height")?.toIntOrNull()?.let { it > 0 } == true
    }.getOrDefault(false)

    private fun safeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
