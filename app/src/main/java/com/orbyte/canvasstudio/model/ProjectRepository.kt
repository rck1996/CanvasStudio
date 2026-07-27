package com.orbyte.canvasstudio.model

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object ProjectRepository {
    private const val ROOT_FOLDER = "canvasstudio/projects"

    fun projectsRoot(context: Context): File = File(context.filesDir, ROOT_FOLDER).apply { mkdirs() }

    fun projectDirectory(context: Context, projectId: String): File =
        File(projectsRoot(context), safeId(projectId)).apply { mkdirs() }

    fun previewPath(context: Context, projectId: String): String =
        File(projectDirectory(context, projectId), "preview.png").absolutePath

    fun metadataFile(context: Context, projectId: String): File =
        recoverMetadata(projectDirectory(context, projectId))

    fun loadLocalProjects(context: Context): List<ProjectCard> {
        val root = projectsRoot(context)
        return root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && recoverMetadata(it).isFile }
            .mapNotNull(::readProjectCard)
            .sortedByDescending(ProjectCard::modifiedEpoch)
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        val directory = File(projectsRoot(context), safeId(projectId))
        val deleted = !directory.exists() || directory.deleteRecursively()
        if (deleted) ProjectVersionStore.deleteAll(context, projectId)
        return deleted
    }

    fun duplicateProject(context: Context, sourceId: String, newTitle: String? = null): ProjectCard? {
        val source = File(projectsRoot(context), safeId(sourceId))
        if (!source.isDirectory) return null

        val destinationId = "project-${System.currentTimeMillis()}"
        val destination = File(projectsRoot(context), safeId(destinationId))
        if (!source.copyRecursively(destination, overwrite = false)) {
            destination.deleteRecursively()
            return null
        }

        return runCatching {
            val metadataFile = recoverMetadata(destination)
            val properties = Properties().apply {
                FileInputStream(metadataFile).use { input -> load(input) }
                setProperty("id", destinationId)
                setProperty(
                    "title",
                    newTitle ?: "${getProperty("title", "Proyecto")} copia",
                )
                setProperty("modifiedEpoch", System.currentTimeMillis().toString())
            }
            val temporary = File(destination, "project.properties.tmp")
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Canvas Studio project")
                output.fd.sync()
            }
            val backup = File(destination, "project.properties.bak")
            if (metadataFile.exists()) {
                metadataFile.copyTo(backup, overwrite = true)
                check(metadataFile.delete()) { "No se pudo reemplazar la metadata del duplicado" }
            }
            if (!temporary.renameTo(metadataFile)) {
                if (backup.isFile) backup.copyTo(metadataFile, overwrite = true)
                error("No se pudo completar el duplicado del proyecto")
            }
            backup.delete()
            checkNotNull(readProjectCard(destination)) { "No se pudo leer el proyecto duplicado" }
        }.getOrElse {
            destination.deleteRecursively()
            null
        }
    }

    private fun readProjectCard(directory: File): ProjectCard? = runCatching {
        val properties = Properties().apply {
            FileInputStream(recoverMetadata(directory)).use { input -> load(input) }
        }
        val id = properties.getProperty("id") ?: directory.name
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
