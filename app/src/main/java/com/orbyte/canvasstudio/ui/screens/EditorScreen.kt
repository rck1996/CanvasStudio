package com.orbyte.canvasstudio.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushRepository
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.GuideMode
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.LayerGroupUiModel
import com.orbyte.canvasstudio.drawing.LayerUiModel
import com.orbyte.canvasstudio.drawing.premiumBrushes
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.StudioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun decodeBitmapForImport(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

private enum class DockTab { BRUSHES, COLOR, LAYERS }

private data class ToolSpec(val tool: DrawingTool, val label: String, val icon: ImageVector)

private val toolSpecs = listOf(
    ToolSpec(DrawingTool.BRUSH, "Pincel", Icons.Outlined.Brush),
    ToolSpec(DrawingTool.ERASER, "Borrador", Icons.Outlined.DeleteSweep),
    ToolSpec(DrawingTool.FILL, "Relleno", Icons.Outlined.FormatColorFill),
    ToolSpec(DrawingTool.GRADIENT, "Degradado", Icons.Outlined.Gradient),
    ToolSpec(DrawingTool.SELECT_RECTANGLE, "Selección", Icons.Outlined.SelectAll),
    ToolSpec(DrawingTool.SELECT_ELLIPSE, "Selec. elíptica", Icons.Outlined.RadioButtonUnchecked),
    ToolSpec(DrawingTool.SELECT_LASSO, "Lazo", Icons.Outlined.Gesture),
    ToolSpec(DrawingTool.TRANSFORM, "Transformar", Icons.Outlined.Transform),
    ToolSpec(DrawingTool.LINE, "Línea", Icons.AutoMirrored.Outlined.ShowChart),
    ToolSpec(DrawingTool.RECTANGLE, "Rectángulo", Icons.Outlined.CropSquare),
    ToolSpec(DrawingTool.ELLIPSE, "Elipse", Icons.Outlined.RadioButtonUnchecked),
    ToolSpec(DrawingTool.EYEDROPPER, "Color", Icons.Outlined.Colorize),
    ToolSpec(DrawingTool.HAND, "Mover", Icons.Outlined.PanTool),
)

@Composable
fun EditorScreen(
    document: EditorDocument,
    onBackToGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var drawingView by remember { mutableStateOf<DrawingView?>(null) }
    var selectedTool by remember { mutableStateOf(DrawingTool.BRUSH) }
    var selectedDock by remember { mutableStateOf(DockTab.LAYERS) }
    var customBrushes by remember { mutableStateOf(BrushRepository.load(context)) }
    var selectedPreset by remember { mutableStateOf(premiumBrushes.first()) }
    var brushSettings by remember {
        mutableStateOf(
            BrushSettings(
                sizePx = selectedPreset.sizePx,
                opacity = selectedPreset.opacity,
                hardness = selectedPreset.hardness,
                spacing = selectedPreset.spacing,
                stabilization = selectedPreset.stabilization,
                flow = selectedPreset.flow,
                minSize = selectedPreset.minSize,
                pressureSize = selectedPreset.pressureSize,
                pressureOpacity = selectedPreset.pressureOpacity,
                pressureCurve = selectedPreset.pressureCurve,
                tiltResponse = selectedPreset.tiltResponse,
                taperStart = selectedPreset.taperStart,
                taperEnd = selectedPreset.taperEnd,
                scatter = selectedPreset.scatter,
                grain = selectedPreset.grain,
                velocitySize = selectedPreset.velocitySize,
                kind = selectedPreset.kind,
                color = AndroidColor.rgb(37, 42, 49),
            ),
        )
    }
    var layerModels by remember { mutableStateOf<List<LayerUiModel>>(emptyList()) }
    var layerGroups by remember { mutableStateOf<List<LayerGroupUiModel>>(emptyList()) }
    var changeTick by remember { mutableIntStateOf(0) }
    var saveLabel by remember { mutableStateOf("Guardado automático") }
    var engineStatus by remember { mutableStateOf("Tiles 512 · preparando") }
    var zenMode by remember { mutableStateOf(false) }
    var zoomLabel by remember { mutableIntStateOf(100) }
    var rotationLabel by remember { mutableIntStateOf(0) }
    var gridVisible by remember { mutableStateOf(false) }
    var symmetryMode by remember { mutableIntStateOf(0) }
    var guideMode by remember { mutableStateOf(GuideMode.NONE) }
    var perspectiveEditing by remember { mutableStateOf(false) }
    var selectionActive by remember { mutableStateOf(false) }
    var renameLayerOpen by remember { mutableStateOf(false) }
    var renameLayerText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(changeTick) {
        if (changeTick == 0) return@LaunchedEffect
        saveLabel = "Guardando…"
        delay(3_000)
        drawingView?.saveProject(
            projectId = document.id,
            title = document.title,
            dpi = document.dpi,
            includePreview = false,
        )
    }

    DisposableEffect(lifecycleOwner, document.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                drawingView?.saveProject(
                    projectId = document.id,
                    title = document.title,
                    dpi = document.dpi,
                    includePreview = false,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            drawingView?.saveProject(
                projectId = document.id,
                title = document.title,
                dpi = document.dpi,
                includePreview = false,
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val view = drawingView ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val bitmap = runCatching { view.exportCompositeBitmap(includePaper = true) }
                .getOrElse { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "No se pudo preparar la exportación.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@rememberLauncherForActivityResult
                }
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 96, output)) {
                            "No se pudo codificar la imagen PNG."
                        }
                    } ?: error("No se pudo abrir el archivo de destino.")
                }
                bitmap.recycle()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "PNG exportado" else result.exceptionOrNull()?.message
                            ?: "No se pudo exportar PNG.",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val openRasterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/openraster"),
    ) { uri ->
        val view = drawingView ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use(view::exportOpenRaster)
                        ?: error("No se pudo abrir el archivo de destino.")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "OpenRaster exportado" else result.exceptionOrNull()?.message
                            ?: "No se pudo exportar OpenRaster.",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val importImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val decoded = runCatching {
                    decodeBitmapForImport(
                        context = context,
                        uri = uri,
                        maxDimension = max(document.width, document.height).coerceIn(2048, 8192),
                    )
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (decoded != null) {
                        drawingView?.importBitmapAsLayer(decoded, "Imagen importada")
                        decoded.recycle()
                    } else {
                        Toast.makeText(context, "No se pudo abrir la imagen.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(StudioPalette.Background)) {
        EditorTopBar(
            document = document,
            zoom = zoomLabel,
            rotation = rotationLabel,
            saveLabel = saveLabel,
            onBack = {
                drawingView?.saveProject(document.id, document.title, document.dpi)
                onBackToGallery()
            },
            onUndo = { drawingView?.undo() },
            onRedo = { drawingView?.redo() },
            onZoomOut = {
                drawingView?.zoomBy(0.82f)
                zoomLabel = drawingView?.zoomPercent() ?: zoomLabel
            },
            onZoomIn = {
                drawingView?.zoomBy(1.22f)
                zoomLabel = drawingView?.zoomPercent() ?: zoomLabel
            },
            onRotateLeft = { drawingView?.rotateBy(-15f) },
            onRotateRight = { drawingView?.rotateBy(15f) },
            onResetView = {
                drawingView?.resetView()
                zoomLabel = drawingView?.zoomPercent() ?: zoomLabel
                rotationLabel = drawingView?.rotationDegrees() ?: rotationLabel
            },
            onExport = {
                drawingView?.commitPendingTransform()
                val safeName = document.title.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"), "").ifBlank { "Canvas Studio" }
                exportLauncher.launch("$safeName.png")
            },
            onExportOpenRaster = {
                drawingView?.commitPendingTransform()
                val safeName = document.title.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"), "").ifBlank { "Canvas Studio" }
                openRasterLauncher.launch("$safeName.ora")
            },
            onImportImage = { importImageLauncher.launch("image/*") },
            onSaveNow = {
                saveLabel = "Guardando…"
                drawingView?.saveProject(document.id, document.title, document.dpi)
            },
            gridVisible = gridVisible,
            onToggleGrid = {
                gridVisible = !gridVisible
                drawingView?.setGridVisible(gridVisible)
            },
            symmetryLabel = when (symmetryMode) {
                1 -> "Simetría vertical"
                2 -> "Simetría radial 4"
                3 -> "Simetría radial 8"
                else -> "Simetría desactivada"
            },
            onCycleSymmetry = {
                symmetryMode = (symmetryMode + 1) % 4
                drawingView?.apply {
                    when (symmetryMode) {
                        1 -> setVerticalSymmetry(true)
                        2 -> setRadialSymmetry(4)
                        3 -> setRadialSymmetry(8)
                        else -> {
                            setVerticalSymmetry(false)
                            setRadialSymmetry(1)
                        }
                    }
                }
            },
            guideLabel = when (guideMode) {
                GuideMode.PERSPECTIVE_ONE_POINT -> "Perspectiva 1 punto"
                GuideMode.PERSPECTIVE_TWO_POINT -> "Perspectiva 2 puntos"
                GuideMode.NONE -> "Guías desactivadas"
            },
            onCycleGuides = {
                guideMode = when (guideMode) {
                    GuideMode.NONE -> GuideMode.PERSPECTIVE_ONE_POINT
                    GuideMode.PERSPECTIVE_ONE_POINT -> GuideMode.PERSPECTIVE_TWO_POINT
                    GuideMode.PERSPECTIVE_TWO_POINT -> GuideMode.NONE
                }
                if (guideMode == GuideMode.NONE) perspectiveEditing = false
                drawingView?.setGuideMode(guideMode)
                drawingView?.setPerspectiveEditing(perspectiveEditing)
            },
            zenMode = zenMode,
            onToggleZen = { zenMode = !zenMode },
            onShowHelp = { showHelp = true },
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compactWorkspace = maxWidth < 1080.dp
            Row(Modifier.fillMaxSize()) {
            if (!zenMode) {
                EditorToolRail(
                    selectedTool = selectedTool,
                    compact = compactWorkspace,
                    onSelect = { selectedTool = it },
                    onTune = { selectedDock = DockTab.BRUSHES },
                )
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                AndroidView(
                    factory = { viewContext ->
                        DrawingView(viewContext).apply {
                            configureDocument(document.width, document.height)
                            val loaded = if (document.isLocal) loadProject(document.id) else false
                            if (!loaded) {
                                seedDemoArtwork(document.preview?.name)
                            } else {
                                // Capture before AndroidView's first update pass can apply the
                                // Compose defaults, then mirror the persisted values back to state.
                                val restoredGuideMode = currentGuideMode()
                                val restoredPerspectiveEditing = isPerspectiveEditing()
                                post {
                                    guideMode = restoredGuideMode
                                    perspectiveEditing = restoredPerspectiveEditing
                                }
                            }
                            tool = selectedTool
                            brushSettings = brushSettings
                            onLayersChanged = { layerModels = it }
                            onLayerGroupsChanged = { layerGroups = it }
                            onDocumentChanged = { changeTick++ }
                            onColorPicked = { color ->
                                brushSettings = brushSettings.copy(color = color)
                                selectedDock = DockTab.COLOR
                            }
                            onZoomChanged = { zoomLabel = it }
                            onRotationChanged = { rotationLabel = it }
                            onToolShortcut = { selectedTool = it }
                            onBrushSettingsShortcut = { brushSettings = it }
                            onProjectSaved = { success ->
                                saveLabel = if (success) "Guardado" else "Error al guardar"
                            }
                            onEngineStatusChanged = { engineStatus = it }
                            onEngineMessage = { message ->
                                Toast.makeText(viewContext, message, Toast.LENGTH_LONG).show()
                            }
                            onSelectionChanged = { selectionActive = it }
                            setGridVisible(gridVisible)
                            when (symmetryMode) {
                                1 -> setVerticalSymmetry(true)
                                2 -> setRadialSymmetry(4)
                                3 -> setRadialSymmetry(8)
                                else -> setRadialSymmetry(1)
                            }
                            if (!loaded) {
                                setGuideMode(guideMode)
                                setPerspectiveEditing(perspectiveEditing)
                            }
                            refreshLayerState()
                            drawingView = this
                        }
                    },
                    update = { view ->
                        view.tool = selectedTool
                        view.brushSettings = brushSettings
                        view.setGridVisible(gridVisible)
                        when (symmetryMode) {
                            1 -> view.setVerticalSymmetry(true)
                            2 -> view.setRadialSymmetry(4)
                            3 -> view.setRadialSymmetry(8)
                            else -> {
                                view.setVerticalSymmetry(false)
                                view.setRadialSymmetry(1)
                            }
                        }
                        view.setGuideMode(guideMode)
                        view.setPerspectiveEditing(perspectiveEditing)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!zenMode) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickDial(
                            label = "Tamaño",
                            progress = brushSettings.sizePx / 180f,
                            value = "${brushSettings.sizePx.toInt()} px",
                            onDecrease = { brushSettings = brushSettings.copy(sizePx = (brushSettings.sizePx / 1.16f).coerceAtLeast(2f)) },
                            onIncrease = { brushSettings = brushSettings.copy(sizePx = (brushSettings.sizePx * 1.16f).coerceAtMost(180f)) },
                        )
                        QuickDial(
                            label = "Opacidad",
                            progress = brushSettings.opacity,
                            value = "${(brushSettings.opacity * 100).toInt()}%",
                            onDecrease = { brushSettings = brushSettings.copy(opacity = (brushSettings.opacity - .1f).coerceAtLeast(.05f)) },
                            onIncrease = { brushSettings = brushSettings.copy(opacity = (brushSettings.opacity + .1f).coerceAtMost(1f)) },
                            color = Color(brushSettings.color),
                        )
                    }
                }

                if (selectionActive && !zenMode) {
                    SelectionToolbar(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                        onTransform = { selectedTool = DrawingTool.TRANSFORM },
                        onFlipHorizontal = { drawingView?.flipSelection(horizontal = true) },
                        onFlipVertical = { drawingView?.flipSelection(horizontal = false) },
                        onDelete = { drawingView?.deleteSelectionContents() },
                        onDeselect = { drawingView?.deselect() },
                    )
                }

                if (guideMode != GuideMode.NONE && !zenMode) {
                    PerspectiveToolbar(
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                        editing = perspectiveEditing,
                        onToggleEditing = {
                            perspectiveEditing = !perspectiveEditing
                            drawingView?.setPerspectiveEditing(perspectiveEditing)
                        },
                        onReset = { drawingView?.resetPerspectiveGuides() },
                    )
                }

                CanvasStatusBar(
                    document = document,
                    saveLabel = saveLabel,
                    rotation = rotationLabel,
                    engineStatus = engineStatus,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
            }

            if (!zenMode) {
                RightDock(
                    dockWidth = if (compactWorkspace) 316.dp else 360.dp,
                    selectedTab = selectedDock,
                    onSelectTab = { selectedDock = it },
                    selectedPreset = selectedPreset,
                    brushes = premiumBrushes + customBrushes,
                    brushSettings = brushSettings,
                    onPresetSelected = { preset ->
                        selectedPreset = preset
                        brushSettings = brushSettings.copy(
                            sizePx = preset.sizePx,
                            opacity = preset.opacity,
                            hardness = preset.hardness,
                            spacing = preset.spacing,
                            stabilization = preset.stabilization,
                            flow = preset.flow,
                            minSize = preset.minSize,
                            pressureSize = preset.pressureSize,
                            pressureOpacity = preset.pressureOpacity,
                            pressureCurve = preset.pressureCurve,
                            tiltResponse = preset.tiltResponse,
                            taperStart = preset.taperStart,
                            taperEnd = preset.taperEnd,
                            scatter = preset.scatter,
                            grain = preset.grain,
                            velocitySize = preset.velocitySize,
                            kind = preset.kind,
                        )
                        selectedTool = DrawingTool.BRUSH
                    },
                    onBrushSettingsChanged = { brushSettings = it },
                    onSaveCustomBrush = { brushName ->
                        val custom = BrushPreset(
                            id = "custom-${System.currentTimeMillis()}",
                            name = brushName.ifBlank { "Pincel personalizado ${customBrushes.size + 1}" },
                            category = "Personalizados",
                            kind = brushSettings.kind,
                            sizePx = brushSettings.sizePx,
                            opacity = brushSettings.opacity,
                            hardness = brushSettings.hardness,
                            spacing = brushSettings.spacing,
                            stabilization = brushSettings.stabilization,
                            flow = brushSettings.flow,
                            minSize = brushSettings.minSize,
                            pressureSize = brushSettings.pressureSize,
                            pressureOpacity = brushSettings.pressureOpacity,
                            pressureCurve = brushSettings.pressureCurve,
                            tiltResponse = brushSettings.tiltResponse,
                            taperStart = brushSettings.taperStart,
                            taperEnd = brushSettings.taperEnd,
                            scatter = brushSettings.scatter,
                            grain = brushSettings.grain,
                            velocitySize = brushSettings.velocitySize,
                        )
                        val updatedBrushes = (customBrushes + custom).takeLast(40)
                        customBrushes = updatedBrushes
                        BrushRepository.save(context, updatedBrushes)
                        selectedPreset = custom
                    },
                    layers = layerModels,
                    groups = layerGroups,
                    onSelectLayer = { drawingView?.setActiveLayer(it) },
                    onToggleVisibility = { drawingView?.toggleLayerVisibility(it) },
                    onLayerOpacity = { id, opacity -> drawingView?.setLayerOpacity(id, opacity) },
                    onLayerBlendMode = { id, mode -> drawingView?.setLayerBlendMode(id, mode) },
                    onLayerAlphaLock = { id, locked -> drawingView?.setLayerAlphaLocked(id, locked) },
                    onLayerClipping = { id, clipping -> drawingView?.setLayerClipping(id, clipping) },
                    onCreateGroup = { drawingView?.createGroupFromActiveLayer() },
                    onUngroupLayer = { drawingView?.ungroupActiveLayer() },
                    onToggleGroupVisibility = { drawingView?.toggleGroupVisibility(it) },
                    onToggleGroupCollapsed = { drawingView?.toggleGroupCollapsed(it) },
                    onGroupOpacity = { id, opacity -> drawingView?.setGroupOpacity(id, opacity) },
                    onAddMask = { drawingView?.addMaskToActiveLayer() },
                    onEditMask = { id, editing ->
                        drawingView?.setEditingLayerMask(id, editing)
                        if (editing) selectedTool = DrawingTool.BRUSH
                    },
                    onToggleMask = { drawingView?.toggleLayerMaskEnabled(it) },
                    onDeleteMask = { drawingView?.deleteActiveLayerMask() },
                    onAddLayer = { drawingView?.addLayer() },
                    onDuplicateLayer = { drawingView?.duplicateActiveLayer() },
                    onDeleteLayer = { drawingView?.deleteActiveLayer() },
                    onMoveLayerUp = { drawingView?.moveActiveLayer(true) },
                    onMoveLayerDown = { drawingView?.moveActiveLayer(false) },
                    onClearLayer = { drawingView?.clearActiveLayer() },
                    onRenameLayer = {
                        renameLayerText = layerModels.firstOrNull { it.isActive }?.name.orEmpty()
                        renameLayerOpen = true
                    },
                )
            }
        }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Ayuda rápida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("S Pen: presión e inclinación controlan el trazo; el botón lateral activa el borrador temporal.")
                    Text("Vista: usa dos dedos para mover, ampliar y girar. Restablecer vista vuelve al encuadre inicial.")
                    Text("Rendimiento: los tiles visibles permanecen en memoria y se guardan incrementalmente.")
                    Text("Teclado: B pincel, E borrador, H mano, Ctrl+Z deshacer. El perfil puede cambiarse en Ajustes.")
                    Text("La cuadrícula solo es una guía visual y nunca se exporta.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Entendido") }
            },
        )
    }

    if (renameLayerOpen) {
        AlertDialog(
            onDismissRequest = { renameLayerOpen = false },
            title = { Text("Renombrar capa") },
            text = {
                OutlinedTextField(
                    value = renameLayerText,
                    onValueChange = { renameLayerText = it.take(48) },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawingView?.renameActiveLayer(renameLayerText)
                        renameLayerOpen = false
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameLayerOpen = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    document: EditorDocument,
    zoom: Int,
    rotation: Int,
    saveLabel: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetView: () -> Unit,
    onExport: () -> Unit,
    onExportOpenRaster: () -> Unit,
    onImportImage: () -> Unit,
    onSaveNow: () -> Unit,
    gridVisible: Boolean,
    onToggleGrid: () -> Unit,
    symmetryLabel: String,
    onCycleSymmetry: () -> Unit,
    guideLabel: String,
    onCycleGuides: () -> Unit,
    zenMode: Boolean,
    onToggleZen: () -> Unit,
    onShowHelp: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border),
    ) {
        val compact = maxWidth < 1120.dp
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = StudioPalette.Text) }
            Column(Modifier.width(if (compact) 148.dp else 230.dp)) {
                Text(
                    document.title,
                    color = StudioPalette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        "${document.width} × ${document.height}px · ${document.dpi} dpi",
                        color = StudioPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TopIconButton(Icons.AutoMirrored.Outlined.Undo, "Deshacer", onUndo)
            TopIconButton(Icons.AutoMirrored.Outlined.Redo, "Rehacer", onRedo)
            Spacer(Modifier.width(6.dp))
            Surface(
                color = StudioPalette.SurfaceRaised,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onZoomOut, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.ZoomOut, null, tint = StudioPalette.TextMuted)
                    }
                    Text("$zoom%", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = onZoomIn, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.ZoomIn, null, tint = StudioPalette.TextMuted)
                    }
                    if (!compact) {
                        IconButton(onClick = onRotateLeft, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.RotateLeft, "Rotar a la izquierda", tint = StudioPalette.TextMuted)
                        }
                        Text("${rotation}°", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = onRotateRight, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.RotateRight, "Rotar a la derecha", tint = StudioPalette.TextMuted)
                        }
                    }
                    IconButton(onClick = onResetView, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.CenterFocusStrong, "Restablecer vista", tint = StudioPalette.TextMuted)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(StudioPalette.Success, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(saveLabel, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
            }
            TopIconButton(Icons.Outlined.Fullscreen, if (zenMode) "Mostrar paneles" else "Modo lienzo", onToggleZen)
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = StudioPalette.Accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 14.dp, vertical = 9.dp),
            ) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                if (!compact) {
                    Spacer(Modifier.width(7.dp))
                    Text("Exportar PNG")
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreHoriz, "Más opciones", tint = StudioPalette.TextMuted)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Importar imagen como capa") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = { menuExpanded = false; onImportImage() },
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar OpenRaster (.ora)") },
                        leadingIcon = { Icon(Icons.Outlined.Layers, null) },
                        onClick = { menuExpanded = false; onExportOpenRaster() },
                    )
                    DropdownMenuItem(
                        text = { Text("Guardar ahora") },
                        leadingIcon = { Icon(Icons.Outlined.Save, null) },
                        onClick = { menuExpanded = false; onSaveNow() },
                    )
                    DropdownMenuItem(
                        text = { Text("Ayuda rápida") },
                        leadingIcon = { Icon(Icons.Outlined.HelpOutline, null) },
                        onClick = { menuExpanded = false; onShowHelp() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (gridVisible) "Ocultar cuadrícula" else "Mostrar cuadrícula") },
                        leadingIcon = { Icon(Icons.Outlined.CropSquare, null) },
                        onClick = { menuExpanded = false; onToggleGrid() },
                    )
                    DropdownMenuItem(
                        text = { Text(symmetryLabel) },
                        leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) },
                        onClick = { menuExpanded = false; onCycleSymmetry() },
                    )
                    DropdownMenuItem(
                        text = { Text(guideLabel) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ShowChart, null) },
                        onClick = { menuExpanded = false; onCycleGuides() },
                    )
                    if (compact) {
                        DropdownMenuItem(
                            text = { Text("Rotar -15°") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateLeft, null) },
                            onClick = { menuExpanded = false; onRotateLeft() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rotar +15°") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, null) },
                            onClick = { menuExpanded = false; onRotateRight() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, description, tint = StudioPalette.TextMuted, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun EditorToolRail(
    selectedTool: DrawingTool,
    compact: Boolean,
    onSelect: (DrawingTool) -> Unit,
    onTune: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (compact) 72.dp else 92.dp)
            .fillMaxHeight()
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border)
            .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toolSpecs.forEach { spec ->
                ToolRailButton(
                    spec = spec,
                    selected = selectedTool == spec.tool,
                    showLabel = !compact,
                    onClick = { onSelect(spec.tool) },
                )
                Spacer(Modifier.height(5.dp))
            }
        }
        IconButton(onClick = onTune) {
            Icon(Icons.Outlined.Tune, "Ajustes de pincel", tint = StudioPalette.TextMuted)
        }
    }
}

@Composable
private fun ToolRailButton(spec: ToolSpec, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) StudioPalette.Accent else Color.Transparent, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(spec.icon, spec.label, tint = if (selected) Color.White else StudioPalette.TextMuted, modifier = Modifier.size(21.dp))
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(spec.label, color = if (selected) Color.White else StudioPalette.TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun QuickDial(
    label: String,
    progress: Float,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    color: Color? = null,
) {
    Surface(
        color = Color(0xEA171A1F),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(35.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                    color = StudioPalette.Accent,
                    trackColor = StudioPalette.SurfaceHover,
                )
                Text(value.substringBefore(" ").substringBefore("%"), color = StudioPalette.Text, fontSize = 9.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(label, color = StudioPalette.TextMuted, fontSize = 10.sp)
                Text(value, color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
            }
            color?.let {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(22.dp).background(it, CircleShape).border(1.dp, Color.White.copy(alpha = .6f), CircleShape))
            }
            IconButton(onClick = onDecrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Remove, "Reducir $label", tint = StudioPalette.TextMuted)
            }
            IconButton(onClick = onIncrease, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, "Aumentar $label", tint = StudioPalette.Text)
            }
        }
    }
}

@Composable
private fun PerspectiveToolbar(
    modifier: Modifier = Modifier,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleEditing) {
                Text(if (editing) "Terminar guías" else "Editar puntos")
            }
            TextButton(onClick = onReset) { Text("Restablecer") }
        }
    }
}

@Composable
private fun SelectionToolbar(
    modifier: Modifier = Modifier,
    onTransform: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onTransform) { Text("Transformar") }
            TextButton(onClick = onFlipHorizontal) { Text("Voltear H") }
            TextButton(onClick = onFlipVertical) { Text("Voltear V") }
            TextButton(onClick = onDelete) { Text("Borrar") }
            TextButton(onClick = onDeselect) { Text("Deseleccionar") }
        }
    }
}

@Composable
private fun CanvasStatusBar(
    document: EditorDocument,
    saveLabel: String,
    rotation: Int,
    engineStatus: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xEA171A1F),
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Save, null, tint = StudioPalette.Success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(saveLabel, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(18.dp))
            Text("${document.width} × ${document.height}", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(18.dp))
            Text("Rotación ${rotation}°", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(18.dp))
            Text(
                engineStatus,
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RightDock(
    dockWidth: androidx.compose.ui.unit.Dp,
    selectedTab: DockTab,
    onSelectTab: (DockTab) -> Unit,
    selectedPreset: BrushPreset,
    brushes: List<BrushPreset>,
    brushSettings: BrushSettings,
    onPresetSelected: (BrushPreset) -> Unit,
    onBrushSettingsChanged: (BrushSettings) -> Unit,
    onSaveCustomBrush: (String) -> Unit,
    layers: List<LayerUiModel>,
    groups: List<LayerGroupUiModel>,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onLayerOpacity: (String, Float) -> Unit,
    onLayerBlendMode: (String, LayerBlendMode) -> Unit,
    onLayerAlphaLock: (String, Boolean) -> Unit,
    onLayerClipping: (String, Boolean) -> Unit,
    onCreateGroup: () -> Unit,
    onUngroupLayer: () -> Unit,
    onToggleGroupVisibility: (String) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit,
    onGroupOpacity: (String, Float) -> Unit,
    onAddMask: () -> Unit,
    onEditMask: (String, Boolean) -> Unit,
    onToggleMask: (String) -> Unit,
    onDeleteMask: () -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onMoveLayerUp: () -> Unit,
    onMoveLayerDown: () -> Unit,
    onClearLayer: () -> Unit,
    onRenameLayer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(dockWidth)
            .fillMaxHeight()
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border),
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DockTabButton(
                icon = Icons.Outlined.Brush,
                label = "Pinceles",
                selected = selectedTab == DockTab.BRUSHES,
                modifier = Modifier.weight(1f),
            ) { onSelectTab(DockTab.BRUSHES) }
            DockTabButton(
                icon = Icons.Outlined.Palette,
                label = "Color",
                selected = selectedTab == DockTab.COLOR,
                modifier = Modifier.weight(1f),
            ) { onSelectTab(DockTab.COLOR) }
            DockTabButton(
                icon = Icons.Outlined.Layers,
                label = "Capas",
                selected = selectedTab == DockTab.LAYERS,
                modifier = Modifier.weight(1f),
            ) { onSelectTab(DockTab.LAYERS) }
        }
        HorizontalDivider(color = StudioPalette.Border)
        when (selectedTab) {
            DockTab.BRUSHES -> BrushDock(
                selectedPreset = selectedPreset,
                brushes = brushes,
                settings = brushSettings,
                onPresetSelected = onPresetSelected,
                onSettingsChanged = onBrushSettingsChanged,
                onSaveCustomBrush = onSaveCustomBrush,
            )
            DockTab.COLOR -> ColorDock(
                currentColor = brushSettings.color,
                onColorChanged = { onBrushSettingsChanged(brushSettings.copy(color = it)) },
            )
            DockTab.LAYERS -> LayersDock(
                layers = layers,
                groups = groups,
                onSelectLayer = onSelectLayer,
                onToggleVisibility = onToggleVisibility,
                onOpacity = onLayerOpacity,
                onBlendMode = onLayerBlendMode,
                onAlphaLock = onLayerAlphaLock,
                onClipping = onLayerClipping,
                onCreateGroup = onCreateGroup,
                onUngroup = onUngroupLayer,
                onToggleGroupVisibility = onToggleGroupVisibility,
                onToggleGroupCollapsed = onToggleGroupCollapsed,
                onGroupOpacity = onGroupOpacity,
                onAddMask = onAddMask,
                onEditMask = onEditMask,
                onToggleMask = onToggleMask,
                onDeleteMask = onDeleteMask,
                onAdd = onAddLayer,
                onDuplicate = onDuplicateLayer,
                onDelete = onDeleteLayer,
                onMoveUp = onMoveLayerUp,
                onMoveDown = onMoveLayerDown,
                onClear = onClearLayer,
                onRename = onRenameLayer,
            )
        }
    }
}

@Composable
private fun DockTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) StudioPalette.SurfaceHover else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) StudioPalette.Accent else StudioPalette.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) StudioPalette.Text else StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BrushDock(
    selectedPreset: BrushPreset,
    brushes: List<BrushPreset>,
    settings: BrushSettings,
    onPresetSelected: (BrushPreset) -> Unit,
    onSettingsChanged: (BrushSettings) -> Unit,
    onSaveCustomBrush: (String) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("Todos") }
    var brushQuery by remember { mutableStateOf("") }
    var saveBrushDialogOpen by remember { mutableStateOf(false) }
    var customBrushName by remember(selectedPreset.id) {
        mutableStateOf("${selectedPreset.name} personalizado")
    }
    val categories = listOf("Todos") + brushes.map(BrushPreset::category).distinct()
    val categoryBrushes = if (selectedCategory == "Todos") {
        brushes
    } else {
        brushes.filter { it.category == selectedCategory }
    }
    val visibleBrushes = categoryBrushes.filter {
        brushQuery.isBlank() ||
            it.name.contains(brushQuery.trim(), ignoreCase = true) ||
            it.category.contains(brushQuery.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca de pinceles", color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Motor 2.0 · presión, textura, taper y velocidad",
                    color = StudioPalette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = { saveBrushDialogOpen = true }) {
                Icon(Icons.Outlined.Add, "Guardar pincel personalizado", tint = StudioPalette.TextMuted)
            }
        }
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(
            value = brushQuery,
            onValueChange = { brushQuery = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, "Buscar pinceles") },
            placeholder = { Text("Buscar pinceles") },
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Surface(
                    modifier = Modifier.clickable { selectedCategory = category },
                    color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) StudioPalette.Accent else StudioPalette.Border,
                    ),
                ) {
                    Text(
                        category,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = if (selected) Color.White else StudioPalette.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        visibleBrushes.forEach { preset ->
            BrushPresetRow(preset, selected = preset.id == selectedPreset.id) { onPresetSelected(preset) }
            Spacer(Modifier.height(7.dp))
        }
        if (visibleBrushes.isEmpty()) {
            Text(
                "No hay pinceles que coincidan con la búsqueda.",
                color = StudioPalette.TextMuted,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Ajustes del pincel", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(9.dp))
        SettingSlider("Tamaño", settings.sizePx, 2f..180f, "${settings.sizePx.toInt()} px") {
            onSettingsChanged(settings.copy(sizePx = it))
        }
        SettingSlider("Opacidad", settings.opacity, 0.05f..1f, "${(settings.opacity * 100).toInt()}%") {
            onSettingsChanged(settings.copy(opacity = it))
        }
        SettingSlider("Flujo", settings.flow, 0.08f..1f, "${(settings.flow * 100).toInt()}%") {
            onSettingsChanged(settings.copy(flow = it))
        }
        SettingSlider("Dureza", settings.hardness, 0f..1f, "${(settings.hardness * 100).toInt()}%") {
            onSettingsChanged(settings.copy(hardness = it))
        }
        if (settings.kind == BrushKind.AIRBRUSH || settings.kind == BrushKind.CHARCOAL || settings.kind == BrushKind.CHALK) {
            SettingSlider("Espaciado", settings.spacing, 0.025f..0.4f, "${(settings.spacing * 100).toInt()}%") {
                onSettingsChanged(settings.copy(spacing = it))
            }
        }
        SettingSlider("Tamaño mínimo", settings.minSize, 0.02f..1f, "${(settings.minSize * 100).toInt()}%") {
            onSettingsChanged(settings.copy(minSize = it))
        }
        SettingSlider(
            "Curva de presión",
            settings.pressureCurve,
            0.35f..2.5f,
            String.format(Locale.US, "%.2f", settings.pressureCurve),
        ) {
            onSettingsChanged(settings.copy(pressureCurve = it))
        }
        SettingSlider("Inclinación", settings.tiltResponse, 0f..1f, "${(settings.tiltResponse * 100).toInt()}%") {
            onSettingsChanged(settings.copy(tiltResponse = it))
        }
        SettingSlider("Estabilización", settings.stabilization, 0f..0.9f, "${(settings.stabilization * 100).toInt()}%") {
            onSettingsChanged(settings.copy(stabilization = it))
        }
        SettingSlider("Taper inicial", settings.taperStart, 0f..0.48f, "${(settings.taperStart * 100).toInt()}%") {
            onSettingsChanged(settings.copy(taperStart = it))
        }
        SettingSlider("Taper final", settings.taperEnd, 0f..0.48f, "${(settings.taperEnd * 100).toInt()}%") {
            onSettingsChanged(settings.copy(taperEnd = it))
        }
        SettingSlider("Dispersión", settings.scatter, 0f..0.5f, "${(settings.scatter * 100).toInt()}%") {
            onSettingsChanged(settings.copy(scatter = it))
        }
        SettingSlider("Textura", settings.grain, 0f..1f, "${(settings.grain * 100).toInt()}%") {
            onSettingsChanged(settings.copy(grain = it))
        }
        SettingSlider("Respuesta a velocidad", settings.velocitySize, 0f..1f, "${(settings.velocitySize * 100).toInt()}%") {
            onSettingsChanged(settings.copy(velocitySize = it))
        }
        BrushToggle("Presión controla tamaño", settings.pressureSize) {
            onSettingsChanged(settings.copy(pressureSize = it))
        }
        BrushToggle("Presión controla opacidad", settings.pressureOpacity) {
            onSettingsChanged(settings.copy(pressureOpacity = it))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "El botón + guarda estos ajustes como un pincel personalizado en el dispositivo.",
            color = StudioPalette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (saveBrushDialogOpen) {
        AlertDialog(
            onDismissRequest = { saveBrushDialogOpen = false },
            title = { Text("Guardar pincel") },
            text = {
                OutlinedTextField(
                    value = customBrushName,
                    onValueChange = { customBrushName = it.take(40) },
                    label = { Text("Nombre del pincel") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomBrush(customBrushName)
                        saveBrushDialogOpen = false
                    },
                    enabled = customBrushName.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { saveBrushDialogOpen = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BrushToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = StudioPalette.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BrushPresetRow(preset: BrushPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) StudioPalette.Accent else StudioPalette.Border),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(95.dp)) {
                Text(preset.name, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text(preset.category, color = StudioPalette.TextMuted, fontSize = 10.sp)
            }
            BrushStrokePreview(preset, Modifier.weight(1f).height(34.dp))
        }
    }
}

@Composable
private fun BrushStrokePreview(preset: BrushPreset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        val path = Path().apply {
            moveTo(4f, y + 3f)
            cubicTo(size.width * .23f, y - 13f, size.width * .42f, y + 11f, size.width * .68f, y - 4f)
            cubicTo(size.width * .8f, y - 9f, size.width * .9f, y + 2f, size.width - 4f, y - 7f)
        }
        val previewWidth = (2f + preset.sizePx / 15f).coerceAtMost(10f)
        when (preset.kind) {
            BrushKind.AIRBRUSH -> {
                drawPath(path, Color.White.copy(alpha = .12f), style = Stroke(previewWidth * 2.8f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = .2f), style = Stroke(previewWidth * 1.7f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = .34f), style = Stroke(previewWidth * .75f, cap = StrokeCap.Round))
            }
            BrushKind.CHARCOAL, BrushKind.CHALK -> {
                repeat(4) { index ->
                    val offset = (index - 1.5f) * 1.7f
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = .16f + index * .08f),
                        style = Stroke((previewWidth * (.28f + index * .08f)).coerceAtLeast(1f), cap = StrokeCap.Round),
                        alpha = 1f,
                    )
                    if (offset != 0f) {
                        drawLine(
                            color = Color.White.copy(alpha = .18f),
                            start = Offset(size.width * .2f, y + offset),
                            end = Offset(size.width * .82f, y - 3f + offset),
                            strokeWidth = 1f + index * .3f,
                        )
                    }
                }
            }
            BrushKind.PENCIL -> {
                drawPath(path, Color.White.copy(alpha = preset.opacity.coerceAtLeast(.42f)), style = Stroke(previewWidth * .62f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = .2f), style = Stroke((previewWidth * .2f).coerceAtLeast(.8f), cap = StrokeCap.Round))
            }
            BrushKind.MARKER -> drawPath(
                path,
                Color.White.copy(alpha = preset.opacity.coerceAtLeast(.32f)),
                style = Stroke(previewWidth, cap = StrokeCap.Square),
            )
            else -> drawPath(
                path,
                Color.White.copy(alpha = preset.opacity.coerceAtLeast(.4f)),
                style = Stroke(previewWidth, cap = StrokeCap.Round),
            )
        }
    }
}


@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValueChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row {
            Text(label, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(display, color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = StudioPalette.Accent,
                activeTrackColor = StudioPalette.Accent,
                inactiveTrackColor = StudioPalette.SurfaceHover,
            ),
        )
    }
}

@Composable
private fun ColorDock(currentColor: Int, onColorChanged: (Int) -> Unit) {
    val hsv = remember(currentColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColor, it) }
    }
    var hue by remember(currentColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(currentColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(currentColor) { mutableFloatStateOf(hsv[2]) }

    fun emitColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        onColorChanged(AndroidColor.HSVToColor(floatArrayOf(newHue, newSaturation, newValue)))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Color", color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                Text("Rueda HSV y paleta del documento", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.size(34.dp).background(Color(currentColor), CircleShape).border(1.dp, StudioPalette.Border, CircleShape))
        }
        Spacer(Modifier.height(18.dp))
        HueWheel(
            hue = hue,
            saturation = saturation,
            value = value,
            modifier = Modifier.size(225.dp),
            onHueChanged = {
                hue = it
                emitColor(newHue = it)
            },
            onSaturationValueChanged = { newSaturation, newValue ->
                saturation = newSaturation
                value = newValue
                emitColor(newSaturation = newSaturation, newValue = newValue)
            },
        )
        Spacer(Modifier.height(18.dp))
        SettingSlider("Tono", hue, 0f..360f, "${hue.toInt()}°") {
            hue = it
            emitColor(newHue = it)
        }
        SettingSlider("Saturación", saturation, 0f..1f, "${(saturation * 100).toInt()}%") {
            saturation = it
            emitColor(newSaturation = it)
        }
        SettingSlider("Luminosidad", value, 0f..1f, "${(value * 100).toInt()}%") {
            value = it
            emitColor(newValue = it)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Paleta reciente", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                String.format(Locale.US, "#%06X", 0xFFFFFF and currentColor),
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        val swatches = listOf(0xFF20242B, 0xFFF4F1E8, 0xFFE25555, 0xFFF2A65A, 0xFFE6D04A, 0xFF57B879, 0xFF4A90E2, 0xFF7257D9, 0xFFB052A1, 0xFF8A6751)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            swatches.forEach { swatch ->
                Box(
                    Modifier
                        .size(25.dp)
                        .background(Color(swatch), RoundedCornerShape(7.dp))
                        .border(1.dp, StudioPalette.Border, RoundedCornerShape(7.dp))
                        .clickable { onColorChanged(swatch.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun HueWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    modifier: Modifier,
    onHueChanged: (Float) -> Unit,
    onSaturationValueChanged: (Float, Float) -> Unit,
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = point.x - center.x
                    val dy = point.y - center.y
                    val radius = min(size.width, size.height) / 2f
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared > (radius * .62f) * (radius * .62f)) {
                        val angle = (atan2(dy.toDouble(), dx.toDouble()).toFloat() * 180f / PI.toFloat() + 360f) % 360f
                        onHueChanged(angle)
                    } else {
                        val squareHalf = radius * .42f
                        val sat = ((point.x - (center.x - squareHalf)) / (squareHalf * 2f)).coerceIn(0f, 1f)
                        val v = (1f - (point.y - (center.y - squareHalf)) / (squareHalf * 2f)).coerceIn(0f, 1f)
                        onSaturationValueChanged(sat, v)
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = min(size.width, size.height) / 2f
        val ringWidth = outerRadius * .19f
        repeat(120) { index ->
            drawArc(
                color = Color(AndroidColor.HSVToColor(floatArrayOf(index * 3f, 1f, 1f))),
                startAngle = index * 3f,
                sweepAngle = 3.4f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius + ringWidth / 2f, center.y - outerRadius + ringWidth / 2f),
                size = Size((outerRadius - ringWidth / 2f) * 2f, (outerRadius - ringWidth / 2f) * 2f),
                style = Stroke(ringWidth),
            )
        }
        val squareHalf = outerRadius * .42f
        val steps = 24
        val cell = squareHalf * 2f / steps
        repeat(steps) { xIndex ->
            repeat(steps) { yIndex ->
                val sat = xIndex / (steps - 1f)
                val v = 1f - yIndex / (steps - 1f)
                drawRect(
                    Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, v))),
                    topLeft = Offset(center.x - squareHalf + xIndex * cell, center.y - squareHalf + yIndex * cell),
                    size = Size(cell + 1f, cell + 1f),
                )
            }
        }
        val pointerAngle = hue * PI.toFloat() / 180f
        val pointerRadius = outerRadius - ringWidth / 2f
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = Offset(center.x + cos(pointerAngle.toDouble()).toFloat() * pointerRadius, center.y + sin(pointerAngle.toDouble()).toFloat() * pointerRadius),
            style = Stroke(2.5f),
        )
        drawCircle(
            color = Color.White,
            radius = 5f,
            center = Offset(center.x - squareHalf + saturation * squareHalf * 2f, center.y + squareHalf - value * squareHalf * 2f),
            style = Stroke(2f),
        )
    }
}

@Composable
private fun LayersDock(
    layers: List<LayerUiModel>,
    groups: List<LayerGroupUiModel>,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onOpacity: (String, Float) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onAlphaLock: (String, Boolean) -> Unit,
    onClipping: (String, Boolean) -> Unit,
    onCreateGroup: () -> Unit,
    onUngroup: () -> Unit,
    onToggleGroupVisibility: (String) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit,
    onGroupOpacity: (String, Float) -> Unit,
    onAddMask: () -> Unit,
    onEditMask: (String, Boolean) -> Unit,
    onToggleMask: (String) -> Unit,
    onDeleteMask: () -> Unit,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClear: () -> Unit,
    onRename: () -> Unit,
) {
    val active = layers.firstOrNull { it.isActive }
    val activeGroup = groups.firstOrNull { it.id == active?.groupId }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Capas", color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                    Text("Composición raster no destructiva", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onCreateGroup) { Icon(Icons.Outlined.Layers, "Crear grupo", tint = StudioPalette.TextMuted) }
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Añadir capa", tint = StudioPalette.TextMuted) }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        active?.let { layer ->
                            val modes = LayerBlendMode.values()
                            onBlendMode(layer.id, modes[(layer.blendMode.ordinal + 1) % modes.size])
                        }
                    },
                color = StudioPalette.SurfaceRaised,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(active?.blendMode?.displayName() ?: "Normal", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("Toca para cambiar", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (active != null) {
                SettingSlider("Opacidad de capa", active.opacity, 0f..1f, "${(active.opacity * 100).toInt()}%") {
                    onOpacity(active.id, it)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LayerToggle(
                        label = "Bloquear alfa",
                        checked = active.alphaLocked,
                        modifier = Modifier.weight(1f),
                    ) { onAlphaLock(active.id, it) }
                    LayerToggle(
                        label = "Recorte",
                        checked = active.clipping,
                        modifier = Modifier.weight(1f),
                    ) { onClipping(active.id, it) }
                }
                Spacer(Modifier.height(8.dp))
                if (active.hasMask) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LayerToggle(
                            label = if (active.editingMask) "Editando máscara" else "Editar máscara",
                            checked = active.editingMask,
                            modifier = Modifier.weight(1f),
                        ) { onEditMask(active.id, it) }
                        LayerToggle(
                            label = if (active.maskEnabled) "Máscara activa" else "Máscara oculta",
                            checked = active.maskEnabled,
                            modifier = Modifier.weight(1f),
                        ) { onToggleMask(active.id) }
                    }
                    TextButton(onClick = onDeleteMask) { Text("Eliminar máscara") }
                } else {
                    TextButton(onClick = onAddMask) { Text("Añadir máscara raster") }
                }
                if (activeGroup != null) {
                    SettingSlider(
                        "Opacidad del grupo",
                        activeGroup.opacity,
                        0f..1f,
                        "${(activeGroup.opacity * 100).toInt()}%",
                    ) { onGroupOpacity(activeGroup.id, it) }
                }
            }
        }
        HorizontalDivider(color = StudioPalette.Border)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp)) {
            val emittedGroups = mutableSetOf<String>()
            layers.forEach { layer ->
                val group = layer.groupId?.let { id -> groups.firstOrNull { it.id == id } }
                if (group != null && emittedGroups.add(group.id)) {
                    LayerGroupRow(
                        group = group,
                        onToggleVisibility = onToggleGroupVisibility,
                        onToggleCollapsed = onToggleGroupCollapsed,
                    )
                    Spacer(Modifier.height(7.dp))
                }
                if (group?.collapsed != true) {
                    LayerRow(
                        layer = layer,
                        onSelect = onSelectLayer,
                        onToggleVisibility = onToggleVisibility,
                        indented = group != null,
                    )
                    Spacer(Modifier.height(7.dp))
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = StudioPalette.SurfaceRaised,
                shape = RoundedCornerShape(11.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
            ) {
                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, tint = StudioPalette.TextMuted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(38.dp).background(Color.White, RoundedCornerShape(7.dp)).border(1.dp, StudioPalette.Border, RoundedCornerShape(7.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Papel", color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                        Text("Blanco · bloqueado", color = StudioPalette.TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = StudioPalette.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCreateGroup, modifier = Modifier.weight(1f)) { Text("Agrupar capa") }
            TextButton(onClick = onUngroup, modifier = Modifier.weight(1f)) { Text("Sacar del grupo") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LayerAction(Icons.Outlined.Add, "Añadir", onAdd)
            LayerAction(Icons.Outlined.ContentCopy, "Duplicar", onDuplicate)
            LayerAction(Icons.Outlined.MoreHoriz, "Renombrar", onRename)
            LayerAction(Icons.Outlined.ArrowUpward, "Subir", onMoveUp)
            LayerAction(Icons.Outlined.ArrowDownward, "Bajar", onMoveDown)
            LayerAction(Icons.Outlined.Remove, "Limpiar", onClear)
            LayerAction(Icons.Outlined.Delete, "Eliminar", onDelete)
        }
    }
}

private fun LayerBlendMode.displayName(): String = when (this) {
    LayerBlendMode.NORMAL -> "Normal"
    LayerBlendMode.MULTIPLY -> "Multiplicar"
    LayerBlendMode.SCREEN -> "Trama"
    LayerBlendMode.OVERLAY -> "Superponer"
    LayerBlendMode.ADD -> "Añadir"
    LayerBlendMode.DARKEN -> "Oscurecer"
    LayerBlendMode.LIGHTEN -> "Aclarar"
}

@Composable
private fun LayerToggle(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        color = if (checked) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) StudioPalette.Accent else StudioPalette.Border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (checked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                null,
                tint = if (checked) Color.White else StudioPalette.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (checked) Color.White else StudioPalette.TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LayerGroupRow(
    group: LayerGroupUiModel,
    onToggleVisibility: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StudioPalette.SurfaceHover,
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onToggleVisibility(group.id) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (group.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    null,
                    tint = StudioPalette.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(onClick = { onToggleCollapsed(group.id) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (group.collapsed) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                    null,
                    tint = StudioPalette.Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Icon(Icons.Outlined.Layers, null, tint = StudioPalette.Accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text(
                    "${group.layerCount} capas · ${(group.opacity * 100).toInt()}%",
                    color = StudioPalette.TextMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: LayerUiModel,
    onSelect: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    indented: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 18.dp else 0.dp)
            .clickable { onSelect(layer.id) },
        color = if (layer.isActive) StudioPalette.Accent else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (layer.isActive) Color(0xFF74A3FF) else StudioPalette.Border),
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggleVisibility(layer.id) }, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    null,
                    tint = if (layer.isActive) Color.White else StudioPalette.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (layer.name.hashCode() % 2 == 0) Color(0xFF394450) else Color(0xFF55505B),
                        RoundedCornerShape(7.dp),
                    )
                    .border(1.dp, if (layer.isActive) Color.White.copy(alpha = .35f) else StudioPalette.Border, RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(layer.name, color = Color.White, style = MaterialTheme.typography.labelLarge)
                val attributes = buildList {
                    add("Raster")
                    add("${(layer.opacity * 100).toInt()}%")
                    if (layer.alphaLocked) add("Alfa")
                    if (layer.clipping) add("Recorte")
                    if (layer.hasMask) add(if (layer.editingMask) "Máscara editando" else "Máscara")
                }.joinToString(" · ")
                Text(attributes, color = if (layer.isActive) Color.White.copy(alpha = .72f) else StudioPalette.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LayerAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = StudioPalette.TextMuted, modifier = Modifier.size(19.dp))
    }
}
