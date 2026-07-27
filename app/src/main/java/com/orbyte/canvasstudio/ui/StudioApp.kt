package com.orbyte.canvasstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.R
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.model.ProjectRepository
import com.orbyte.canvasstudio.model.ProjectVersionStore
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.model.defaultProjects
import com.orbyte.canvasstudio.ui.screens.EditorScreen
import com.orbyte.canvasstudio.ui.screens.GalleryScreen
import com.orbyte.canvasstudio.ui.screens.NewCanvasDialog

private enum class Destination { GALLERY, EDITOR }

@Composable
fun StudioApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("canvas_studio_preferences", android.content.Context.MODE_PRIVATE)
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(!preferences.getBoolean("onboarding_complete", false))
    }
    var destination by remember { mutableStateOf(Destination.GALLERY) }
    var activeDocument by remember {
        mutableStateOf(
            EditorDocument(
                id = "untitled",
                title = "Sin título",
                width = 4096,
                height = 2732,
                dpi = 300,
            ),
        )
    }
    var projects by remember {
        mutableStateOf(ProjectRepository.loadLocalProjects(context) + defaultProjects)
    }
    var showNewCanvas by rememberSaveable { mutableStateOf(false) }

    fun addOrRefreshLocalCard(document: EditorDocument) {
        val card = ProjectCard(
            id = document.id,
            title = document.title,
            width = document.width,
            height = document.height,
            dpi = document.dpi,
            modifiedLabel = "Guardado localmente",
            preview = document.preview ?: PreviewStyle.SKETCH,
            isLocal = true,
            localPreviewPath = ProjectRepository.previewPath(context, document.id),
            modifiedEpoch = System.currentTimeMillis(),
        )
        projects = listOf(card) + projects.filterNot { it.id == card.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioPalette.Background)
            .padding(WindowInsets.safeDrawing.asPaddingValues()),
    ) {
        when (destination) {
            Destination.GALLERY -> GalleryScreen(
                projects = projects,
                onNewCanvas = { showNewCanvas = true },
                onOpenProject = { project ->
                    if (project.isLocal) {
                        activeDocument = EditorDocument(
                            id = project.id,
                            title = project.title,
                            width = project.width,
                            height = project.height,
                            dpi = project.dpi,
                            preview = null,
                            isLocal = true,
                        )
                    } else {
                        val localId = "${project.id}-${System.currentTimeMillis()}"
                        activeDocument = EditorDocument(
                            id = localId,
                            title = project.title,
                            width = project.width,
                            height = project.height,
                            dpi = project.dpi,
                            preview = project.preview,
                            isLocal = true,
                        )
                        addOrRefreshLocalCard(activeDocument)
                    }
                    destination = Destination.EDITOR
                },
                onDuplicateProject = { project ->
                    if (project.isLocal) {
                        ProjectRepository.duplicateProject(context, project.id)?.let { duplicated ->
                            projects = listOf(duplicated) + projects.filterNot { it.id == duplicated.id }
                        }
                    } else {
                        activeDocument = EditorDocument(
                            id = "project-${System.currentTimeMillis()}",
                            title = "${project.title} copia",
                            width = project.width,
                            height = project.height,
                            dpi = project.dpi,
                            preview = project.preview,
                            isLocal = true,
                        )
                        addOrRefreshLocalCard(activeDocument)
                        destination = Destination.EDITOR
                    }
                },
                onDeleteProject = { project ->
                    if (project.isLocal && ProjectRepository.deleteProject(context, project.id)) {
                        projects = projects.filterNot { it.id == project.id }
                    }
                },
                onRestoreLatestVersion = { project ->
                    val latest = ProjectVersionStore.list(context, project.id).firstOrNull()
                    if (latest != null && ProjectVersionStore.restore(context, latest)) {
                        projects = ProjectRepository.loadLocalProjects(context) + defaultProjects
                    }
                },
            )

            Destination.EDITOR -> EditorScreen(
                document = activeDocument,
                onBackToGallery = {
                    addOrRefreshLocalCard(activeDocument)
                    destination = Destination.GALLERY
                },
            )
        }
    }

    if (showNewCanvas) {
        NewCanvasDialog(
            onDismiss = { showNewCanvas = false },
            onCreate = { draft ->
                activeDocument = draft.copy(
                    id = "project-${System.currentTimeMillis()}",
                    isLocal = true,
                    preview = null,
                )
                addOrRefreshLocalCard(activeDocument)
                showNewCanvas = false
                destination = Destination.EDITOR
            },
        )
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bienvenido a Canvas Studio") },
            text = {
                Column {
                    Image(
                        painter = painterResource(R.drawable.canvas_studio_logo),
                        contentDescription = "Logo de Canvas Studio",
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Diseñado para tablets y lápices sensibles a presión.")
                    Spacer(Modifier.height(8.dp))
                    Text("• Dibuja con S Pen o stylus; presión e inclinación se aplican automáticamente.")
                    Text("• Usa dos dedos para mover, ampliar o girar el lienzo.")
                    Text("• El autoguardado mantiene tiles y metadata; las versiones locales protegen cambios recientes.")
                    Text("• Abre Más opciones en el editor para guías, simetría y ayuda.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferences.edit().putBoolean("onboarding_complete", true).apply()
                        showOnboarding = false
                    },
                ) {
                    Text("Empezar")
                }
            },
        )
    }
}
