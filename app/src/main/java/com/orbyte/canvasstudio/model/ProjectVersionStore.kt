package com.orbyte.canvasstudio.model

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.util.Properties

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

    @Synchronized
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
        // Never replace a snapshot if the clock repeats or moves backwards.
        if (versions.any { it.createdEpoch >= nowEpoch }) return false
        if (versions.firstOrNull()?.let { nowEpoch - it.createdEpoch < minIntervalMs.coerceAtLeast(0L) } == true) return false

        val root = versionRoot(context, projectId)
        val temporary = File(root, "$nowEpoch.tmp")
        val destination = File(root, nowEpoch.toString())
        if (destination.exists()) return false
        if (temporary.exists() && !temporary.deleteRecursively()) return false

        return runCatching {
            check(sourceDirectory.copyRecursively(temporary, overwrite = false))
            check(!destination.exists() && temporary.renameTo(destination))
            list(context, projectId).drop(maxVersions.coerceAtLeast(1)).forEach {
                it.directory.deleteRecursively()
            }
            true
        }.getOrElse {
            temporary.deleteRecursively()
            false
        }
    }

    @Synchronized
    fun restore(context: Context, version: LocalProjectVersion): Boolean {
        if (!isRestorableVersion(version)) return false
        // projectDirectory() would create an empty destination before recovery.
        val destination = File(ProjectRepository.projectsRoot(context), safeId(version.projectId))
        val recovery = File(destination.parentFile, "${destination.name}.restore")
        val backup = File(destination.parentFile, "${destination.name}.before-restore")

        if (backup.exists()) {
            // This may be the only remaining copy after an interrupted swap.
            if (destination.exists() || !backup.renameTo(destination)) return false
        }
        if (recovery.exists() && !recovery.deleteRecursively()) return false

        return runCatching {
            check(version.directory.copyRecursively(recovery, overwrite = false))
            check(isRestorableVersion(version.copy(directory = recovery)))
            if (destination.exists()) check(destination.renameTo(backup))
            check(recovery.renameTo(destination))
            // The restored project now exists; removing its old backup is safe.
            backup.deleteRecursively()
            true
        }.getOrElse {
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            recovery.deleteRecursively()
            false
        }
    }

    @Synchronized
    fun deleteAll(context: Context, projectId: String): Boolean =
        versionRoot(context, projectId).deleteRecursively()

    private fun isRestorableVersion(version: LocalProjectVersion): Boolean = runCatching {
        val metadata = File(version.directory, "project.properties")
        if (!metadata.isFile) return@runCatching false
        val properties = Properties().apply {
            FileInputStream(metadata).use(::load)
        }
        properties.getProperty("id") == version.projectId &&
            properties.getProperty("width")?.toIntOrNull()?.let { it > 0 } == true &&
            properties.getProperty("height")?.toIntOrNull()?.let { it > 0 } == true
    }.getOrDefault(false)

    private fun versionRoot(context: Context, projectId: String): File =
        File(context.filesDir, "$ROOT_FOLDER/${safeId(projectId)}").apply { mkdirs() }

    private fun safeId(id: String): String = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
