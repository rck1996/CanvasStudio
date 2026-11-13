package com.orbyte.canvasstudio.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.export.DocumentExportCoordinator
import com.orbyte.canvasstudio.export.ExportDestination
import com.orbyte.canvasstudio.export.ExportFormat
import com.orbyte.canvasstudio.export.ExportOutcome
import com.orbyte.canvasstudio.export.ExportWriter
import com.orbyte.canvasstudio.export.exportFileName
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class EditorExportActions(
    val png: (String) -> Unit,
    val openRaster: (String) -> Unit,
    val psd: (String) -> Unit,
)

/** UI adapter for the system picker; export policy and encoding stay outside Compose. */
@Composable
internal fun rememberEditorExportActions(view: DrawingView?): EditorExportActions {
    val currentView by rememberUpdatedState(view)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun export(uri: Uri, format: ExportFormat) {
        val destination = object : ExportDestination {
            override fun open(): OutputStream = context.contentResolver.openOutputStream(uri)
                ?: error("No se pudo abrir el archivo de destino.")
            override fun deleteIncomplete(): Boolean = DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
        // The view and its metadata belong to the main thread.
        val snapshot = try {
            requireNotNull(currentView) { "El editor ya no está disponible." }.captureExportSnapshot()
        } catch (error: Exception) {
            scope.launch {
                val failure = withContext(Dispatchers.IO) { DocumentExportCoordinator.captureFailed(error, destination) }
                val suffix = if (failure.incompleteFileMayRemain) " Puede quedar un archivo vacío." else ""
                Toast.makeText(context, (error.message ?: "No se pudo preparar la exportación.") + suffix, Toast.LENGTH_LONG).show()
            }
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DocumentExportCoordinator.execute(format, ExportWriter { selected, open ->
                    when (selected) {
                        ExportFormat.PNG -> snapshot.writePngTo(open, includePaper = true)
                        ExportFormat.OPEN_RASTER -> snapshot.writeOpenRasterTo(open)
                        ExportFormat.PSD -> snapshot.writeLayeredPsdTo(open)
                    }
                }, destination)
            }
            val message = when (result) {
                ExportOutcome.Success -> format.successMessage
                is ExportOutcome.Failure -> (result.cause.message ?: "No se pudo exportar el archivo.") +
                    if (result.incompleteFileMayRemain) " Puede quedar un archivo incompleto." else ""
            }
            Toast.makeText(context, message, if (result is ExportOutcome.Success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        }
    }

    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PNG.mimeType)) { uri ->
        if (uri != null) export(uri, ExportFormat.PNG)
    }
    val ora = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.OPEN_RASTER.mimeType)) { uri ->
        if (uri != null) export(uri, ExportFormat.OPEN_RASTER)
    }
    val psd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PSD.mimeType)) { uri ->
        if (uri != null) export(uri, ExportFormat.PSD)
    }
    return EditorExportActions(
        png = { png.launch(exportFileName(it, ExportFormat.PNG)) },
        openRaster = { ora.launch(exportFileName(it, ExportFormat.OPEN_RASTER)) },
        psd = { psd.launch(exportFileName(it, ExportFormat.PSD)) },
    )
}
