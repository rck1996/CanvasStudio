package com.orbyte.canvasstudio.model

import android.content.Context
import java.io.File

data class LocalProjectVersion(
    val projectId: String,
    val createdEpoch: Long,
    val directory: File,
)

object ProjectVersionStore {
    private const val ROOT_FOLDER = "canvasstudio/versions"
    private const val MIN_INTERVAL_MS = 15 * 60 * 1000L
    private const val MAX_VERSIONS = 3

    fun list(context: Context, projectId: String): List<LocalProjectVersion> =
        versionRoot(context, projectId).listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.endsWith(".tmp") }
            .mapNotNull { directory ->
                directory.name.toLongOrNull()?.let { epoch ->
                    LocalProjectVersion(projectId, epoch, directory)
                }
            }
            .sortedByDescending(LocalProjectVersion::createdEpoch)

    fun maybeSnapshot(
        context: Context,
        projectId: String,
        sourceDirectory: File,
        nowEpoch: Long = System.currentTimeMillis(),
        minIntervalMs: Long = MIN_INTERVAL_MS,
        maxVersions: Int = MAX_VERSIONS,
    ): Boolean {
        if (!sourceDirectory.isDirectory || !File(sourceDirectory, "project.properties").isFile) return false
        val versions = list(context, projectId)
        if (versions.firstOrNull()?.let { nowEpoch - it.createdEpoch < minIntervalMs } == true) return false

        val root = versionRoot(context, projectId)
        val temporary = File(root, "$nowEpoch.tmp")
        val destination = File(root, nowEpoch.toString())
        temporary.deleteRecursively()
        destination.deleteRecursively()
        return runCatching {
            check(sourceDirectory.copyRecursively(temporary, overwrite = false))
            check(temporary.renameTo(destination))
            list(context, projectId).drop(maxVersions.coerceAtLeast(1)).forEach {
                it.directory.deleteRecursively()
            }
            true
        }.getOrElse {
            temporary.deleteRecursively()
            false
        }
    }

    fun restore(context: Context, version: LocalProjectVersion): Boolean {
        if (!version.directory.isDirectory) return false
        val destination = ProjectRepository.projectDirectory(context, version.projectId)
        val recovery = File(destination.parentFile, "${destination.name}.restore")
        val backup = File(destination.parentFile, "${destination.name}.before-restore")
        recovery.deleteRecursively()
        backup.deleteRecursively()
        return runCatching {
            check(version.directory.copyRecursively(recovery, overwrite = false))
            if (destination.exists()) check(destination.renameTo(backup))
            check(recovery.renameTo(destination))
            backup.deleteRecursively()
            true
        }.getOrElse {
            recovery.deleteRecursively()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            false
        }
    }

    fun deleteAll(context: Context, projectId: String): Boolean =
        versionRoot(context, projectId).deleteRecursively()

    private fun versionRoot(context: Context, projectId: String): File =
        File(context.filesDir, "$ROOT_FOLDER/${safeId(projectId)}").apply { mkdirs() }

    private fun safeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
