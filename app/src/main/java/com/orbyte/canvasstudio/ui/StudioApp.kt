package com.orbyte.canvasstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.model.ProjectRepository
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.model.defaultProjects
import com.orbyte.canvasstudio.ui.screens.EditorScreen
import com.orbyte.canvasstudio.ui.screens.GalleryScreen
import com.orbyte.canvasstudio.ui.screens.NewCanvasDialog

private enum class Destination { GALLERY, EDITOR }

@Composable
fun StudioApp() {
    val context = LocalContext.current
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
}
