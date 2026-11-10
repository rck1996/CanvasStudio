package com.orbyte.canvasstudio.model

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.Properties
import java.util.UUID

/**
 * A project-wide save transaction. The editable directory is a private sibling of the
 * published project, not the published project itself. Unchanged files are hard-linked
 * to avoid duplicating large sparse-raster tile collections; all changed files are
 * replaced atomically (never opened in-place) by the drawing persistence layer.
 *
 * Publication uses two same-filesystem directory renames:
 *   published -> before-save; staged -> published.
 * On the next launch an interrupted swap is recovered before a project is opened.
 *
 * The guarantee is crash-consistent file-set publication (process kill), not a promise
 * against physical media failure or concurrent edits from another process.
 */
internal class ProjectSaveTransaction private constructor(
    private val projectId: String,
    private val published: File,
    private val staging: File,
    private val previous: File,
) {
    val workingDirectory: File get() = staging
    private var committed = false

    fun commit() {
        check(!committed) { "La transacción ya fue publicada" }
        check(validMetadata(staging, projectId)) { "Metadatos de guardado incompletos" }
        try {
            check(!previous.exists()) { "Existe una copia de recuperación pendiente" }
            val hadOriginal = published.exists()
            if (hadOriginal) check(published.renameTo(previous)) {
                "No se pudo respaldar el proyecto antes de guardarlo"
            }
            if (!staging.renameTo(published)) {
                if (hadOriginal && !published.exists()) previous.renameTo(published)
                error("No se pudo publicar el guardado del proyecto")
            }
            committed = true
            // A leftover backup is harmless if process termination interrupts cleanup.
            if (previous.exists()) previous.deleteRecursively()
        } finally {
            if (committed) release(projectId)
        }
    }

    /** Leave the published project untouched when a staged save fails. */
    fun abort() {
        if (committed) return
        try {
            if (!published.exists() && previous.exists()) previous.renameTo(published)
            staging.deleteRecursively()
        } finally {
            release(projectId)
        }
    }

    companion object {
        private val lock = Any()
        private val active = mutableSetOf<String>()
        private const val STAGING = ".save-stage"
        private const val BACKUP = ".before-save"

        fun isActive(projectId: String): Boolean =
            synchronized(lock) { projectId in active }

        /** Serialize gallery mutations with the start of an asynchronous project save. */
        fun runWhenIdle(projectId: String, block: () -> Boolean): Boolean =
            synchronized(lock) {
                projectId !in active && block()
            }

        fun begin(context: Context, projectId: String): ProjectSaveTransaction {
            require(projectId.matches(Regex("[a-zA-Z0-9_-]+"))) { "ID de proyecto no válido" }
            val root = ProjectRepository.projectsRoot(context)
            val live = File(root, projectId)
            val stage = File(root, "$projectId$STAGING")
            val backup = File(root, "$projectId$BACKUP")
            synchronized(lock) {
                check(projectId !in active) { "El proyecto ya se está guardando" }
                check(!File(context.filesDir, "canvasstudio/trash/$projectId").exists()) {
                    "No se puede guardar un proyecto enviado a la papelera"
                }
                recoverFiles(projectId, live, stage, backup)
                check(active.add(projectId))
            }
            return try {
                cloneDirectory(live, stage)
                ProjectSaveTransaction(projectId, live, stage, backup)
            } catch (error: Throwable) {
                stage.deleteRecursively()
                release(projectId)
                throw error
            }
        }

        /** Called before reading project metadata or populating the gallery. */
        fun recover(context: Context, projectId: String) {
            val root = ProjectRepository.projectsRoot(context)
            synchronized(lock) {
                if (projectId !in active) recoverFiles(
                    projectId,
                    File(root, projectId),
                    File(root, "$projectId$STAGING"),
                    File(root, "$projectId$BACKUP"),
                )
            }
        }

        fun recoverPending(context: Context) {
            val root = ProjectRepository.projectsRoot(context)
            val pending = root.listFiles().orEmpty()
                .filter(File::isDirectory)
                .mapNotNull { directory ->
                    when {
                        directory.name.endsWith(STAGING) ->
                            directory.name.removeSuffix(STAGING)
                        directory.name.endsWith(BACKUP) ->
                            directory.name.removeSuffix(BACKUP)
                        else -> null
                    }
                }
                .filter { it.matches(Regex("[a-zA-Z0-9_-]+")) }
                .distinct()
            pending.forEach { recover(context, it) }
        }

        private fun recoverFiles(id: String, live: File, stage: File, backup: File) {
            if (backup.exists()) {
                when {
                    !live.exists() -> check(backup.renameTo(live)) {
                        "No se pudo recuperar el proyecto $id"
                    }
                    validMetadata(live, id) -> check(backup.deleteRecursively()) {
                        "No se pudo limpiar el respaldo anterior"
                    }
                    validMetadata(backup, id) -> {
                        // Preserve a corrupt published directory for manual diagnostics.
                        val rejected = File(live.parentFile, "$id.failed-save-${UUID.randomUUID()}")
                        check(live.renameTo(rejected))
                        check(backup.renameTo(live)) {
                            "No se pudo restaurar la copia válida de $id"
                        }
                    }
                    else -> error("El proyecto $id y su respaldo son inválidos; no se borrarán")
                }
            }
            if (stage.exists()) check(stage.deleteRecursively()) {
                "No se pudo limpiar el guardado interrumpido de $id"
            }
        }

        private fun validMetadata(directory: File, id: String): Boolean = runCatching {
            if (!directory.isDirectory) return@runCatching false
            val metadata = File(directory, "project.properties")
            if (!metadata.isFile) return@runCatching false
            val props = Properties().apply {
                FileInputStream(metadata).use(::load)
            }
            props.getProperty("id") == id &&
                props.getProperty("width")?.toIntOrNull()?.let { it > 0 } == true &&
                props.getProperty("height")?.toIntOrNull()?.let { it > 0 } == true
        }.getOrDefault(false)

        private fun cloneDirectory(source: File, destination: File) {
            check(!destination.exists()) { "La carpeta de guardado ya existe" }
            check(destination.mkdirs()) { "No se pudo crear el guardado temporal" }
            if (!source.exists()) return
            check(source.isDirectory) { "El proyecto original no es una carpeta" }
            source.listFiles()?.forEach { child ->
                val copy = File(destination, child.name)
                when {
                    Files.isSymbolicLink(child.toPath()) -> error("Enlace simbólico no permitido")
                    child.isDirectory -> cloneDirectory(child, copy)
                    child.isFile -> {
                        // File-level atomic replacement detaches modified files from their links.
                        // Fall back to copying if this device/filesystem forbids hard links.
                        try {
                            Files.createLink(copy.toPath(), child.toPath())
                        } catch (_: Exception) {
                            child.copyTo(copy, overwrite = false)
                        }
                    }
                    else -> error("Entrada de proyecto no compatible")
                }
            } ?: error("No se pudo leer el directorio de proyecto")
        }

        private fun release(projectId: String) {
            synchronized(lock) { active.remove(projectId) }
        }
    }
}
