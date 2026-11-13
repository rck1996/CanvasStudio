package com.orbyte.canvasstudio.export

import java.io.OutputStream
import java.util.concurrent.CancellationException

/** Format and destination policy independent of Android UI and raster implementations. */
internal enum class ExportFormat(val extension: String, val mimeType: String, val successMessage: String) {
    PNG("png", "image/png", "PNG exportado"),
    OPEN_RASTER("ora", "image/openraster", "OpenRaster exportado"),
    PSD("psd", "image/vnd.adobe.photoshop", "PSD por capas exportado"),
}

internal fun interface ExportWriter {
    fun write(format: ExportFormat, openDestination: () -> OutputStream)
}

internal interface ExportDestination {
    fun open(): OutputStream
    /** Returns false if the provider could not remove an incomplete new document. */
    fun deleteIncomplete(): Boolean
}

internal sealed class ExportOutcome {
    data object Success : ExportOutcome()
    data class Failure(val cause: Exception, val incompleteFileMayRemain: Boolean) : ExportOutcome()
}

/** Caller supplies an immutable snapshot; invoke on an IO worker. */
internal object DocumentExportCoordinator {
    fun execute(format: ExportFormat, writer: ExportWriter, destination: ExportDestination): ExportOutcome =
        try {
            writer.write(format, destination::open)
            ExportOutcome.Success
        } catch (error: Exception) {
            val removed = runCatching { destination.deleteIncomplete() }.getOrDefault(false)
            if (error is CancellationException) throw error
            ExportOutcome.Failure(error, !removed)
        }

    fun captureFailed(error: Exception, destination: ExportDestination): ExportOutcome.Failure =
        ExportOutcome.Failure(error, !runCatching { destination.deleteIncomplete() }.getOrDefault(false))
}

internal fun exportFileName(title: String, format: ExportFormat): String {
    val safe = title.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"), "").ifBlank { "Canvas Studio" }
    return "$safe.${format.extension}"
}
