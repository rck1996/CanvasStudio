package com.orbyte.canvasstudio.model

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/** A recoverable deletion. Project content is moved, not copied or silently discarded. */
data class TrashedProject(
    val id: String,
    val title: String,
    val deletedEpoch: Long,
)

object ProjectTrashStore {
    private const val ROOT_FOLDER = "canvasstudio/trash"
    private val validId = Regex("[a-zA-Z0-9_-]+")

    @Synchronized
    fun list(context: Context): List<TrashedProject> =
        trashRoot(context).listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.matches(validId) }
            .map { directory ->
                val properties = runCatching {
                    Properties().apply {
                        FileInputStream(File(directory, "project.properties")).use(::load)
                    }
                }.getOrNull()
                TrashedProject(
                    id = directory.name,
                    title = properties?.getProperty("title")
                        ?.takeIf(String::isNotBlank)
                        ?: "Proyecto ${directory.name}",
                    deletedEpoch = directory.lastModified(),
                )
            }
            .sortedByDescending(TrashedProject::deletedEpoch)

    @Synchronized
    fun moveToTrash(context: Context, projectId: String): Boolean {
        if (!projectId.matches(validId) || ProjectSaveTransaction.isActive(projectId)) return false
        ProjectSaveTransaction.recover(context, projectId)
        val original = File(ProjectRepository.projectsRoot(context), projectId)
        if (!original.isDirectory) return false
        val destination = File(trashRoot(context), projectId)
        if (destination.exists() || !original.renameTo(destination)) return false
        destination.setLastModified(System.currentTimeMillis())
        // Version snapshots deliberately remain linked to the ID so undo survives restore.
        return true
    }

    @Synchronized
    fun restore(context: Context, projectId: String): Boolean {
        if (!projectId.matches(validId) || ProjectSaveTransaction.isActive(projectId)) return false
        ProjectSaveTransaction.recover(context, projectId)
        val trashed = File(trashRoot(context), projectId)
        val destination = File(ProjectRepository.projectsRoot(context), projectId)
        if (!trashed.isDirectory || destination.exists()) return false
        return trashed.renameTo(destination)
    }

    /** This is the only irreversible user-facing deletion; also purges stored versions. */
    @Synchronized
    fun deleteForever(context: Context, projectId: String): Boolean {
        if (!projectId.matches(validId) || ProjectSaveTransaction.isActive(projectId)) return false
        val trashed = File(trashRoot(context), projectId)
        if (!trashed.isDirectory) return false
        if (!trashed.deleteRecursively()) return false
        return ProjectVersionStore.deleteAll(context, projectId)
    }

    private fun trashRoot(context: Context): File =
        File(context.filesDir, ROOT_FOLDER).apply { mkdirs() }
}
