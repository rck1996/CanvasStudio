package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.model.LocalProjectVersion
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.ui.PreviewBitmapRepository
import java.io.File
import java.text.DateFormat
import java.util.Date

/** View and restore any of the project's saved local versions, with a thumbnail when available. */
@Composable
internal fun ProjectVersionHistoryDialog(
    project: ProjectCard,
    versions: List<LocalProjectVersion>,
    onDismiss: () -> Unit,
    onRestore: (LocalProjectVersion) -> Unit,
) {
    var selectedVersion by remember { mutableStateOf<LocalProjectVersion?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de ${project.title}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Selecciona una versión. Se reemplazará el dibujo actual después de confirmar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (versions.isEmpty()) {
                    Text("No hay versiones guardadas.", color = StudioPalette.TextMuted)
                }
                versions.forEach { version ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        VersionThumbnail(File(version.directory, "preview.png"))
                        Column(Modifier.weight(1f)) {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(version.createdEpoch)),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(
                                modifier = Modifier.testTag("restore_version_${version.createdEpoch}"),
                                onClick = { selectedVersion = version },
                            ) { Text("Restaurar esta versión") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )

    selectedVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { selectedVersion = null },
            title = { Text("Confirmar restauración") },
            text = {
                Text("El lienzo actual será reemplazado por la versión seleccionada de ${project.title}.")
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_restore_version"),
                    onClick = {
                        selectedVersion = null
                        onRestore(version)
                        onDismiss()
                    },
                ) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { selectedVersion = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun VersionThumbnail(file: File) {
    var thumbnail by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        thumbnail = PreviewBitmapRepository.load(file, maxEdge = 512)?.asImageBitmap()
    }
    Box(
        modifier = Modifier.size(width = 100.dp, height = 75.dp)
            .background(StudioPalette.SurfaceRaised, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Vista previa de la versión guardada",
                modifier = Modifier.size(100.dp, 75.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("Sin vista previa", style = MaterialTheme.typography.labelSmall)
        }
    }
}
