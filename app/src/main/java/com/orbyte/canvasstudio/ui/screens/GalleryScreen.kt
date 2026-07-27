package com.orbyte.canvasstudio.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbyte.canvasstudio.model.CanvasPreset
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.model.canvasPresets
import com.orbyte.canvasstudio.model.constrainCanvasSize
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

@Composable
fun GalleryScreen(
    projects: List<ProjectCard>,
    onNewCanvas: () -> Unit,
    onOpenProject: (ProjectCard) -> Unit,
    onDuplicateProject: (ProjectCard) -> Unit,
    onDeleteProject: (ProjectCard) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val filteredProjects = remember(projects, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) projects else projects.filter { project ->
            project.title.contains(query, ignoreCase = true) ||
                "${project.width}x${project.height}".contains(query.replace(" ", ""), ignoreCase = true)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StudioPalette.Background)) {
        val compactLayout = maxWidth < 900.dp
        Row(Modifier.fillMaxSize()) {
        GallerySidebar(onNewCanvas = onNewCanvas, compact = compactLayout, onSettings = { showSettings = true })
        Column(Modifier.weight(1f).fillMaxHeight()) {
            GalleryTopBar(onSettings = { showSettings = true })
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
                if (compactLayout) {
                    Column {
                        Text("Mis proyectos", style = MaterialTheme.typography.headlineMedium, color = StudioPalette.Text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tu estudio local de ilustración y pintura digital",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioPalette.TextMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(60) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = StudioPalette.TextMuted) },
                            placeholder = { Text("Buscar proyectos") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("Mis proyectos", style = MaterialTheme.typography.displaySmall, color = StudioPalette.Text)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tu estudio local de ilustración y pintura digital",
                                style = MaterialTheme.typography.bodyLarge,
                                color = StudioPalette.TextMuted,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(60) },
                            modifier = Modifier.width(260.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = StudioPalette.TextMuted) },
                            placeholder = { Text("Buscar proyectos") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item {
                        NewProjectCard(onClick = onNewCanvas)
                    }
                    items(filteredProjects, key = { it.id }) { project ->
                        GalleryProjectCard(
                            project = project,
                            onClick = { onOpenProject(project) },
                            onDuplicate = { onDuplicateProject(project) },
                            onDelete = { onDeleteProject(project) },
                        )
                    }
                }
            }
        }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Canvas Studio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alpha Premium 1.1", fontWeight = FontWeight.SemiBold)
                    Text("Guardado local, sin cuenta y sin servicios en segundo plano.")
                    Text("Atajos: B pincel · E borrador · H mover · Ctrl+Z deshacer.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
private fun GallerySidebar(
    onNewCanvas: () -> Unit,
    compact: Boolean,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (compact) 188.dp else 224.dp)
            .fillMaxHeight()
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(StudioPalette.Accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Brush, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("Canvas", color = StudioPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("STUDIO", color = StudioPalette.Accent, fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNewCanvas,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = StudioPalette.Accent),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 13.dp),
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nuevo lienzo")
        }
        Spacer(Modifier.height(18.dp))
        GalleryNavItem(
            icon = Icons.Outlined.Collections,
            label = "Mis proyectos",
            selected = true,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = StudioPalette.Border)
        Spacer(Modifier.weight(1f))
        GalleryNavItem(
            icon = Icons.Outlined.Settings,
            label = "Ajustes",
            onClick = onSettings,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = StudioPalette.SurfaceRaised,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Todo se guarda localmente", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(3.dp))
                Text("Sin cuenta · sin nube", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GalleryNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) StudioPalette.AccentSoft else Color.Transparent, RoundedCornerShape(10.dp))
            .then(interactionModifier)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else StudioPalette.TextMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (selected) Color.White else StudioPalette.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GalleryTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Galería", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Surface(
            color = Color(0xFF153326),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF285A45)),
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(StudioPalette.Success, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("Guardado local activo", color = Color(0xFFA9E8CD), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(10.dp))
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ajustes", tint = StudioPalette.TextMuted) }
    }
}

@Composable
private fun NewProjectCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.18f)
            .clickable(onClick = onClick),
        color = StudioPalette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(54.dp).background(StudioPalette.AccentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, null, tint = StudioPalette.Accent, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Crear nuevo lienzo", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Elige tamaño, DPI y formato", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GalleryProjectCard(
    project: ProjectCard,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = StudioPalette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Column {
            ProjectArtworkPreview(project, Modifier.fillMaxWidth().aspectRatio(1.54f))
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project.title,
                        color = StudioPalette.Text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${project.width} × ${project.height}px  ·  ${project.modifiedLabel}",
                        color = StudioPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, "Opciones del proyecto", tint = StudioPalette.TextMuted)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Abrir") },
                            onClick = { menuExpanded = false; onClick() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicar") },
                            onClick = { menuExpanded = false; onDuplicate() },
                        )
                        if (project.isLocal) {
                            DropdownMenuItem(
                                text = { Text("Eliminar del dispositivo") },
                                onClick = { menuExpanded = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar proyecto") },
            text = { Text("Se eliminará ${project.title} y sus capas guardadas en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}


@Composable
private fun ProjectArtworkPreview(project: ProjectCard, modifier: Modifier = Modifier) {
    val previewPath = project.localPreviewPath
    var previewBitmap by remember(previewPath, project.modifiedEpoch) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(previewPath, project.modifiedEpoch) {
        if (previewPath == null) return@LaunchedEffect
        repeat(6) {
            val file = File(previewPath)
            if (file.isFile) {
                previewBitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                if (previewBitmap != null) return@LaunchedEffect
            }
            delay(250)
        }
    }

    val bitmap = previewBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = project.title,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(StudioPalette.SurfaceRaised),
        )
    } else {
        ProjectPreview(project.preview, modifier)
    }
}

@Composable
private fun ProjectPreview(style: PreviewStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.background(StudioPalette.SurfaceRaised)) {
        val w = size.width
        val h = size.height
        when (style) {
            PreviewStyle.MOUNTAIN -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF79A7D9), Color(0xFFE9C58F), Color(0xFF395043))))
                val back = Path().apply {
                    moveTo(0f, h * .78f); lineTo(w * .25f, h * .38f); lineTo(w * .42f, h * .7f)
                    lineTo(w * .62f, h * .2f); lineTo(w, h * .77f); lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(back, Color(0xFFDDE3E5))
                val front = Path().apply {
                    moveTo(0f, h); lineTo(w * .32f, h * .55f); lineTo(w * .5f, h * .86f)
                    lineTo(w * .65f, h * .42f); lineTo(w, h); close()
                }
                drawPath(front, Color(0xFF283B3C))
                drawCircle(Color(0xFFFFE3A1), radius = h * .13f, center = Offset(w * .82f, h * .22f))
            }
            PreviewStyle.PORTRAIT -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF6D7B72), Color(0xFFB8A38A))))
                drawOval(Color(0xFFE5C1A9), topLeft = Offset(w * .32f, h * .16f), size = Size(w * .36f, h * .64f))
                drawArc(Color(0xFF49524D), 188f, 168f, false, topLeft = Offset(w * .28f, h * .08f), size = Size(w * .44f, h * .6f), style = Stroke(h * .12f))
                drawCircle(Color(0xFF2B3432), h * .025f, Offset(w * .45f, h * .45f))
                drawCircle(Color(0xFF2B3432), h * .025f, Offset(w * .57f, h * .45f))
            }
            PreviewStyle.CITY -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2D3F6E), Color(0xFFEF9B83))))
                repeat(9) { index ->
                    val bw = w / 10f
                    val left = index * bw + bw * .15f
                    val top = h * (.36f + (index % 4) * .08f)
                    drawRect(Color(0xFF182237), topLeft = Offset(left, top), size = Size(bw * .72f, h - top))
                    drawRect(Color(0xFFFFD279), topLeft = Offset(left + bw * .15f, top + h * .12f), size = Size(bw * .1f, h * .04f))
                }
            }
            PreviewStyle.FOREST -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF192D4E), Color(0xFF613D70), Color(0xFF162A26))))
                repeat(12) { index ->
                    val x = w * (index / 11f)
                    val treeH = h * (.35f + (index % 3) * .09f)
                    val tree = Path().apply {
                        moveTo(x, h * .94f); lineTo(x + w * .055f, h * .94f - treeH); lineTo(x + w * .11f, h * .94f); close()
                    }
                    drawPath(tree, Color(0xFF102420))
                }
                drawCircle(Color(0xFF8EE8D0), h * .08f, Offset(w * .7f, h * .56f))
            }
            PreviewStyle.CHARACTER -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFFE0C3A1), Color(0xFF8E728A))))
                drawCircle(Color(0xFFD9A08E), h * .2f, Offset(w * .5f, h * .38f))
                val body = Path().apply { moveTo(w * .25f, h); lineTo(w * .38f, h * .58f); lineTo(w * .62f, h * .58f); lineTo(w * .76f, h); close() }
                drawPath(body, Color(0xFF873F55))
                drawArc(Color(0xFF403847), 190f, 160f, false, topLeft = Offset(w * .31f, h * .13f), size = Size(w * .38f, h * .46f), style = Stroke(h * .09f))
            }
            PreviewStyle.SKETCH -> {
                drawRect(Color(0xFFD9D0BB))
                val ink = Color(0xFF6D6355)
                repeat(9) { index ->
                    val y = h * (.18f + index * .07f)
                    drawLine(ink, Offset(w * .08f, y), Offset(w * (.45f + (index % 3) * .16f), y + h * .04f), strokeWidth = 2f)
                }
                drawRect(ink, Offset(w * .56f, h * .28f), Size(w * .25f, h * .42f), style = Stroke(2.2f))
                drawCircle(ink, min(w, h) * .08f, Offset(w * .38f, h * .5f), style = Stroke(2.2f))
            }
        }
    }
}

@Composable
fun NewCanvasDialog(
    onDismiss: () -> Unit,
    onCreate: (EditorDocument) -> Unit,
) {
    var selectedPreset by remember { mutableStateOf(canvasPresets[1]) }
    var width by remember { mutableIntStateOf(selectedPreset.width) }
    var height by remember { mutableIntStateOf(selectedPreset.height) }
    var dpi by remember { mutableIntStateOf(selectedPreset.dpi) }
    var title by remember { mutableStateOf("Ilustración sin título") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = if (maxWidth > 820.dp) 820.dp else maxWidth
            val dialogHeight = if (maxHeight > 680.dp) 680.dp else maxHeight
            val compactLandscape = maxHeight < 560.dp

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .height(dialogHeight),
                color = StudioPalette.Surface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = if (compactLandscape) 16.dp else 24.dp),
                    ) {
                        Text("Nuevo lienzo", color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Comienza con un preset profesional o define tus propias medidas.",
                            color = StudioPalette.TextMuted,
                        )
                        Spacer(Modifier.height(if (compactLandscape) 12.dp else 20.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            canvasPresets.forEach { preset ->
                                PresetCard(preset, selected = preset == selectedPreset) {
                                    selectedPreset = preset
                                    width = preset.width
                                    height = preset.height
                                    dpi = preset.dpi
                                }
                            }
                        }

                        Spacer(Modifier.height(if (compactLandscape) 12.dp else 20.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Nombre del proyecto") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberField("Ancho", width, Modifier.weight(1f)) { width = it }
                            NumberField("Alto", height, Modifier.weight(1f)) { height = it }
                            NumberField("DPI", dpi, Modifier.weight(1f)) { dpi = it }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Protección de memoria activa: los lienzos extremos se ajustan a un máximo de 18 MP.",
                            color = StudioPalette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider(color = StudioPalette.Border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${width.coerceAtLeast(1)} × ${height.coerceAtLeast(1)} px",
                            color = StudioPalette.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val (safeWidth, safeHeight) = constrainCanvasSize(width, height)
                                onCreate(
                                    EditorDocument(
                                        title = title.ifBlank { "Sin título" },
                                        width = safeWidth,
                                        height = safeHeight,
                                        dpi = dpi.coerceIn(72, 600),
                                    ),
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPalette.Accent),
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Crear lienzo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: CanvasPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(170.dp).clickable(onClick = onClick),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) StudioPalette.Accent else StudioPalette.Border),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(preset.title, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(3.dp))
            Text(preset.subtitle, color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(9.dp))
            Text("${preset.width} × ${preset.height}", color = if (selected) Color.White else StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onValueChange: (Int) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.filter(Char::isDigit).toIntOrNull()?.let(onValueChange) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
