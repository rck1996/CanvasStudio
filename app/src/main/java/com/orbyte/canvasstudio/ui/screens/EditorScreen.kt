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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushRepository
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.BrushTipShape
import com.orbyte.canvasstudio.drawing.BasicPsdCodec
import com.orbyte.canvasstudio.drawing.PsdLayerReader
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.GuideMode
import com.orbyte.canvasstudio.drawing.InkDrawingContainer
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.LayerGroupUiModel
import com.orbyte.canvasstudio.drawing.LayerUiModel
import com.orbyte.canvasstudio.drawing.experimentalBrushes
import com.orbyte.canvasstudio.drawing.premiumBrushes
import com.orbyte.canvasstudio.drawing.sanitized
import com.orbyte.canvasstudio.drawing.raster.RendererMode
import com.orbyte.canvasstudio.BuildConfig
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.RecentColorRepository
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.ui.tutorial.EditorTutorialFocusProvider
import com.orbyte.canvasstudio.ui.tutorial.EditorTutorialOverlay
import com.orbyte.canvasstudio.ui.tutorial.EditorTutorialSession
import com.orbyte.canvasstudio.ui.tutorial.StudioTutorialEvent
import com.orbyte.canvasstudio.ui.tutorial.StudioTutorialProgressStore
import com.orbyte.canvasstudio.ui.tutorial.editorTutorialAnchor
import com.orbyte.canvasstudio.ui.tutorial.freshEditorTutorialState
import com.orbyte.canvasstudio.ui.tutorial.rememberTutorialFocusRegistry
import com.orbyte.canvasstudio.ui.tutorial.tutorialRuntimePolicy
import com.orbyte.canvasstudio.ui.tutorial.tutorialShouldExitMaskEditing
import com.orbyte.canvasstudio.ui.workspace.QuickBrushSizing
import com.orbyte.canvasstudio.ui.workspace.ColorWheelSelection
import com.orbyte.canvasstudio.ui.workspace.colorWheelSelection
import com.orbyte.canvasstudio.ui.workspace.QuickAccessAction
import com.orbyte.canvasstudio.ui.workspace.QuickAccessContext
import com.orbyte.canvasstudio.ui.workspace.QuickAccessProfile
import com.orbyte.canvasstudio.ui.workspace.BrushStrokePreview
import com.orbyte.canvasstudio.ui.workspace.defaultQuickAccessActions
import com.orbyte.canvasstudio.ui.workspace.matchingQuickAccessProfile
import com.orbyte.canvasstudio.ui.workspace.matchesWorkspaceAction
import com.orbyte.canvasstudio.ui.workspace.normalizedQuickAccessActions
import com.orbyte.canvasstudio.ui.workspace.quickAccessActionsFor
import com.orbyte.canvasstudio.ui.workspace.quickAccessItems
import com.orbyte.canvasstudio.ui.workspace.quickMenuTopLeft
import com.orbyte.canvasstudio.ui.workspace.reassignQuickAccessAction
import com.orbyte.canvasstudio.ui.workspace.shouldShowInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

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

@Composable
private fun RendererDebugDialog(
    mode: RendererMode,
    onModeSelected: (RendererMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Motor de renderizado") },
        text = {
            Column {
                RendererMode.entries.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onModeSelected(candidate) }
                            .semantics { selected = candidate == mode }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = candidate == mode, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(candidate.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

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
    ToolSpec(DrawingTool.EYEDROPPER, "Cuentagotas", Icons.Outlined.Colorize),
    ToolSpec(DrawingTool.HAND, "Mover", Icons.Outlined.PanTool),
)

private val shapeToolSpecs = listOf(
    ToolSpec(DrawingTool.RECTANGLE, "Rectángulo", Icons.Outlined.CropSquare),
    ToolSpec(DrawingTool.TRIANGLE, "Triángulo", Icons.Outlined.ChangeHistory),
    ToolSpec(DrawingTool.ELLIPSE, "Elipse", Icons.Outlined.RadioButtonUnchecked),
    ToolSpec(DrawingTool.LINE, "Línea", Icons.AutoMirrored.Outlined.ShowChart),
)

@Composable
fun EditorScreen(
    document: EditorDocument,
    onBackToGallery: () -> Unit,
    tutorialMode: Boolean = false,
    onTutorialFinish: () -> Unit = {},
    onTutorialExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var drawingView by remember { mutableStateOf<DrawingView?>(null) }
    var selectedTool by remember { mutableStateOf(DrawingTool.BRUSH) }
    var selectedDock by remember { mutableStateOf(DockTab.LAYERS) }
    val tutorialPreferences = remember {
        context.getSharedPreferences("canvas_studio_tutorial_progress", android.content.Context.MODE_PRIVATE)
    }
    val tutorialSession = remember(tutorialMode) {
        if (tutorialMode) {
            val saved = StudioTutorialProgressStore.load(tutorialPreferences)
            EditorTutorialSession(freshEditorTutorialState(saved), tutorialPreferences)
        }
        else null
    }
    val tutorialFocus = rememberTutorialFocusRegistry()
    var customBrushes by remember { mutableStateOf(BrushRepository.load(context)) }
    var selectedPreset by remember { mutableStateOf(premiumBrushes.first()) }
    var brushSettings by remember {
        mutableStateOf(
            BrushSettings(
                presetId = selectedPreset.id,
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
                tipAssetPath = selectedPreset.tipAssetPath,
                tipProfile = selectedPreset.tipProfile,
                grainProfile = selectedPreset.grainProfile,
                renderProfile = selectedPreset.renderProfile,
                dynamicsProfile = selectedPreset.dynamicsProfile,
                dualBrushProfile = selectedPreset.dualBrushProfile,
                kind = selectedPreset.kind,
                color = AndroidColor.rgb(37, 42, 49),
            ),
        )
    }
    var layerModels by remember { mutableStateOf<List<LayerUiModel>>(emptyList()) }
    var layerGroups by remember { mutableStateOf<List<LayerGroupUiModel>>(emptyList()) }
    var changeTick by remember { mutableIntStateOf(0) }
    var saveState by remember(document.id, tutorialMode) {
        mutableStateOf(if (tutorialMode) EditorSaveState.TEMPORARY else EditorSaveState.AUTO)
    }
    var zenMode by remember { mutableStateOf(false) }
    // Keep the painting area open by default in either orientation.
    var inspectorExpanded by remember(tutorialMode) { mutableStateOf(tutorialMode) }
    val workspacePreferences = remember {
        context.getSharedPreferences("canvas_studio_workspace", android.content.Context.MODE_PRIVATE)
    }
    var controlsOnRight by remember { mutableStateOf(workspacePreferences.getBoolean("brush_controls_on_right", false)) }
    var quickShapeEnabled by remember(tutorialMode) {
        mutableStateOf(!tutorialMode && workspacePreferences.getBoolean("quick_shape_enabled", false))
    }
    var editQuickShape by remember(tutorialMode) {
        mutableStateOf(!tutorialMode && workspacePreferences.getBoolean("quick_shape_edit_before_apply", false))
    }
    var preciseShapeTool by remember(document.id) { mutableStateOf<DrawingTool?>(null) }
    var preciseShapeReady by remember(document.id) { mutableStateOf(false) }
    var lastShapeTool by remember(document.id) { mutableStateOf(DrawingTool.RECTANGLE) }
    var recentColors by remember { mutableStateOf(RecentColorRepository.load(context)) }
    var lastRecordedColor by remember { mutableIntStateOf(brushSettings.color) }
    // Color-wheel and HSV sliders can emit dozens of changes per second. Persist the
    // final selected color only after the gesture settles instead of doing disk I/O per move.
    LaunchedEffect(brushSettings.color, tutorialMode) {
        if (tutorialMode || brushSettings.color == lastRecordedColor) return@LaunchedEffect
        delay(550)
        recentColors = RecentColorRepository.record(context, brushSettings.color)
        lastRecordedColor = brushSettings.color
    }
    var quickMenuOpen by remember { mutableStateOf(false) }
    var quickMenuAnchor by remember { mutableStateOf<Offset?>(null) }
    var quickMenuEditSlot by remember { mutableStateOf<Int?>(null) }
    var workspaceActionSearchOpen by remember { mutableStateOf(false) }
    var maskToDeleteId by remember(document.id) { mutableStateOf<String?>(null) }
    var quickMenuTouchHoldEnabled by remember {
        mutableStateOf(workspacePreferences.getBoolean("quick_access_touch_hold", true))
    }
    var quickAccessActions by remember(tutorialMode) {
        mutableStateOf(
            if (tutorialMode) defaultQuickAccessActions
            else normalizedQuickAccessActions(
                workspacePreferences.getString("quick_access_actions", null)
                    ?.split(',')
                    .orEmpty(),
            ),
        )
    }
    var zoomLabel by remember { mutableIntStateOf(100) }
    var rotationLabel by remember { mutableIntStateOf(0) }
    var gridVisible by remember { mutableStateOf(false) }
    var rulersVisible by remember { mutableStateOf(false) }
    var rulersUseCentimeters by remember { mutableStateOf(false) }
    var angleSnappingEnabled by remember { mutableStateOf(false) }
    var perspectiveSnappingEnabled by remember { mutableStateOf(false) }
    var symmetryMode by remember { mutableIntStateOf(0) }
    var guideMode by remember { mutableStateOf(GuideMode.NONE) }
    var perspectiveEditing by remember { mutableStateOf(false) }
    var selectionActive by remember { mutableStateOf(false) }
    var numericTransformOpen by remember(document.id) { mutableStateOf(false) }
    var selectionFeatherPx by remember { mutableFloatStateOf(0f) }
    var rendererMode by remember { mutableStateOf(RendererMode.CANVAS_BITMAP) }
    var showRendererDebug by remember { mutableStateOf(false) }
    var renameLayerOpen by remember { mutableStateOf(false) }
    var renameLayerText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var confirmPsdExport by remember(document.id) { mutableStateOf(false) }
    var brushLibraryOpen by remember { mutableStateOf(false) }
    var floatingBrushPaletteOpen by remember { mutableStateOf(false) }
    val brushWorkspaceState = rememberSaveableStateHolder()
    var brushDockResetTick by remember { mutableIntStateOf(0) }
    var favoriteBrushIds by remember { mutableStateOf(BrushRepository.loadFavorites(context)) }
    var recentBrushIds by remember { mutableStateOf(BrushRepository.loadRecents(context)) }
    var showExperimentalBrushes by remember(context) { mutableStateOf(BrushRepository.loadExperimentalVisibility(context)) }
    val availableBrushes = remember(customBrushes, showExperimentalBrushes) {
        premiumBrushes + (if (showExperimentalBrushes) experimentalBrushes else emptyList()) + customBrushes
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateCustomBrushes: (List<BrushPreset>) -> Unit = { brushes ->
        val normalized = brushes.takeLast(BrushRepository.MAX_CUSTOM_BRUSHES)
        customBrushes = normalized
        BrushRepository.save(context, normalized)
    }
    val applyBrushPreset: (BrushPreset) -> Unit = { preset ->
        brushDockResetTick += 1
        selectedPreset = preset
        recentBrushIds = BrushRepository.recordRecent(context, preset.id, recentBrushIds)
        brushSettings = brushSettings.copy(
            presetId = preset.id,
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
            tipAssetPath = preset.tipAssetPath,
            tipProfile = preset.tipProfile,
            grainProfile = preset.grainProfile,
            renderProfile = preset.renderProfile,
            dynamicsProfile = preset.dynamicsProfile,
            dualBrushProfile = preset.dualBrushProfile,
            kind = preset.kind,
        ).sanitized()
        selectedTool = DrawingTool.BRUSH
    }

    LaunchedEffect(changeTick) {
        if (changeTick == 0 || tutorialMode) return@LaunchedEffect
        saveState = EditorSaveState.SAVING
        delay(3_000)
        // Autosave waits for the ordered raster lane without rasterizing
        // pending strokes synchronously during a drawing gesture.
        drawingView?.saveProjectWhenIdle(
            projectId = document.id,
            title = document.title,
            dpi = document.dpi,
            includePreview = false,
        )
    }

    DisposableEffect(lifecycleOwner, document.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (tutorialMode) return@LifecycleEventObserver
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
            if (!tutorialMode) {
                drawingView?.saveProject(
                    projectId = document.id,
                    title = document.title,
                    dpi = document.dpi,
                    includePreview = false,
                )
            }
        }
    }

    val exportActions = rememberEditorExportActions(drawingView)

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

    val importPsdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                var layered: PsdLayerReader.Imported? = null
                var composite: Bitmap? = null
                try {
                    withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val input = resolver.openInputStream(uri) ?: error("No se pudo abrir el PSD.")
                        layered = input.use(PsdLayerReader::read)
                        if (layered == null) {
                            composite = resolver.openInputStream(uri)?.use(BasicPsdCodec::read)
                                ?: error("No se pudo abrir la imagen compuesta del PSD.")
                        }
                    }
                    val view = requireNotNull(drawingView) { "El editor ya no está disponible." }
                    val imported = layered
                    if (imported != null) {
                        val count = view.importLayeredPsd(imported)
                        val profileNote = if (imported.hasEmbeddedProfile) " · perfil ICC sin convertir" else ""
                        Toast.makeText(context, "PSD importado · $count capas$profileNote",
                            if (imported.hasEmbeddedProfile) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    } else {
                        composite?.let { view.importBitmapAsLayer(it, "PSD compuesto importado") }
                    }
                } catch (cancel: java.util.concurrent.CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "No se pudo importar PSD.", Toast.LENGTH_LONG).show()
                } finally {
                    layered?.close()
                    composite?.recycle()
                }
            }
        }
    }

    val exportBrushesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(BrushRepository.exportJsonWithAssets(customBrushes))
                    } ?: error("No se pudo abrir el archivo de destino.")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Biblioteca de pinceles exportada"
                        else result.exceptionOrNull()?.message ?: "No se pudo exportar.",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val importBrushesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo abrir el archivo.")
                    BrushRepository.importJsonWithAssets(context, raw)
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess { imported ->
                        val merged = (customBrushes + imported)
                            .distinctBy { it.name.lowercase(Locale.ROOT) to it.kind }
                            .takeLast(BrushRepository.MAX_CUSTOM_BRUSHES)
                        updateCustomBrushes(merged)
                    }
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "${result.getOrNull()?.size ?: 0} pinceles importados"
                        else result.exceptionOrNull()?.message ?: "No se pudo importar.",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val importBrushTipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching { BrushRepository.importTipAsset(context, uri) }
                withContext(Dispatchers.Main) {
                    result.onSuccess { path ->
                        val custom = selectedPreset.withSettings(brushSettings).copy(
                            id = "custom-${System.currentTimeMillis()}",
                            name = "${selectedPreset.name} · punta",
                            category = "Personalizados",
                            tipAssetPath = path,
                        )
                        updateCustomBrushes(customBrushes + custom)
                        applyBrushPreset(custom)
                    }
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Punta bitmap importada"
                        else result.exceptionOrNull()?.message ?: "No se pudo importar la punta.",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val activeLayer = layerModels.firstOrNull { it.isActive }
    tutorialSession?.also { session ->
        session.activeTool = selectedTool
        session.layersPanelActive = selectedDock == DockTab.LAYERS
        session.brushesPanelActive = selectedDock == DockTab.BRUSHES
        session.selectionActive = selectionActive
        session.quickMenuOpen = quickMenuOpen
        session.maskEditingActive = activeLayer?.editingMask ?: false
    }
    LaunchedEffect(
        tutorialSession?.state?.current,
        tutorialSession?.state?.attemptId,
        drawingView,
    ) {
        val state = tutorialSession?.state ?: return@LaunchedEffect
        val policy = tutorialRuntimePolicy(state)
        if (policy.closeTransientUi) {
            quickMenuOpen = false
            brushLibraryOpen = false
            floatingBrushPaletteOpen = false
            quickMenuEditSlot = null
            zenMode = false
        }
        if (policy.exitMaskEditing) drawingView?.finishMaskEditing()
        if (policy.clearSelection) drawingView?.deselect()
        if (policy.resetSymmetry) {
            symmetryMode = 0
            drawingView?.setVerticalSymmetry(false)
            drawingView?.setRadialSymmetry(1)
        }
    }
    LaunchedEffect(
        tutorialSession?.state?.current,
        tutorialSession?.state?.currentComplete,
        selectedTool,
        activeLayer?.editingMask,
        drawingView,
    ) {
        val incompatibleTool = selectedTool !in setOf(DrawingTool.BRUSH, DrawingTool.ERASER, DrawingTool.HAND)
        val tutorialFinishedMask = tutorialSession?.state?.let(::tutorialShouldExitMaskEditing) == true
        if (activeLayer?.editingMask == true && (incompatibleTool || tutorialFinishedMask)) {
            drawingView?.finishMaskEditing()
            quickMenuOpen = false
        }
    }
    val addLayerFromWorkspace: () -> Unit = {
        val beforeCount = layerModels.size
        drawingView?.addLayer()
        drawingView?.post {
            val created = layerModels.firstOrNull { it.isActive }
            if (layerModels.size > beforeCount && created != null) {
                tutorialSession?.observe(StudioTutorialEvent.LayerCreated(created.id))
            }
        }
    }
    val toggleActiveLayerVisibility: () -> Unit = {
        activeLayer?.let { layer ->
            drawingView?.toggleLayerVisibility(layer.id)
            tutorialSession?.observe(StudioTutorialEvent.LayerVisibilityChanged(layer.id, !layer.visible, true))
        }
    }
    val openActiveMaskWorkflow: () -> Unit = {
        activeLayer?.let { layer ->
            if (!layer.hasMask) {
                drawingView?.addMaskToActiveLayer()
                tutorialSession?.observe(StudioTutorialEvent.MaskCreated(layer.id))
            } else {
                drawingView?.setEditingLayerMask(layer.id, !layer.editingMask)
            }
            selectedTool = if (layer.maskInverted && !layer.editingMask) DrawingTool.ERASER else DrawingTool.BRUSH
            if (!tutorialMode && (!layer.hasMask || !layer.editingMask)) inspectorExpanded = false
        }
    }
    val setLayerClippingFromWorkspace: (String, Boolean) -> Unit = { id, clipping ->
        drawingView?.setLayerClipping(id, clipping)
        tutorialSession?.observe(StudioTutorialEvent.LayerClippingChanged(id, clipping))
    }
    val paintMask: (String, Boolean) -> Unit = { id, recover ->
        layerModels.firstOrNull { it.id == id && it.hasMask }?.let { layer ->
            if (!layer.editingMask) drawingView?.setEditingLayerMask(id, true)
            if (!layer.maskEnabled) drawingView?.toggleLayerMaskEnabled(id)
            drawingView?.setMaskOriginalPreview(false)
            selectedTool = if (recover != layer.maskInverted) DrawingTool.ERASER else DrawingTool.BRUSH
            if (!tutorialMode && !layer.editingMask) inspectorExpanded = false
        }
    }
    val performQuickAccessAction: (QuickAccessAction) -> Unit = { action ->
        when (action) {
            QuickAccessAction.BRUSH_LIBRARY -> {
                if (tutorialMode) brushLibraryOpen = true
                else {
                    zenMode = false
                    inspectorExpanded = false
                    floatingBrushPaletteOpen = true
                }
            }
            QuickAccessAction.EYEDROPPER -> selectedTool = DrawingTool.EYEDROPPER
            QuickAccessAction.ADD_LAYER -> addLayerFromWorkspace()
            QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> toggleActiveLayerVisibility()
            QuickAccessAction.DUPLICATE_LAYER -> drawingView?.duplicateActiveLayer()
            QuickAccessAction.TOGGLE_ALPHA_LOCK -> activeLayer?.let {
                drawingView?.setLayerAlphaLocked(it.id, !it.alphaLocked)
            }
            QuickAccessAction.MASK_WORKFLOW -> openActiveMaskWorkflow()
            QuickAccessAction.UNDO -> drawingView?.undo()
            QuickAccessAction.REDO -> drawingView?.redo()
            QuickAccessAction.FIT_CANVAS -> drawingView?.resetView()
            QuickAccessAction.TOGGLE_CANVAS_MODE -> zenMode = !zenMode
            QuickAccessAction.OPEN_LAYERS -> {
                zenMode = false
                floatingBrushPaletteOpen = false
                inspectorExpanded = true
                selectedDock = DockTab.LAYERS
            }
        }
        quickMenuOpen = false
        quickMenuAnchor = null
    }
    val toggleDock: (DockTab) -> Unit = { tab ->
        if (shouldShowInspector(inspectorExpanded, zenMode) && selectedDock == tab) {
            inspectorExpanded = false
        } else {
            floatingBrushPaletteOpen = false
            selectedDock = tab
            inspectorExpanded = true
            zenMode = false
        }
    }
    val toggleBrushPalette: () -> Unit = {
        if (tutorialMode) {
            brushLibraryOpen = true
        } else {
            floatingBrushPaletteOpen = zenMode || !floatingBrushPaletteOpen
            if (floatingBrushPaletteOpen) {
                inspectorExpanded = false
                zenMode = false
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    EditorTutorialFocusProvider(tutorialFocus, tutorialSession?.guide?.target) {
    Column(Modifier.fillMaxSize().background(StudioPalette.Background)) {
        EditorTopBar(
            document = document,
            zoom = zoomLabel,
            rotation = rotationLabel,
            saveState = saveState,
            tutorialMode = tutorialMode,
            canExportOpenRaster = layerModels.none { it.clipping },
            onBack = {
                if (tutorialMode) onTutorialExit() else {
                    drawingView?.saveProject(document.id, document.title, document.dpi)
                    onBackToGallery()
                }
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
                if (tutorialMode) {
                    tutorialSession?.observe(StudioTutorialEvent.ExportFormatSelected("PNG"))
                    tutorialSession?.observe(StudioTutorialEvent.ExportPreviewGenerated("PNG", document.width, document.height))
                } else {
                    exportActions.png(document.title)
                }
            },
            onExportOpenRaster = {
                drawingView?.commitPendingTransform()
                exportActions.openRaster(document.title)
            },
            onExportPsd = {
                if (layerGroups.isNotEmpty() || layerModels.any { it.hasMask }) {
                    confirmPsdExport = true
                } else {
                    drawingView?.commitPendingTransform()
                    exportActions.psd(document.title)
                }
            },
            onImportImage = { importImageLauncher.launch("image/*") },
            onImportPsd = { importPsdLauncher.launch(arrayOf("image/vnd.adobe.photoshop", "application/octet-stream")) },
            onSaveNow = {
                saveState = EditorSaveState.SAVING
                drawingView?.saveProject(document.id, document.title, document.dpi)
            },
            gridVisible = gridVisible,
            onToggleGrid = {
                gridVisible = !gridVisible
                drawingView?.setGridVisible(gridVisible)
            },
            rulersVisible = rulersVisible,
            onToggleRulers = {
                rulersVisible = !rulersVisible
                drawingView?.setRulersVisible(rulersVisible)
            },
            rulersUseCentimeters = rulersUseCentimeters,
            onToggleRulerUnits = {
                rulersUseCentimeters = !rulersUseCentimeters
                drawingView?.setRulerUnitCentimeters(rulersUseCentimeters)
            },
            angleSnappingEnabled = angleSnappingEnabled,
            onToggleAngleSnapping = {
                angleSnappingEnabled = !angleSnappingEnabled
                drawingView?.setAngleSnappingEnabled(angleSnappingEnabled)
            },
            quickShapeEnabled = quickShapeEnabled,
            onToggleQuickShape = {
                quickShapeEnabled = !quickShapeEnabled
                drawingView?.quickShapeEnabled = quickShapeEnabled
                if (!tutorialMode) {
                    workspacePreferences.edit()
                        .putBoolean("quick_shape_enabled", quickShapeEnabled)
                        .apply()
                }
            },
            editQuickShape = editQuickShape,
            onToggleEditQuickShape = {
                editQuickShape = !editQuickShape
                drawingView?.quickShapeEditBeforeApply = editQuickShape
                if (!tutorialMode) workspacePreferences.edit().putBoolean("quick_shape_edit_before_apply", editQuickShape).apply()
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
                tutorialSession?.observe(StudioTutorialEvent.SymmetryEnabled(symmetryMode != 0))
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
            layersVisible = shouldShowInspector(inspectorExpanded, zenMode) && selectedDock == DockTab.LAYERS,
            onToggleLayers = { toggleDock(DockTab.LAYERS) },
            colorVisible = shouldShowInspector(inspectorExpanded, zenMode) && selectedDock == DockTab.COLOR,
            currentColor = Color(brushSettings.color),
            onToggleColor = { toggleDock(DockTab.COLOR) },
            brushPaletteVisible = floatingBrushPaletteOpen && !zenMode,
            brushName = selectedPreset.name,
            onToggleBrushPalette = toggleBrushPalette,
            onShowHelp = { showHelp = true },
            onSearchActions = { workspaceActionSearchOpen = true },
            onShowRendererDebug = if (BuildConfig.DEBUG) ({ showRendererDebug = true }) else null,
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compactWorkspace = maxWidth < 1120.dp
            val brushWorkspaceWidth = (maxWidth * .34f).coerceIn(272.dp, 390.dp).coerceAtMost(maxWidth * .60f)
            val useSideControls = maxHeight >= 500.dp && !tutorialMode
            val sideOnRight = useSideControls && controlsOnRight
            val rail: @Composable () -> Unit = {
                EditorToolRail(
                    selectedTool = selectedTool,
                    preciseShapeTool = preciseShapeTool,
                    lastShapeTool = lastShapeTool,
                    compact = compactWorkspace,
                    showAllTools = tutorialMode,
                    onSelect = { kind ->
                        drawingView?.cancelPreciseShape()
                        selectedTool = kind
                    },
                    onSelectShape = { kind ->
                        if (tutorialMode && kind != DrawingTool.TRIANGLE) {
                            drawingView?.cancelPreciseShape()
                            selectedTool = kind
                            lastShapeTool = kind
                        } else if (drawingView?.startPreciseShape(kind) == true) {
                            lastShapeTool = kind
                        }
                    },
                    bottomContent = if (useSideControls) ({
                        BrushSideControls(
                            settings = brushSettings,
                            onSettingsChanged = { brushSettings = it },
                            onRight = controlsOnRight,
                            onSwitchSide = {
                                controlsOnRight = !controlsOnRight
                                workspacePreferences.edit().putBoolean("brush_controls_on_right", controlsOnRight).apply()
                            },
                        )
                    }) else null,
                )
            }
            Row(Modifier.fillMaxSize()) {
            if (!zenMode && !sideOnRight) rail()

            Column(Modifier.weight(1f).fillMaxHeight()) {
            activeLayer?.takeIf { it.editingMask }?.let { maskLayer ->
                MaskEditingControls(
                    layerId = maskLayer.id,
                    layerName = maskLayer.name,
                    recovering = (selectedTool == DrawingTool.ERASER) != maskLayer.maskInverted,
                    maskEnabled = maskLayer.maskEnabled,
                    maskStrength = maskLayer.maskStrength,
                    maskInverted = maskLayer.maskInverted,
                    showingOriginal = maskLayer.maskPreviewOriginal,
                    onPaint = { recover -> paintMask(maskLayer.id, recover) },
                    onStrengthChange = { drawingView?.setLayerMaskStrength(maskLayer.id, it) },
                    onInvert = {
                        drawingView?.toggleLayerMaskInverted(maskLayer.id)
                        selectedTool = when (selectedTool) {
                            DrawingTool.BRUSH -> DrawingTool.ERASER
                            DrawingTool.ERASER -> DrawingTool.BRUSH
                            else -> selectedTool
                        }
                    },
                    onCompare = { drawingView?.setMaskOriginalPreview(!maskLayer.maskPreviewOriginal) },
                    onDone = {
                        drawingView?.finishMaskEditing()
                        selectedTool = DrawingTool.BRUSH
                    },
                )
            }
            preciseShapeTool?.let { kind ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(StudioPalette.Surface)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .testTag("precise_shape_controls"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(shapeToolSpecs.first { it.tool == kind }.label,
                            color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                        Text(if (preciseShapeReady) "Ajusta los bordes o arrastra el centro para mover"
                            else "Arrastra en el lienzo para crear la figura",
                            color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { drawingView?.cancelPreciseShape() }) { Text("Cancelar") }
                    Button(enabled = preciseShapeReady, onClick = { drawingView?.applyPreciseShape() }) {
                        Text("Aplicar")
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().testTag("editor_artwork_viewport")) {
                AndroidView(
                    factory = { viewContext ->
                        InkDrawingContainer(viewContext).also { container ->
                            container.drawingView.apply {
                            configureDocument(document.width, document.height)
                            val loaded = if (!tutorialMode && document.isLocal) loadProject(document.id) else false
                            if (!loaded) {
                                seedDemoArtwork(document.preview?.name)
                            } else {
                                // Capture before AndroidView's first update pass can apply the
                                // Compose defaults, then mirror the persisted values back to state.
                                val restoredGuideMode = currentGuideMode()
                                val restoredPerspectiveEditing = isPerspectiveEditing()
                                val restoredRulersVisible = areRulersVisible()
                                val restoredRulersUseCentimeters = areRulersUsingCentimeters()
                                val restoredAngleSnapping = isAngleSnappingEnabled()
                                val restoredPerspectiveSnapping = isPerspectiveSnappingEnabled()
                                post {
                                    guideMode = restoredGuideMode
                                    perspectiveEditing = restoredPerspectiveEditing
                                    rulersVisible = restoredRulersVisible
                                    rulersUseCentimeters = restoredRulersUseCentimeters
                                    angleSnappingEnabled = restoredAngleSnapping
                                    perspectiveSnappingEnabled = restoredPerspectiveSnapping
                                }
                            }
                            tool = selectedTool
                            brushSettings = brushSettings
                            quickShapeEnabled = quickShapeEnabled
                            quickShapeEditBeforeApply = editQuickShape
                            onLayersChanged = { layerModels = it }
                            onLayerGroupsChanged = { layerGroups = it }
                            onDocumentChanged = { changeTick++ }
                            onColorPicked = { color ->
                                val previous = brushSettings.color
                                brushSettings = brushSettings.copy(color = color)
                                selectedDock = DockTab.COLOR
                                tutorialSession?.observe(StudioTutorialEvent.ColorSampled(color.toLong(), previous.toLong()))
                            }
                            onZoomChanged = { zoomLabel = it }
                            onRotationChanged = { rotationLabel = it }
                            onToolShortcut = { selectedTool = it }
                            onBrushSettingsShortcut = { brushSettings = it }
                            onSaveShortcut = {
                                saveState = EditorSaveState.SAVING
                                saveProject(document.id, document.title, document.dpi)
                            }
                            onTogglePanelsShortcut = { zenMode = !zenMode }
                            onProjectSaved = { success ->
                                saveState = if (success) EditorSaveState.SAVED else EditorSaveState.FAILED
                            }
                            onEngineMessage = { message ->
                                Toast.makeText(viewContext, message, Toast.LENGTH_LONG).show()
                            }
                            onPreciseShapeChanged = { kind, ready ->
                                preciseShapeTool = kind
                                preciseShapeReady = ready
                            }
                            onSelectionChanged = {
                                selectionActive = it
                                if (!it) selectionFeatherPx = 0f
                            }
                            onInteraction = { tutorialSession?.observeDrawing(it) }
                            onQuickMenuRequested = { x, y ->
                                quickMenuAnchor = Offset(x, y)
                                quickMenuOpen = true
                            }
                            onQuickMenuFlickSelected = { slot ->
                                quickAccessActions.getOrNull(slot)?.let(performQuickAccessAction)
                            }
                            quickMenuTouchHoldEnabled = tutorialMode || quickMenuTouchHoldEnabled
                            setRendererMode(rendererMode)
                            setGridVisible(gridVisible)
                            setRulersVisible(rulersVisible)
                            setDocumentDpi(document.dpi)
                            setRulerUnitCentimeters(rulersUseCentimeters)
                            setAngleSnappingEnabled(angleSnappingEnabled)
                            setPerspectiveSnappingEnabled(perspectiveSnappingEnabled)
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
                        }
                    },
                    update = { container ->
                        val view = container.drawingView
                        view.tool = selectedTool
                        view.brushSettings = brushSettings
                        view.quickShapeEnabled = quickShapeEnabled
                        view.quickShapeEditBeforeApply = editQuickShape
                        view.setGridVisible(gridVisible)
                        view.setRulersVisible(rulersVisible)
                        view.setDocumentDpi(document.dpi)
                        view.setRulerUnitCentimeters(rulersUseCentimeters)
                        view.setAngleSnappingEnabled(angleSnappingEnabled)
                        view.setPerspectiveSnappingEnabled(perspectiveSnappingEnabled)
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
                        view.setRendererMode(rendererMode)
                        view.onQuickMenuRequested = { x, y ->
                            quickMenuAnchor = Offset(x, y)
                            quickMenuOpen = true
                        }
                        view.onQuickMenuFlickSelected = { slot ->
                            quickAccessActions.getOrNull(slot)?.let(performQuickAccessAction)
                        }
                        view.quickMenuTouchHoldEnabled = tutorialMode || quickMenuTouchHoldEnabled
                    },
                    modifier = Modifier.fillMaxSize().editorTutorialAnchor("canvas"),
                )

                if (tutorialSession?.guide?.target == "quick_menu_gesture") {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(150.dp)
                            .editorTutorialAnchor("quick_menu_gesture")
                            .semantics { contentDescription = "Zona para abrir Acceso rápido manteniendo un dedo" },
                        color = StudioPalette.Accent.copy(alpha = .12f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(2.dp, StudioPalette.Accent),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.Gesture, null, tint = StudioPalette.Accent, modifier = Modifier.size(34.dp))
                            Text("Mantén aquí", color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                            Text("y desliza", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                QuickMenuTrigger(
                    onOpen = {
                        quickMenuAnchor = null
                        quickMenuOpen = true
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )

                if (quickMenuOpen) {
                    RadialQuickMenu(
                        actions = quickAccessActions,
                        activeLayer = activeLayer,
                        selectedTool = selectedTool,
                        anchor = quickMenuAnchor,
                        onDismiss = {
                            quickMenuOpen = false
                            quickMenuAnchor = null
                        },
                        onAction = performQuickAccessAction,
                        canEdit = !tutorialMode,
                        onEditSlot = { slot ->
                            if (tutorialMode) {
                                Toast.makeText(context, "Personaliza la rueda fuera del tutorial.", Toast.LENGTH_SHORT).show()
                            } else quickMenuEditSlot = slot
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (selectionActive && !zenMode) {
                    SelectionToolbar(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                        onTransform = { selectedTool = DrawingTool.TRANSFORM },
                        onPreciseTransform = { numericTransformOpen = true },
                        onExpand = { drawingView?.adjustSelectionBounds(16f) },
                        onContract = { drawingView?.adjustSelectionBounds(-16f) },
                        onInvert = { drawingView?.invertSelection() },
                        featherPx = selectionFeatherPx,
                        onCycleFeather = {
                            selectionFeatherPx = when (selectionFeatherPx.toInt()) {
                                0 -> 8f
                                8 -> 16f
                                16 -> 32f
                                32 -> 64f
                                else -> 0f
                            }
                            drawingView?.setSelectionFeather(selectionFeatherPx)
                        },
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
                        snapping = perspectiveSnappingEnabled,
                        onToggleEditing = {
                            perspectiveEditing = !perspectiveEditing
                            drawingView?.setPerspectiveEditing(perspectiveEditing)
                        },
                        onToggleSnapping = {
                            perspectiveSnappingEnabled = !perspectiveSnappingEnabled
                            drawingView?.setPerspectiveSnappingEnabled(perspectiveSnappingEnabled)
                        },
                        onReset = { drawingView?.resetPerspectiveGuides() },
                    )
                }
            }

            if (!zenMode && !useSideControls) {
                HorizontalDivider(color = StudioPalette.Border)
                Box(
                    Modifier.fillMaxWidth().background(StudioPalette.Surface)
                        .testTag("editor_brush_bar").padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickDial(
                            modifier = Modifier.weight(1f),
                            label = "Tamaño",
                            progress = QuickBrushSizing.sizeProgress(brushSettings.sizePx),
                            value = "${brushSettings.sizePx.toInt()} px",
                            onProgressChanged = { progress ->
                                val before = brushSettings.sizePx
                                val after = QuickBrushSizing.sizeFromProgress(progress)
                                brushSettings = brushSettings.copy(sizePx = after)
                                if (abs(after - before) >= 0.1f) {
                                    tutorialSession?.observe(
                                        StudioTutorialEvent.BrushParameterChanged("size", before, after),
                                    )
                                }
                            },
                            onDecrease = {
                                val before = brushSettings.sizePx
                                brushSettings = brushSettings.copy(sizePx = (before / 1.16f).coerceAtLeast(2f))
                                tutorialSession?.observe(StudioTutorialEvent.BrushParameterChanged("size", before, brushSettings.sizePx))
                            },
                            onIncrease = {
                                val before = brushSettings.sizePx
                                brushSettings = brushSettings.copy(sizePx = (before * 1.16f).coerceAtMost(180f))
                                tutorialSession?.observe(StudioTutorialEvent.BrushParameterChanged("size", before, brushSettings.sizePx))
                            },
                        )
                        QuickDial(
                            modifier = Modifier.weight(1f),
                            label = "Opacidad",
                            progress = QuickBrushSizing.opacityProgress(brushSettings.opacity),
                            value = "${(brushSettings.opacity * 100).toInt()}%",
                            onProgressChanged = { progress ->
                                brushSettings = brushSettings.copy(
                                    opacity = QuickBrushSizing.opacityFromProgress(progress),
                                )
                            },
                            onDecrease = { brushSettings = brushSettings.copy(opacity = (brushSettings.opacity - .1f).coerceAtLeast(.05f)) },
                            onIncrease = { brushSettings = brushSettings.copy(opacity = (brushSettings.opacity + .1f).coerceAtMost(1f)) },
                        )
                    }
                }
            }
            }

            if (!zenMode && sideOnRight) rail()

            if (floatingBrushPaletteOpen && !zenMode) {
                brushWorkspaceState.SaveableStateProvider("brush_workspace") {
                    BrushWorkspacePanel(
                        presets = availableBrushes,
                        activePreset = selectedPreset,
                        settings = brushSettings,
                        favorites = favoriteBrushIds,
                        recents = recentBrushIds,
                        showExperimental = showExperimentalBrushes,
                        onShowExperimental = {
                            showExperimentalBrushes = it
                            BrushRepository.saveExperimentalVisibility(context, it)
                        },
                        onSelect = applyBrushPreset,
                        onFavorite = { preset ->
                            favoriteBrushIds = if (preset.id in favoriteBrushIds) favoriteBrushIds - preset.id
                            else favoriteBrushIds + preset.id
                            BrushRepository.saveFavorites(context, favoriteBrushIds)
                        },
                        onAdjust = { brushSettings = it.sanitized() },
                        onSaveCopy = {
                            val copy = selectedPreset.withSettings(brushSettings).copy(
                                id = "custom-${java.util.UUID.randomUUID()}",
                                name = "${selectedPreset.name.take(42)} copia",
                                category = "Personalizados",
                            )
                            updateCustomBrushes(customBrushes + copy)
                            applyBrushPreset(copy)
                            Toast.makeText(context, "Copia guardada", Toast.LENGTH_SHORT).show()
                        },
                        onReset = { applyBrushPreset(selectedPreset) },
                        onAdvanced = { brushLibraryOpen = true },
                        onClose = { floatingBrushPaletteOpen = false },
                        modifier = Modifier.width(brushWorkspaceWidth).fillMaxHeight(),
                    )
                }
            } else if (shouldShowInspector(inspectorExpanded, zenMode)) {
                RightDock(
                    dockWidth = if (compactWorkspace) 286.dp else 320.dp,
                    selectedTab = selectedDock,
                    onSelectTab = {
                        if (it == DockTab.BRUSHES && !tutorialMode) {
                            inspectorExpanded = false
                            floatingBrushPaletteOpen = true
                        } else {
                            selectedDock = it
                            if (it == DockTab.BRUSHES) brushDockResetTick += 1
                        }
                    },
                    brushDockResetTick = brushDockResetTick,
                    selectedPreset = selectedPreset,
                    brushes = availableBrushes,
                    brushSettings = brushSettings,
                    recentColors = recentColors,
                    onClearRecentColors = {
                        RecentColorRepository.clear(context)
                        recentColors = emptyList()
                    },
                    onPresetSelected = applyBrushPreset,
                    onBrushSettingsChanged = { updated ->
                        val before = brushSettings.sizePx
                        brushSettings = updated
                        if (abs(updated.sizePx - before) / before.coerceAtLeast(1f) >= .15f) {
                            tutorialSession?.observe(StudioTutorialEvent.BrushParameterChanged("size", before, updated.sizePx))
                        }
                    },
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
                            tipAssetPath = brushSettings.tipAssetPath,
                            tipProfile = brushSettings.tipProfile,
                            grainProfile = brushSettings.grainProfile,
                            renderProfile = brushSettings.renderProfile,
                            dynamicsProfile = brushSettings.dynamicsProfile,
                            dualBrushProfile = brushSettings.dualBrushProfile,
                        )
                        updateCustomBrushes(customBrushes + custom)
                        selectedPreset = custom
                    },
                    layers = layerModels,
                    groups = layerGroups,
                    onSelectLayer = { drawingView?.setActiveLayer(it) },
                    onToggleLayerSelection = { drawingView?.toggleLayerSelection(it) },
                    onToggleVisibility = { id ->
                        val before = layerModels.firstOrNull { it.id == id }?.visible ?: true
                        drawingView?.toggleLayerVisibility(id)
                        tutorialSession?.observe(StudioTutorialEvent.LayerVisibilityChanged(id, !before, true))
                    },
                    onLayerOpacity = { id, opacity -> drawingView?.setLayerOpacity(id, opacity) },
                    onLayerBlendMode = { id, mode -> drawingView?.setLayerBlendMode(id, mode) },
                    onLayerAlphaLock = { id, locked -> drawingView?.setLayerAlphaLocked(id, locked) },
                    onLayerClipping = setLayerClippingFromWorkspace,
                    onCreateGroup = { drawingView?.createGroupFromActiveLayer() },
                    onUngroupLayer = { drawingView?.ungroupActiveLayer() },
                    onToggleGroupVisibility = { drawingView?.toggleGroupVisibility(it) },
                    onToggleGroupCollapsed = { drawingView?.toggleGroupCollapsed(it) },
                    onGroupOpacity = { id, opacity -> drawingView?.setGroupOpacity(id, opacity) },
                    onAddMask = openActiveMaskWorkflow,
                    onEditMask = { id, editing ->
                        drawingView?.setEditingLayerMask(id, editing)
                        if (editing || selectedTool == DrawingTool.ERASER) {
                            val inverted = layerModels.firstOrNull { it.id == id }?.maskInverted == true
                            selectedTool = if (editing && inverted) DrawingTool.ERASER else DrawingTool.BRUSH
                        }
                        if (editing && !tutorialMode) inspectorExpanded = false
                    },
                    onHideWithMask = { id -> paintMask(id, false) },
                    onRevealWithMask = { id -> paintMask(id, true) },
                    onToggleMask = { drawingView?.toggleLayerMaskEnabled(it) },
                    onDeleteMask = { maskToDeleteId = activeLayer?.id },
                    onAddLayer = addLayerFromWorkspace,
                    onDuplicateLayer = { drawingView?.duplicateActiveLayer() },
                    onDeleteLayer = { drawingView?.deleteActiveLayer() },
                    onMoveLayerUp = {
                        val id = layerModels.firstOrNull { it.isActive }?.id.orEmpty()
                        drawingView?.moveActiveLayer(true)
                        tutorialSession?.observe(StudioTutorialEvent.LayerReordered(id, true))
                    },
                    onMoveLayerDown = {
                        val id = layerModels.firstOrNull { it.isActive }?.id.orEmpty()
                        drawingView?.moveActiveLayer(false)
                        tutorialSession?.observe(StudioTutorialEvent.LayerReordered(id, true))
                    },
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
    }
    tutorialSession?.let { session ->
        EditorTutorialOverlay(session, tutorialFocus, onTutorialFinish, onTutorialExit)
    }
    }

    if (workspaceActionSearchOpen || quickMenuEditSlot != null) {
        val slot = quickMenuEditSlot
        var actionQuery by remember(slot) { mutableStateOf("") }
        val dismissActions = {
            quickMenuEditSlot = null
            workspaceActionSearchOpen = false
        }
        val matchingActions = remember(actionQuery) {
            QuickAccessAction.entries.filter { action ->
                matchesWorkspaceAction(actionQuery, quickAccessShortLabel(action), quickAccessDescription(action))
            }
        }
        AlertDialog(
            onDismissRequest = dismissActions,
            title = { Text(if (slot == null) "Buscar acciones" else "Configurar acceso ${slot + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = actionQuery,
                        onValueChange = { actionQuery = it.take(80) },
                        label = { Text("Buscar acción") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (slot != null) {
                        Text("Perfiles", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            QuickAccessProfile.entries.filterNot { it == QuickAccessProfile.CUSTOM }.forEach { profile ->
                                FilterChip(
                                    selected = matchingQuickAccessProfile(quickAccessActions) == profile,
                                    onClick = {
                                        val updated = quickAccessActionsFor(profile)
                                        quickAccessActions = updated
                                        workspacePreferences.edit()
                                            .putString("quick_access_actions", updated.joinToString(",") { it.name })
                                            .apply()
                                    },
                                    label = { Text(profile.label) },
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mantener un dedo en el lienzo", color = StudioPalette.Text)
                                Text("Abre la rueda en el punto de contacto; el botón estrella siempre queda disponible.", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = quickMenuTouchHoldEnabled,
                                onCheckedChange = { enabled ->
                                    quickMenuTouchHoldEnabled = enabled
                                    workspacePreferences.edit().putBoolean("quick_access_touch_hold", enabled).apply()
                                },
                            )
                        }
                    }
                    if (matchingActions.isEmpty()) Text("No hay acciones que coincidan.", color = StudioPalette.TextMuted)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 210.dp),
                        modifier = Modifier.heightIn(max = 340.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(matchingActions, key = { it.name }) { action ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (slot == null) {
                                            performQuickAccessAction(action)
                                        } else {
                                            val updated = reassignQuickAccessAction(quickAccessActions, slot, action)
                                            quickAccessActions = updated
                                            workspacePreferences.edit()
                                                .putString("quick_access_actions", updated.joinToString(",") { it.name })
                                                .apply()
                                        }
                                        dismissActions()
                                    },
                                color = if (slot != null && quickAccessActions.getOrNull(slot) == action) StudioPalette.AccentSoft else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(quickAccessIcon(action, activeLayer), null, tint = StudioPalette.TextMuted)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(quickAccessShortLabel(action), color = StudioPalette.Text)
                                        Text(
                                            quickAccessDescription(action),
                                            color = StudioPalette.TextMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = dismissActions) { Text("Cerrar") }
            },
        )
    }

    if (brushLibraryOpen) {
        ExpandedBrushLibraryDialog(
            brushes = availableBrushes,
            customBrushIds = customBrushes.mapTo(mutableSetOf(), BrushPreset::id),
            favoriteBrushIds = favoriteBrushIds,
            recentBrushIds = recentBrushIds,
            selectedPreset = selectedPreset,
            settings = brushSettings,
            onPresetSelected = applyBrushPreset,
            onSettingsChanged = { brushSettings = it.sanitized() },
            onToggleFavorite = { preset ->
                favoriteBrushIds = if (preset.id in favoriteBrushIds) {
                    favoriteBrushIds - preset.id
                } else {
                    favoriteBrushIds + preset.id
                }
                BrushRepository.saveFavorites(context, favoriteBrushIds)
            },
            onDuplicate = { preset ->
                val duplicate = preset.copy(
                    id = "custom-${System.currentTimeMillis()}",
                    name = "${preset.name} copia",
                    category = "Personalizados",
                )
                updateCustomBrushes(customBrushes + duplicate)
                applyBrushPreset(duplicate)
            },
            onRename = { preset, name ->
                val renamed = preset.copy(name = name)
                updateCustomBrushes(customBrushes.map { if (it.id == preset.id) renamed else it })
                if (selectedPreset.id == preset.id) selectedPreset = renamed
            },
            onDelete = { preset ->
                updateCustomBrushes(customBrushes.filterNot { it.id == preset.id })
                favoriteBrushIds = favoriteBrushIds - preset.id
                BrushRepository.saveFavorites(context, favoriteBrushIds)
                if (selectedPreset.id == preset.id) applyBrushPreset(premiumBrushes.first())
            },
            onImport = { importBrushesLauncher.launch("application/json") },
            onImportTip = { importBrushTipLauncher.launch("image/*") },
            onExport = { exportBrushesLauncher.launch("canvas-studio-brushes.json") },
            onDismiss = { brushLibraryOpen = false },
        )
    }

    if (numericTransformOpen && selectionActive) {
        NumericSelectionTransformDialog(
            onDismiss = { numericTransformOpen = false },
            onApply = { x, y, scale, angle ->
                if (drawingView?.applyNumericSelectionTransform(x, y, scale, angle) == true) {
                    numericTransformOpen = false
                }
            },
        )
    }

    if (confirmPsdExport) {
        AlertDialog(
            onDismissRequest = { confirmPsdExport = false },
            title = { Text("Exportar PSD por capas") },
            text = { Text("Las capas raster siguen editables. Las máscaras visibles se aplican a sus píxeles y las carpetas se aplanan. Tu proyecto original no cambia.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPsdExport = false
                    drawingView?.commitPendingTransform()
                    exportActions.psd(document.title)
                }) { Text("Exportar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPsdExport = false }) { Text("Cancelar") }
            },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Ayuda rápida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("S Pen: presión e inclinación controlan el trazo; el botón lateral activa el borrador temporal.")
                    Text("Vista: usa dos dedos para mover, ampliar y girar. Restablecer vista vuelve al encuadre inicial.")
                    Text("QuickShape: dibuja una línea o cierra un círculo, óvalo, rectángulo o triángulo. Mantén el lápiz apoyado hasta que la figura se ajuste y luego levántalo.")
                    Text("Toca Rectángulo en la barra lateral para elegir una figura. Arrastra para crearla, ajusta sus puntos y pulsa Aplicar.")
                    Text("Pinceles con textura: la guía bajo la punta es rápida; al soltar se completa el grano y la mezcla con el dibujo.")
                    Text("Teclado: B pincel, E borrador, H mano, Ctrl+Z deshacer. El perfil puede cambiarse en Ajustes.")
                    Text("La cuadrícula solo es una guía visual y nunca se exporta.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Entendido") }
            },
        )
    }

    maskToDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { maskToDeleteId = null },
            title = { Text("Quitar máscara") },
            text = { Text("Se descarta la máscara y vuelve a verse el dibujo original de la capa.") },
            confirmButton = {
                TextButton(onClick = {
                    if (activeLayer?.id == id) {
                        drawingView?.deleteActiveLayerMask()
                        selectedTool = DrawingTool.BRUSH
                    }
                    maskToDeleteId = null
                }) { Text("Quitar máscara") }
            },
            dismissButton = { TextButton(onClick = { maskToDeleteId = null }) { Text("Cancelar") } },
        )
    }

    if (BuildConfig.DEBUG && showRendererDebug) {
        RendererDebugDialog(
            mode = rendererMode,
            onModeSelected = { selected ->
                showRendererDebug = false
                rendererMode = selected
                val available = drawingView?.setRendererMode(selected) ?: true
                if (!available && selected == RendererMode.VULKAN_EXPERIMENTAL) {
                    Toast.makeText(
                        context,
                        "Vulkan no disponible; los trazos usarán Canvas/Bitmap.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { showRendererDebug = false },
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
    saveState: EditorSaveState,
    tutorialMode: Boolean,
    canExportOpenRaster: Boolean,
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
    onExportPsd: () -> Unit,
    onImportImage: () -> Unit,
    onImportPsd: () -> Unit,
    onSaveNow: () -> Unit,
    gridVisible: Boolean,
    onToggleGrid: () -> Unit,
    rulersVisible: Boolean,
    onToggleRulers: () -> Unit,
    rulersUseCentimeters: Boolean,
    onToggleRulerUnits: () -> Unit,
    angleSnappingEnabled: Boolean,
    onToggleAngleSnapping: () -> Unit,
    quickShapeEnabled: Boolean,
    onToggleQuickShape: () -> Unit,
    editQuickShape: Boolean,
    onToggleEditQuickShape: () -> Unit,
    symmetryLabel: String,
    onCycleSymmetry: () -> Unit,
    guideLabel: String,
    onCycleGuides: () -> Unit,
    zenMode: Boolean,
    onToggleZen: () -> Unit,
    layersVisible: Boolean,
    onToggleLayers: () -> Unit,
    colorVisible: Boolean,
    currentColor: Color,
    onToggleColor: () -> Unit,
    brushPaletteVisible: Boolean,
    brushName: String,
    onToggleBrushPalette: () -> Unit,
    onShowHelp: () -> Unit,
    onSearchActions: () -> Unit,
    onShowRendererDebug: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var guidesExpanded by remember { mutableStateOf(false) }
    var exportExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border),
    ) {
        val narrow = maxWidth < 760.dp
        val wideDetails = maxWidth >= 1280.dp
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = StudioPalette.Text) }
            Column(Modifier.width(if (wideDetails) 212.dp else if (narrow) 104.dp else 118.dp)) {
                Text(
                    document.title,
                    color = StudioPalette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (wideDetails) {
                    Text(
                        "${document.width} × ${document.height}px · ${document.dpi} dpi",
                        color = StudioPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    SaveStatus(saveState, onSaveNow)
                }
            }
            TopIconButton(Icons.AutoMirrored.Outlined.Undo, "Deshacer", "undo", onUndo)
            TopIconButton(Icons.AutoMirrored.Outlined.Redo, "Rehacer", "redo", onRedo)
            Spacer(Modifier.width(6.dp))
            if (narrow) {
                TopIconButton(
                    Icons.Outlined.CenterFocusStrong,
                    "Restablecer vista",
                    "view_reset",
                    onResetView,
                )
            } else Surface(
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
                    if (wideDetails) {
                        IconButton(onClick = onRotateLeft, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.RotateLeft, "Rotar a la izquierda", tint = StudioPalette.TextMuted)
                        }
                        Text("${rotation}°", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = onRotateRight, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.RotateRight, "Rotar a la derecha", tint = StudioPalette.TextMuted)
                        }
                    }
                    IconButton(onClick = onResetView, modifier = Modifier.size(38.dp).editorTutorialAnchor("view_reset")) {
                        Icon(Icons.Outlined.CenterFocusStrong, "Restablecer vista", tint = StudioPalette.TextMuted)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (wideDetails) {
                Box(Modifier.width(110.dp).padding(end = 8.dp)) {
                    SaveStatus(saveState, onSaveNow)
                }
            }
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .background(if (brushPaletteVisible) StudioPalette.SurfaceHover else Color.Transparent, RoundedCornerShape(10.dp))
                    .semantics {
                        contentDescription = if (brushPaletteVisible) "Cerrar ajustes de pincel" else "Ajustes de pincel"
                        stateDescription = brushName
                        selected = brushPaletteVisible
                    }
                    .clickable(role = Role.Button, onClick = onToggleBrushPalette)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Brush, null, tint = if (brushPaletteVisible) StudioPalette.Accent else StudioPalette.Text)
                if (wideDetails) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        brushName,
                        modifier = Modifier.widthIn(max = 92.dp),
                        color = StudioPalette.Text,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onToggleLayers,
                modifier = Modifier.size(48.dp).semantics { selected = layersVisible },
            ) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = if (layersVisible) "Cerrar panel de capas" else "Abrir panel de capas",
                    tint = if (layersVisible) StudioPalette.Accent else StudioPalette.Text,
                    modifier = Modifier.size(23.dp),
                )
            }
            IconButton(
                onClick = onToggleColor,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (colorVisible) "Cerrar panel de color" else "Abrir panel de color"
                    selected = colorVisible
                },
            ) {
                Box(
                    Modifier.size(26.dp)
                        .background(currentColor, CircleShape)
                        .border(2.dp, if (colorVisible) StudioPalette.Accent else StudioPalette.TextMuted, CircleShape),
                )
            }
            TopIconButton(Icons.Outlined.Fullscreen, if (zenMode) "Mostrar paneles" else "Modo lienzo", null, onToggleZen)
            Box {
                Button(
                    onClick = { if (tutorialMode) onExport() else exportExpanded = true },
                    modifier = Modifier.testTag("editor_export_menu").editorTutorialAnchor("export_png"),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioPalette.Accent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = if (wideDetails) 14.dp else 10.dp, vertical = 9.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, if (wideDetails) null else "Exportar", modifier = Modifier.size(18.dp))
                    if (wideDetails) {
                        Spacer(Modifier.width(7.dp))
                        Text("Exportar")
                    }
                }
                DropdownMenu(expanded = exportExpanded, onDismissRequest = { exportExpanded = false }) {
                    Text(
                        "${document.width} × ${document.height} px",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioPalette.TextMuted,
                    )
                    ExportChoice("PNG", "Una sola imagen") {
                        exportExpanded = false
                        onExport()
                    }
                    ExportChoice("PSD por capas", "Capas raster · máscaras aplicadas") {
                        exportExpanded = false
                        onExportPsd()
                    }
                    ExportChoice(
                        "OpenRaster (.ora)",
                        if (canExportOpenRaster) "Capas raster · máscaras aplicadas" else "No disponible con capas recortadas",
                        enabled = canExportOpenRaster,
                    ) {
                        exportExpanded = false
                        onExportOpenRaster()
                    }
                }
            }
            Box {
                IconButton(onClick = { guidesExpanded = false; menuExpanded = true }, modifier = Modifier.editorTutorialAnchor("more_menu")) {
                    Icon(Icons.Outlined.MoreHoriz, "Más opciones", tint = StudioPalette.TextMuted)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (!guidesExpanded) {
                    DropdownMenuItem(
                        text = { Text("Buscar acciones") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        onClick = { menuExpanded = false; onSearchActions() },
                    )
                    DropdownMenuItem(
                        text = { Text("Importar imagen como capa") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = { menuExpanded = false; onImportImage() },
                    )
                    DropdownMenuItem(
                        text = { Text("Importar PSD") },
                        leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                        onClick = { menuExpanded = false; onImportPsd() },
                    )
                    DropdownMenuItem(
                        text = { Text("Guardar ahora") },
                        leadingIcon = { Icon(Icons.Outlined.Save, null) },
                        onClick = { menuExpanded = false; onSaveNow() },
                    )
                    DropdownMenuItem(
                        text = { Text("Ayuda rápida") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
                        onClick = { menuExpanded = false; onShowHelp() },
                    )
                    onShowRendererDebug?.let { showDebug ->
                        DropdownMenuItem(
                            text = { Text("Motor de renderizado · diagnóstico") },
                            onClick = { menuExpanded = false; showDebug() },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(if (quickShapeEnabled) "Desactivar QuickShape" else "Activar QuickShape")
                        },
                        leadingIcon = { Icon(Icons.Outlined.AutoFixNormal, null) },
                        onClick = { menuExpanded = false; onToggleQuickShape() },
                    )
                    if (quickShapeEnabled) DropdownMenuItem(
                        text = { Text(if (editQuickShape) "QuickShape: aplicar al soltar" else "QuickShape: ajustar antes de aplicar") },
                        leadingIcon = { Icon(Icons.Outlined.Transform, null) },
                        onClick = { menuExpanded = false; onToggleEditQuickShape() },
                    )
                    DropdownMenuItem(
                        text = { Text("Guías y asistencia") },
                        leadingIcon = { Icon(Icons.Outlined.CropSquare, null) },
                        onClick = { guidesExpanded = true },
                    )
                    if (!wideDetails) {
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
                    } else {
                    DropdownMenuItem(
                        text = { Text("Volver a más opciones") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                        onClick = { guidesExpanded = false },
                    )
                    HorizontalDivider(color = StudioPalette.Border)
                    DropdownMenuItem(
                        text = { Text(if (gridVisible) "Ocultar cuadrícula" else "Mostrar cuadrícula") },
                        leadingIcon = { Icon(Icons.Outlined.CropSquare, null) },
                        onClick = { menuExpanded = false; onToggleGrid() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (rulersVisible) "Ocultar reglas" else "Mostrar reglas") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ShowChart, null) },
                        onClick = { menuExpanded = false; onToggleRulers() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (rulersUseCentimeters) "Reglas en pixeles" else "Reglas en centimetros") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ShowChart, null) },
                        onClick = { menuExpanded = false; onToggleRulerUnits() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (angleSnappingEnabled) "Desactivar ajuste de ángulo"
                                else "Ajustar ángulos a 15°",
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) },
                        onClick = { menuExpanded = false; onToggleAngleSnapping() },
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
                    }
                }
            }
        }
    }
}

private enum class EditorSaveState(val label: String) {
    AUTO("Autoguardado"), SAVING("Guardando…"), SAVED("Guardado"), FAILED("Reintentar guardado"),
    TEMPORARY("Sesión temporal"),
}

@Composable
private fun SaveStatus(state: EditorSaveState, onRetry: () -> Unit) {
    val tint = when (state) {
        EditorSaveState.FAILED -> StudioPalette.Warning
        EditorSaveState.SAVING -> StudioPalette.Accent
        EditorSaveState.SAVED -> StudioPalette.Success
        else -> StudioPalette.TextMuted
    }
    Row(
        modifier = Modifier.testTag("editor_save_status")
            .then(if (state == EditorSaveState.FAILED) {
                Modifier.clickable(role = Role.Button, onClickLabel = "Reintentar guardado", onClick = onRetry)
            } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            state.label, color = if (state == EditorSaveState.FAILED) tint else StudioPalette.TextMuted,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExportChoice(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        text = {
            Column {
                Text(title)
                Text(detail, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

@Composable
private fun TopIconButton(icon: ImageVector, description: String, anchorId: String?, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).let { if (anchorId != null) it.editorTutorialAnchor(anchorId) else it }) {
        Icon(icon, description, tint = StudioPalette.TextMuted, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun EditorToolRail(
    selectedTool: DrawingTool,
    preciseShapeTool: DrawingTool?,
    lastShapeTool: DrawingTool,
    compact: Boolean,
    showAllTools: Boolean,
    onSelect: (DrawingTool) -> Unit,
    onSelectShape: (DrawingTool) -> Unit,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val primaryTools = remember {
        setOf(DrawingTool.BRUSH, DrawingTool.ERASER, DrawingTool.EYEDROPPER,
            DrawingTool.SELECT_RECTANGLE, DrawingTool.TRANSFORM)
    }
    var moreToolsOpen by remember { mutableStateOf(false) }
    var shapeToolsOpen by remember { mutableStateOf(false) }
    val activeShape = preciseShapeTool ?: selectedTool.takeIf { tool -> shapeToolSpecs.any { it.tool == tool } }
    val shapeSpec = shapeToolSpecs.first { it.tool == (activeShape ?: lastShapeTool) }
    Column(
        modifier = Modifier
            .width(if (compact) 64.dp else 76.dp)
            .fillMaxHeight()
            .background(StudioPalette.Surface)
            .border(1.dp, StudioPalette.Border)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toolSpecs.filter { showAllTools || it.tool in primaryTools }.forEach { spec ->
                ToolRailButton(
                    spec = spec,
                    selected = preciseShapeTool == null && selectedTool == spec.tool,
                    showLabel = !compact,
                    onClick = { onSelect(spec.tool) },
                )
                Spacer(Modifier.height(5.dp))
                if (spec.tool == DrawingTool.TRANSFORM) {
                    Box {
                        ToolRailButton(
                            spec = shapeSpec,
                            selected = activeShape != null,
                            showLabel = !compact,
                            shapeGroup = true,
                            onClick = { shapeToolsOpen = true },
                        )
                        DropdownMenu(
                            expanded = shapeToolsOpen,
                            onDismissRequest = { shapeToolsOpen = false },
                            modifier = Modifier.testTag("shape_tool_menu"),
                        ) {
                            shapeToolSpecs.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = { Icon(item.icon, null) },
                                    trailingIcon = if (activeShape == item.tool) ({
                                        Icon(Icons.Outlined.Check, null)
                                    }) else null,
                                    modifier = Modifier.testTag("shape_tool_${item.tool.name.lowercase(Locale.ROOT)}")
                                        .semantics { selected = activeShape == item.tool },
                                    onClick = { shapeToolsOpen = false; onSelectShape(item.tool) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
            if (!showAllTools) {
                Box {
                    IconButton(
                        onClick = { moreToolsOpen = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Más herramientas",
                            tint = if (selectedTool !in primaryTools && activeShape == null) StudioPalette.Accent
                                else StudioPalette.TextMuted,
                        )
                    }
                    DropdownMenu(expanded = moreToolsOpen, onDismissRequest = { moreToolsOpen = false }) {
                        toolSpecs.filterNot { it.tool in primaryTools }.forEach { spec ->
                            DropdownMenuItem(
                                text = { Text(spec.label) },
                                leadingIcon = { Icon(spec.icon, null) },
                                modifier = Modifier.semantics { selected = selectedTool == spec.tool },
                                onClick = {
                                    onSelect(spec.tool)
                                    moreToolsOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
        bottomContent?.invoke()
    }
}

@Composable
private fun ToolRailButton(
    spec: ToolSpec, selected: Boolean, showLabel: Boolean, shapeGroup: Boolean = false, onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .editorTutorialAnchor(
                if (shapeGroup) "tool_rectangle" else when (spec.tool) {
                    DrawingTool.BRUSH -> "tool_brush"
                    DrawingTool.ERASER -> "tool_eraser"
                    DrawingTool.EYEDROPPER -> "tool_eyedropper"
                    DrawingTool.SELECT_RECTANGLE -> "tool_select_rectangle"
                    DrawingTool.TRANSFORM -> "tool_transform"
                    DrawingTool.RECTANGLE -> "tool_rectangle"
                    DrawingTool.FILL -> "tool_fill"
                    DrawingTool.GRADIENT -> "tool_gradient"
                    else -> "tool_${spec.tool.name.lowercase(Locale.ROOT)}"
                },
            )
            .then(if (shapeGroup) Modifier.testTag("shape_tool_group") else Modifier)
            .background(if (selected) StudioPalette.Accent else Color.Transparent, RoundedCornerShape(11.dp))
            .semantics {
                this.selected = selected
                role = Role.Button
                contentDescription = if (shapeGroup) "${spec.label}, abrir menú de figuras"
                    else "${spec.label}${if (selected) ", seleccionada" else ""}"
            }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (selected) Color.White else StudioPalette.TextMuted
        if (shapeGroup) {
            Box(Modifier.fillMaxWidth()) {
                Icon(spec.icon, null, tint = tint, modifier = Modifier.align(Alignment.Center).size(21.dp))
                Icon(Icons.Outlined.ArrowDropDown, null, tint = tint,
                    modifier = Modifier.align(Alignment.CenterEnd).size(13.dp))
            }
        } else Icon(spec.icon, spec.label, tint = tint, modifier = Modifier.size(21.dp))
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(spec.label, color = if (selected) Color.White else StudioPalette.TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun QuickDial(
    label: String,
    progress: Float,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier.width(252.dp),
) {
    BoxWithConstraints(
        modifier.editorTutorialAnchor(if (label == "Tamaño") "brush_size" else "brush_opacity"),
    ) {
        val showSteppers = maxWidth >= 235.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 6.dp)) {
                Text(label, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(value, color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
            }
            if (showSteppers) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Remove, "Reducir $label", tint = StudioPalette.TextMuted)
                }
            }
            QuickBrushSlider(label, progress, value, onProgressChanged, Modifier.weight(1f))
            if (showSteppers) {
                IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Add, "Aumentar $label", tint = StudioPalette.Text)
                }
            }
        }
    }
}

@Composable
private fun QuickBrushSlider(
    label: String,
    progress: Float,
    value: String,
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = onProgressChanged,
        modifier = modifier.height(48.dp)
            .testTag(if (label == "Tamaño") "quick_slider_size" else "quick_slider_opacity")
            .semantics {
                contentDescription = "Deslizar $label"
                stateDescription = value
            },
        colors = SliderDefaults.colors(
            thumbColor = StudioPalette.Accent,
            activeTrackColor = StudioPalette.Accent,
            inactiveTrackColor = StudioPalette.SurfaceHover,
        ),
    )
}

@Composable
private fun QuickMenuTrigger(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(54.dp)
            .editorTutorialAnchor("quick_menu_trigger")
            .semantics {
                contentDescription = "Abrir rueda de acceso rápido"
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { onOpen() })
            },
        color = Color(0xF2171A1F),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Star, null, tint = StudioPalette.Accent, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun RadialQuickMenu(
    actions: List<QuickAccessAction>,
    activeLayer: LayerUiModel?,
    selectedTool: DrawingTool,
    anchor: Offset?,
    onDismiss: () -> Unit,
    onAction: (QuickAccessAction) -> Unit,
    canEdit: Boolean,
    onEditSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val items = quickAccessItems(
        QuickAccessContext(
            hasActiveLayer = activeLayer != null,
            layerVisible = activeLayer?.visible ?: true,
            alphaLocked = activeLayer?.alphaLocked ?: false,
            hasMask = activeLayer?.hasMask ?: false,
            editingMask = activeLayer?.editingMask ?: false,
        ),
        actions,
    )
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black.copy(alpha = .24f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        val density = LocalDensity.current
        val wheelSize = 330.dp
        val wheelSizePx = with(density) { wheelSize.toPx() }
        val position = quickMenuTopLeft(
            anchorX = anchor?.x,
            anchorY = anchor?.y,
            viewportWidth = constraints.maxWidth.toFloat(),
            viewportHeight = constraints.maxHeight.toFloat(),
            wheelSize = wheelSizePx,
        )
        Box(
            Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(wheelSize),
            contentAlignment = Alignment.Center,
        ) {
            items.forEachIndexed { index, item ->
                val angle = Math.toRadians((-90.0 + index * 60.0))
                val anchor = when (item.action) {
                    QuickAccessAction.ADD_LAYER -> "quick_layer_add"
                    QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> "quick_layer_visibility"
                    QuickAccessAction.MASK_WORKFLOW -> "quick_layer_mask"
                    else -> null
                }
                val selected = item.selected ||
                    (item.action == QuickAccessAction.EYEDROPPER && selectedTool == DrawingTool.EYEDROPPER)
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = (cos(angle) * 112.0).toFloat().dp,
                            y = (sin(angle) * 112.0).toFloat().dp,
                        )
                        .size(78.dp)
                        .testTag("quick_access_slot_$index")
                        .let { if (anchor != null) it.editorTutorialAnchor(anchor) else it }
                        .semantics {
                            val label = quickAccessLabel(item.action, activeLayer)
                            contentDescription = if (editing) "Cambiar acceso ${index + 1}: $label" else label
                            this.selected = selected
                            role = Role.Button
                            if (editing || item.enabled) onClick {
                                if (editing) onEditSlot(index) else onAction(item.action)
                                true
                            }
                            if (canEdit) onLongClick("Cambiar este acceso") {
                                onEditSlot(index)
                                true
                            }
                        }
                        .pointerInput(item.action, item.enabled, editing, canEdit) {
                            detectTapGestures(
                                onTap = {
                                    if (editing) onEditSlot(index) else if (item.enabled) onAction(item.action)
                                },
                                onLongPress = if (canEdit) ({ onEditSlot(index) }) else null,
                            )
                        },
                    color = if (selected) StudioPalette.AccentSoft else Color(0xFA1A1E24),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) StudioPalette.Accent else StudioPalette.Border,
                    ),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            quickAccessIcon(item.action, activeLayer),
                            null,
                            tint = if (item.enabled || editing) Color.White else StudioPalette.TextMuted.copy(alpha = .35f),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                item.action == QuickAccessAction.MASK_WORKFLOW && activeLayer?.editingMask == true -> "Salir"
                                item.action == QuickAccessAction.MASK_WORKFLOW && activeLayer?.hasMask == true -> "Editar"
                                else -> quickAccessShortLabel(item.action)
                            },
                            color = if (item.enabled || editing) StudioPalette.Text else StudioPalette.TextMuted.copy(alpha = .35f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(108.dp)
                    .semantics { contentDescription = "Cerrar rueda de acceso rápido" }
                    .clickable(onClick = onDismiss),
                color = Color(0xFC111419),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Accent),
                shadowElevation = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (editing) "Elige un puesto" else "Acceso rápido", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                    if (canEdit) {
                        TextButton(
                            onClick = { editing = !editing },
                            modifier = Modifier.testTag("quick_access_edit"),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text(if (editing) "Listo" else "Editar rueda", fontSize = 10.sp)
                        }
                    } else Text("Desliza y suelta", color = StudioPalette.TextMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

private fun quickAccessIcon(action: QuickAccessAction, layer: LayerUiModel?): ImageVector = when (action) {
    QuickAccessAction.BRUSH_LIBRARY -> Icons.Outlined.Tune
    QuickAccessAction.EYEDROPPER -> Icons.Outlined.Colorize
    QuickAccessAction.ADD_LAYER -> Icons.Outlined.Add
    QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> if (layer?.visible == false) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
    QuickAccessAction.DUPLICATE_LAYER -> Icons.Outlined.ContentCopy
    QuickAccessAction.TOGGLE_ALPHA_LOCK -> if (layer?.alphaLocked == true) Icons.Outlined.Lock else Icons.Outlined.LockOpen
    QuickAccessAction.MASK_WORKFLOW -> Icons.Outlined.AutoFixNormal
    QuickAccessAction.OPEN_LAYERS -> Icons.Outlined.Layers
    QuickAccessAction.UNDO -> Icons.AutoMirrored.Outlined.Undo
    QuickAccessAction.REDO -> Icons.AutoMirrored.Outlined.Redo
    QuickAccessAction.FIT_CANVAS -> Icons.Outlined.CenterFocusStrong
    QuickAccessAction.TOGGLE_CANVAS_MODE -> Icons.Outlined.Fullscreen
}

private fun quickAccessShortLabel(action: QuickAccessAction): String = when (action) {
    QuickAccessAction.BRUSH_LIBRARY -> "Pinceles"
    QuickAccessAction.EYEDROPPER -> "Color"
    QuickAccessAction.ADD_LAYER -> "Nueva capa"
    QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> "Visibilidad"
    QuickAccessAction.DUPLICATE_LAYER -> "Duplicar"
    QuickAccessAction.TOGGLE_ALPHA_LOCK -> "Pintar dentro"
    QuickAccessAction.MASK_WORKFLOW -> "Ocultar"
    QuickAccessAction.OPEN_LAYERS -> "Capas"
    QuickAccessAction.UNDO -> "Deshacer"
    QuickAccessAction.REDO -> "Rehacer"
    QuickAccessAction.FIT_CANVAS -> "Ajustar lienzo"
    QuickAccessAction.TOGGLE_CANVAS_MODE -> "Modo lienzo"
}

private fun quickAccessLabel(action: QuickAccessAction, layer: LayerUiModel?): String = when (action) {
    QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> if (layer?.visible == false) "Mostrar capa" else "Ocultar capa"
    QuickAccessAction.TOGGLE_ALPHA_LOCK -> if (layer?.alphaLocked == true) "Desactivar Pintar dentro" else "Activar Pintar dentro"
    QuickAccessAction.MASK_WORKFLOW -> when {
        layer?.editingMask == true -> "Salir de la ocultación y volver a pintar la capa"
        layer?.hasMask == true -> "Editar ocultación reversible"
        else -> "Crear ocultación reversible"
    }
    else -> quickAccessShortLabel(action)
}

private fun quickAccessDescription(action: QuickAccessAction): String = when (action) {
    QuickAccessAction.BRUSH_LIBRARY -> "Abrir la biblioteca y ajustar el pincel."
    QuickAccessAction.EYEDROPPER -> "Tomar un color directamente del lienzo."
    QuickAccessAction.ADD_LAYER -> "Crear y seleccionar una capa vacía."
    QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> "Mostrar u ocultar la capa activa."
    QuickAccessAction.DUPLICATE_LAYER -> "Crear una copia de la capa activa."
    QuickAccessAction.TOGGLE_ALPHA_LOCK -> "Pintar únicamente sobre píxeles existentes."
    QuickAccessAction.MASK_WORKFLOW -> "Ocultar o recuperar sin borrar el original."
    QuickAccessAction.OPEN_LAYERS -> "Abrir el panel completo de capas."
    QuickAccessAction.UNDO -> "Deshacer el último cambio del dibujo."
    QuickAccessAction.REDO -> "Rehacer el cambio deshecho."
    QuickAccessAction.FIT_CANVAS -> "Encuadrar el dibujo y restablecer zoom y rotación."
    QuickAccessAction.TOGGLE_CANVAS_MODE -> "Ocultar o mostrar los paneles del editor."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaskEditingControls(
    layerId: String,
    layerName: String,
    recovering: Boolean,
    maskEnabled: Boolean,
    maskStrength: Int,
    maskInverted: Boolean,
    showingOriginal: Boolean,
    onPaint: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onInvert: () -> Unit,
    onCompare: () -> Unit,
    onDone: () -> Unit,
) {
    val paintActive = maskEnabled && !showingOriginal
    var showStrength by remember(layerId) { mutableStateOf(false) }
    var showMore by remember(layerId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mask_editing_bar"),
        color = StudioPalette.Surface,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(
                if (maskInverted) "Editando máscara invertida · $layerName" else "Editando máscara · $layerName",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = StudioPalette.Accent,
            )
            Text(
                when {
                    !maskEnabled -> "Máscara pausada. Elige Ocultar o Recuperar para reactivarla."
                    showingOriginal -> "Viendo el original. Pulsa Ver resultado para seguir pintando."
                    maskStrength == 0 -> "Al 0% puedes pintar la máscara, pero solo verás el efecto al subir la intensidad."
                    recovering -> "Pinta sobre la zona oculta para volver a mostrarla."
                    else -> "Pinta sobre lo que quieres ocultar. Tu dibujo no se borra."
                },
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onPaint(false) },
                    modifier = Modifier.semantics { selected = paintActive && !recovering },
                ) { Text("Ocultar", color = if (paintActive && !recovering) StudioPalette.Accent else StudioPalette.Text) }
                TextButton(
                    onClick = { onPaint(true) },
                    modifier = Modifier.semantics { selected = paintActive && recovering },
                ) { Text("Recuperar", color = if (paintActive && recovering) StudioPalette.Accent else StudioPalette.Text) }
                if (maskEnabled) {
                    TextButton(onClick = onCompare) { Text(if (showingOriginal) "Ver resultado" else "Ver original") }
                }
                TextButton(
                    onClick = { showStrength = !showStrength },
                    modifier = Modifier.testTag("mask_strength_toggle").semantics {
                        stateDescription = if (showStrength) "Expandido" else "Contraído"
                    },
                ) {
                    Text("Intensidad · $maskStrength%")
                    if (!showStrength) Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp))
                }
                TextButton(onClick = onDone) { Text("Listo") }
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Outlined.MoreHoriz, "Más opciones de máscara", tint = StudioPalette.TextMuted)
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text(if (maskInverted) "Quitar inversión" else "Invertir máscara") },
                            onClick = { showMore = false; onInvert() },
                        )
                    }
                }
            }
            if (showStrength) {
                Surface(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    color = StudioPalette.SurfaceRaised,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Slider(
                            value = maskStrength.toFloat(),
                            onValueChange = { onStrengthChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = StudioPalette.Accent,
                                activeTrackColor = StudioPalette.Accent,
                                inactiveTrackColor = StudioPalette.SurfaceHover,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("mask_strength_slider").semantics {
                                contentDescription = "Intensidad de la máscara"
                                stateDescription = "$maskStrength%"
                            },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0% · Original", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
                            Text("100% · Máscara completa", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            HorizontalDivider(color = StudioPalette.Border)
        }
    }
}

@Composable
private fun PerspectiveToolbar(
    modifier: Modifier = Modifier,
    editing: Boolean,
    snapping: Boolean,
    onToggleEditing: () -> Unit,
    onToggleSnapping: () -> Unit,
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
            TextButton(onClick = onToggleSnapping) {
                Text(if (snapping) "Ajuste activo" else "Ajustar a guias")
            }
            TextButton(onClick = onReset) { Text("Restablecer") }
        }
    }
}

@Composable
private fun SelectionToolbar(
    modifier: Modifier = Modifier,
    onTransform: () -> Unit,
    onPreciseTransform: () -> Unit,
    onExpand: () -> Unit,
    onContract: () -> Unit,
    onInvert: () -> Unit,
    featherPx: Float,
    onCycleFeather: () -> Unit,
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
            TextButton(onClick = onPreciseTransform) { Text("Precisión") }
            TextButton(onClick = onExpand) { Text("Expandir 16 px") }
            TextButton(onClick = onContract) { Text("Contraer 16 px") }
            TextButton(onClick = onInvert) { Text("Invertir") }
            TextButton(onClick = onCycleFeather) {
                Text(if (featherPx == 0f) "Suavizar" else "Borde ${featherPx.toInt()} px")
            }
            TextButton(onClick = onFlipHorizontal) { Text("Voltear H") }
            TextButton(onClick = onFlipVertical) { Text("Voltear V") }
            TextButton(onClick = onDelete) { Text("Borrar") }
            TextButton(onClick = onDeselect) { Text("Deseleccionar") }
        }
    }
}

@Composable
private fun NumericSelectionTransformDialog(
    onDismiss: () -> Unit,
    onApply: (Float, Float, Float, Float) -> Unit,
) {
    var x by remember { mutableStateOf("0") }
    var y by remember { mutableStateOf("0") }
    var scale by remember { mutableStateOf("100") }
    var angle by remember { mutableStateOf("0") }
    fun number(value: String): Float? = value.trim().replace(',', '.').toFloatOrNull()?.takeIf(Float::isFinite)
    val dx = number(x)
    val dy = number(y)
    val percent = number(scale)
    val degrees = number(angle)
    val valid = dx != null && dx in -30_000f..30_000f && dy != null && dy in -30_000f..30_000f &&
        percent != null && percent in 5f..400f && degrees != null && degrees in -360f..360f &&
        (dx != 0f || dy != 0f || percent != 100f || degrees % 360f != 0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transformar con precisión") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("El giro y la escala se aplican desde el centro de la selección.")
                OutlinedTextField(x, { x = it }, label = { Text("Mover horizontal (px)") }, singleLine = true)
                OutlinedTextField(y, { y = it }, label = { Text("Mover vertical (px)") }, singleLine = true)
                OutlinedTextField(scale, { scale = it }, label = { Text("Escala uniforme (5–400 %)") }, singleLine = true)
                OutlinedTextField(angle, { angle = it }, label = { Text("Giro (−360° a 360°)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onApply(requireNotNull(dx), requireNotNull(dy), requireNotNull(percent), requireNotNull(degrees))
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RightDock(
    dockWidth: androidx.compose.ui.unit.Dp,
    selectedTab: DockTab,
    onSelectTab: (DockTab) -> Unit,
    brushDockResetTick: Int,
    selectedPreset: BrushPreset,
    brushes: List<BrushPreset>,
    brushSettings: BrushSettings,
    recentColors: List<Int>,
    onClearRecentColors: () -> Unit,
    onPresetSelected: (BrushPreset) -> Unit,
    onBrushSettingsChanged: (BrushSettings) -> Unit,
    onSaveCustomBrush: (String) -> Unit,
    layers: List<LayerUiModel>,
    groups: List<LayerGroupUiModel>,
    onSelectLayer: (String) -> Unit,
    onToggleLayerSelection: (String) -> Unit,
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
    onHideWithMask: (String) -> Unit,
    onRevealWithMask: (String) -> Unit,
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
                resetTick = brushDockResetTick,
                onPresetSelected = onPresetSelected,
                onSettingsChanged = onBrushSettingsChanged,
                onSaveCustomBrush = onSaveCustomBrush,
            )
            DockTab.COLOR -> ColorDock(
                currentColor = brushSettings.color,
                recentColors = recentColors,
                onClearRecentColors = onClearRecentColors,
                onColorChanged = { onBrushSettingsChanged(brushSettings.copy(color = it)) },
            )
            DockTab.LAYERS -> LayersDock(
                layers = layers,
                groups = groups,
                onSelectLayer = onSelectLayer,
                onToggleLayerSelection = onToggleLayerSelection,
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
                onHideWithMask = onHideWithMask,
                onRevealWithMask = onRevealWithMask,
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
            .editorTutorialAnchor(
                when (label) {
                    "Pinceles" -> "dock_brushes"
                    "Color" -> "dock_color"
                    else -> "dock_layers"
                },
            )
            .background(if (selected) StudioPalette.SurfaceHover else Color.Transparent, RoundedCornerShape(9.dp))
            .semantics {
                this.selected = selected
                role = Role.Tab
                contentDescription = "Panel $label${if (selected) ", seleccionado" else ""}"
            }
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
private fun ExpandedBrushLibraryDialog(
    brushes: List<BrushPreset>,
    customBrushIds: Set<String>,
    favoriteBrushIds: Set<String>,
    recentBrushIds: List<String>,
    selectedPreset: BrushPreset,
    settings: BrushSettings,
    onPresetSelected: (BrushPreset) -> Unit,
    onSettingsChanged: (BrushSettings) -> Unit,
    onToggleFavorite: (BrushPreset) -> Unit,
    onDuplicate: (BrushPreset) -> Unit,
    onRename: (BrushPreset, String) -> Unit,
    onDelete: (BrushPreset) -> Unit,
    onImport: () -> Unit,
    onImportTip: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<BrushPreset?>(null) }
    var renameText by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf("General") }
    val supportsGrain = settings.grainProfile.source != com.orbyte.canvasstudio.drawing.BrushGrainSource.NONE ||
        settings.kind in setOf(
            BrushKind.PENCIL, BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.PAINT,
            BrushKind.DRY_BRUSH, BrushKind.BRISTLE, BrushKind.WATERCOLOR, BrushKind.OIL,
        )
    val supportsDualBrush = settings.dualBrushProfile.enabled || settings.kind in setOf(
        BrushKind.PENCIL, BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH,
        BrushKind.BRISTLE, BrushKind.WATERCOLOR, BrushKind.OIL,
    )
    val supportsTilt = settings.tiltResponse > .001f || settings.dynamicsProfile.tiltSize > .001f ||
        settings.kind in setOf(
            BrushKind.PENCIL, BrushKind.MARKER, BrushKind.CHARCOAL, BrushKind.CHALK,
            BrushKind.DRY_BRUSH, BrushKind.BRISTLE,
        )
    val supportsTaper = settings.kind in setOf(BrushKind.PENCIL, BrushKind.INK, BrushKind.DRY_BRUSH)
    val supportsScatter = settings.kind in setOf(
        BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH, BrushKind.BRISTLE,
        BrushKind.WATERCOLOR, BrushKind.OIL,
    )
    val categoryOrder = listOf(
        "Lápices", "Tinta", "Marcadores", "Pintura", "Textura", "Aerógrafos",
        "Experimental", "Personalizados",
    )
    val presentCategories = brushes.mapTo(linkedSetOf(), BrushPreset::category)
    val categories = listOf("Todos", "Favoritos", "Recientes") +
        categoryOrder.filter { it in presentCategories } + presentCategories.filterNot { it in categoryOrder }
    val recentOrder = recentBrushIds.withIndex().associate { it.value to it.index }
    val visible = brushes.filter { preset ->
        when (category) {
            "Todos" -> true
            "Favoritos" -> preset.id in favoriteBrushIds
            "Recientes" -> preset.id in recentBrushIds
            else -> preset.category == category
        } && (
            query.isBlank() ||
                preset.name.contains(query.trim(), ignoreCase = true) ||
                preset.category.contains(query.trim(), ignoreCase = true)
            )
    }.let { filtered ->
        if (category == "Recientes") filtered.sortedBy { recentOrder[it.id] ?: Int.MAX_VALUE } else filtered
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(34.dp),
            color = StudioPalette.Surface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Biblioteca de pinceles",
                            color = StudioPalette.Text,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "${brushes.size} pinceles · ${customBrushIds.size} personalizados",
                            color = StudioPalette.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onImport) {
                        Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Biblioteca")
                    }
                    TextButton(onClick = onImportTip) {
                        Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Punta bitmap")
                    }
                    TextButton(onClick = onExport, enabled = customBrushIds.isNotEmpty()) {
                        Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Exportar")
                    }
                    TextButton(onClick = onDismiss) { Text("Listo") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LazyColumn(
                        modifier = Modifier.width(190.dp).fillMaxHeight()
                            .background(StudioPalette.SurfaceRaised, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(categories, key = { it }) { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { category = item },
                                color = if (category == item) StudioPalette.AccentSoft else Color.Transparent,
                                shape = RoundedCornerShape(9.dp),
                            ) {
                                Text(
                                    item,
                                    color = if (category == item) Color.White else StudioPalette.TextMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                )
                            }
                        }
                    }

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(40) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = { Text("Buscar pinceles") },
                        )
                        Spacer(Modifier.height(9.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items(visible, key = BrushPreset::id) { preset ->
                                BrushPresetRow(
                                    preset = preset,
                                    selected = preset.id == selectedPreset.id,
                                    favorite = preset.id in favoriteBrushIds,
                                    onToggleFavorite = { onToggleFavorite(preset) },
                                    onDuplicate = { onDuplicate(preset) },
                                    onRename = if (preset.id in customBrushIds) {
                                        {
                                            renameTarget = preset
                                            renameText = preset.name
                                        }
                                    } else {
                                        null
                                    },
                                    onDelete = if (preset.id in customBrushIds) {
                                        { onDelete(preset) }
                                    } else {
                                        null
                                    },
                                    onClick = { onPresetSelected(preset) },
                                )
                            }
                            if (visible.isEmpty()) {
                                item {
                                    Text(
                                        when (category) {
                                            "Favoritos" -> "Marca pinceles con la estrella para encontrarlos aquí."
                                            "Recientes" -> "Los pinceles que uses aparecerán aquí."
                                            else -> "No hay pinceles que coincidan con la búsqueda."
                                        },
                                        color = StudioPalette.TextMuted,
                                        modifier = Modifier.padding(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        Modifier.width(360.dp).fillMaxHeight()
                            .background(StudioPalette.SurfaceRaised, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(selectedPreset.name, color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                        Text(
                            selectedPreset.kind.displayName(),
                            color = StudioPalette.Accent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(12.dp))
                        BrushStrokePreview(
                            preset = selectedPreset.withSettings(settings),
                            modifier = Modifier.fillMaxWidth().height(104.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .background(StudioPalette.Surface, RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("General", "Material", "Dinámicas").forEach { section ->
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { settingsSection = section },
                                    color = if (settingsSection == section) StudioPalette.AccentSoft else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        section,
                                        color = if (settingsSection == section) Color.White else StudioPalette.TextMuted,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(vertical = 9.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (settingsSection == "General") {
                            SettingSlider("Tamaño", settings.sizePx, 2f..180f, "${settings.sizePx.toInt()} px") {
                                onSettingsChanged(settings.copy(sizePx = it))
                            }
                            SettingSlider("Opacidad", settings.opacity, .05f..1f, "${(settings.opacity * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(opacity = it))
                            }
                            SettingSlider("Flujo", settings.flow, .05f..1f, "${(settings.flow * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(flow = it))
                            }
                            SettingSlider("Dureza", settings.hardness, 0f..1f, "${(settings.hardness * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(hardness = it))
                            }
                            SettingSlider("Espaciado", settings.spacing, .025f..0.4f, "${(settings.spacing * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(spacing = it))
                            }
                            if (supportsGrain) {
                                SettingSlider("Textura", settings.grain, 0f..1f, "${(settings.grain * 100).toInt()}%") {
                                    onSettingsChanged(
                                        settings.copy(
                                            grain = it,
                                            grainProfile = settings.grainProfile.copy(depth = it),
                                        ),
                                    )
                                }
                            }
                            SettingSlider("Estabilización", settings.stabilization, 0f..0.9f, "${(settings.stabilization * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(stabilization = it))
                            }
                        } else if (settingsSection == "Material") {
                            Text("Punta", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
                            SettingSlider(
                                "Redondez",
                                settings.tipProfile.roundness,
                                .08f..1f,
                                "${(settings.tipProfile.roundness * 100).toInt()}%",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        tipProfile = settings.tipProfile.copy(roundness = it),
                                    ),
                                )
                            }
                            SettingSlider(
                                "Ángulo",
                                settings.tipProfile.angleDegrees,
                                -180f..180f,
                                "${settings.tipProfile.angleDegrees.toInt()}°",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        tipProfile = settings.tipProfile.copy(angleDegrees = it),
                                    ),
                                )
                            }
                            if (settings.tipProfile.shape in setOf(
                                    BrushTipShape.BRISTLE,
                                    BrushTipShape.PARTICLE,
                                )
                            ) {
                                SettingSlider(
                                    "Cantidad",
                                    settings.tipProfile.count.toFloat(),
                                    2f..14f,
                                    settings.tipProfile.count.toString(),
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            tipProfile = settings.tipProfile.copy(count = it.toInt()),
                                        ),
                                    )
                                }
                            }
                            if (supportsGrain) {
                                Text("Grano", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
                                SettingSlider(
                                "Profundidad",
                                settings.grainProfile.depth,
                                0f..1f,
                                "${(settings.grainProfile.depth * 100).toInt()}%",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        grain = it,
                                        grainProfile = settings.grainProfile.copy(depth = it),
                                    ),
                                )
                            }
                                SettingSlider(
                                "Escala",
                                settings.grainProfile.scale,
                                .15f..4f,
                                String.format(Locale.US, "%.2fx", settings.grainProfile.scale),
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        grainProfile = settings.grainProfile.copy(scale = it),
                                    ),
                                )
                            }
                                SettingSlider(
                                "Contraste",
                                settings.grainProfile.contrast,
                                0f..1f,
                                "${(settings.grainProfile.contrast * 100).toInt()}%",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        grainProfile = settings.grainProfile.copy(contrast = it),
                                    ),
                                )
                            }
                            }
                            if (supportsDualBrush) {
                                Text(
                                    "Pincel dual",
                                    color = StudioPalette.Text,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                BrushToggle("Segunda punta", settings.dualBrushProfile.enabled) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dualBrushProfile = settings.dualBrushProfile.copy(enabled = it),
                                        ),
                                    )
                                }
                            }
                            if (supportsDualBrush && settings.dualBrushProfile.enabled) {
                                SettingSlider(
                                    "Escala secundaria",
                                    settings.dualBrushProfile.sizeScale,
                                    .1f..2f,
                                    String.format(Locale.US, "%.2fx", settings.dualBrushProfile.sizeScale),
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dualBrushProfile = settings.dualBrushProfile.copy(sizeScale = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Opacidad secundaria",
                                    settings.dualBrushProfile.opacity,
                                    0f..1f,
                                    "${(settings.dualBrushProfile.opacity * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dualBrushProfile = settings.dualBrushProfile.copy(opacity = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Dispersión secundaria",
                                    settings.dualBrushProfile.scatter,
                                    0f..1f,
                                    "${(settings.dualBrushProfile.scatter * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dualBrushProfile = settings.dualBrushProfile.copy(scatter = it),
                                        ),
                                    )
                                }
                            }
                            Text(
                                "Render y medio",
                                color = StudioPalette.Text,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            SettingSlider(
                                "Acumulación",
                                settings.renderProfile.buildup,
                                0f..1f,
                                "${(settings.renderProfile.buildup * 100).toInt()}%",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        renderProfile = settings.renderProfile.copy(buildup = it),
                                    ),
                                )
                            }
                            if (settings.kind in setOf(
                                    BrushKind.WATERCOLOR,
                                    BrushKind.OIL,
                                    BrushKind.PAINT,
                                    BrushKind.BRISTLE,
                                )
                            ) {
                                SettingSlider(
                                    "Humedad",
                                    settings.renderProfile.wetness,
                                    0f..1f,
                                    "${(settings.renderProfile.wetness * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(wetness = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Arrastre",
                                    settings.renderProfile.drag,
                                    0f..1f,
                                    "${(settings.renderProfile.drag * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(drag = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Dilución",
                                    settings.renderProfile.dilution,
                                    0f..1f,
                                    "${(settings.renderProfile.dilution * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(dilution = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Carga",
                                    settings.renderProfile.charge,
                                    0f..1f,
                                    "${(settings.renderProfile.charge * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(charge = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Ataque",
                                    settings.renderProfile.attack,
                                    0f..1f,
                                    "${(settings.renderProfile.attack * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(attack = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Sangrado",
                                    settings.renderProfile.bleed,
                                    0f..1f,
                                    "${(settings.renderProfile.bleed * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(bleed = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Recogida de color",
                                    settings.renderProfile.colorPickup,
                                    0f..1f,
                                    "${(settings.renderProfile.colorPickup * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            renderProfile = settings.renderProfile.copy(colorPickup = it),
                                        ),
                                    )
                                }
                            }
                        } else {
                            SettingSlider("Tamaño mínimo", settings.minSize, .02f..1f, "${(settings.minSize * 100).toInt()}%") {
                                onSettingsChanged(settings.copy(minSize = it))
                            }
                            SettingSlider("Curva de presión", settings.pressureCurve, .35f..2.5f, "%.2f".format(settings.pressureCurve)) {
                                onSettingsChanged(settings.copy(pressureCurve = it))
                            }
                            PressureCurvePreview(settings.pressureCurve)
                            SettingSlider(
                                "Curva de tamaño",
                                settings.dynamicsProfile.sizePressure.gamma,
                                .25f..4f,
                                "%.2f".format(settings.dynamicsProfile.sizePressure.gamma),
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        dynamicsProfile = settings.dynamicsProfile.copy(
                                            sizePressure = settings.dynamicsProfile.sizePressure.copy(gamma = it),
                                        ),
                                    ),
                                )
                            }
                            SettingSlider(
                                "Curva de opacidad",
                                settings.dynamicsProfile.opacityPressure.gamma,
                                .25f..4f,
                                "%.2f".format(settings.dynamicsProfile.opacityPressure.gamma),
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        dynamicsProfile = settings.dynamicsProfile.copy(
                                            opacityPressure = settings.dynamicsProfile.opacityPressure.copy(gamma = it),
                                        ),
                                    ),
                                )
                            }
                            SettingSlider(
                                "Curva de flujo",
                                settings.dynamicsProfile.flowPressure.gamma,
                                .25f..4f,
                                "%.2f".format(settings.dynamicsProfile.flowPressure.gamma),
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        dynamicsProfile = settings.dynamicsProfile.copy(
                                            flowPressure = settings.dynamicsProfile.flowPressure.copy(gamma = it),
                                        ),
                                    ),
                                )
                            }
                            if (supportsTilt) {
                                SettingSlider("Inclinación", settings.tiltResponse, 0f..1f, "${(settings.tiltResponse * 100).toInt()}%") {
                                    onSettingsChanged(settings.copy(tiltResponse = it))
                                }
                            }
                            if (supportsTaper) {
                                SettingSlider("Taper inicial", settings.taperStart, 0f..0.48f, "${(settings.taperStart * 100).toInt()}%") {
                                    onSettingsChanged(settings.copy(taperStart = it))
                                }
                                SettingSlider("Taper final", settings.taperEnd, 0f..0.48f, "${(settings.taperEnd * 100).toInt()}%") {
                                    onSettingsChanged(settings.copy(taperEnd = it))
                                }
                            }
                            if (supportsScatter) {
                                SettingSlider("Dispersión", settings.scatter, 0f..0.5f, "${(settings.scatter * 100).toInt()}%") {
                                    onSettingsChanged(settings.copy(scatter = it))
                                }
                            }
                            SettingSlider("Respuesta a velocidad", settings.velocitySize, 0f..1f, "${(settings.velocitySize * 100).toInt()}%") {
                                onSettingsChanged(
                                    settings.copy(
                                        velocitySize = it,
                                        dynamicsProfile = settings.dynamicsProfile.copy(velocitySize = it),
                                    ),
                                )
                            }
                            SettingSlider(
                                "Velocidad → opacidad",
                                settings.dynamicsProfile.velocityOpacity,
                                0f..1f,
                                "${(settings.dynamicsProfile.velocityOpacity * 100).toInt()}%",
                            ) {
                                onSettingsChanged(
                                    settings.copy(
                                        dynamicsProfile = settings.dynamicsProfile.copy(velocityOpacity = it),
                                    ),
                                )
                            }
                            if (supportsTilt) {
                                SettingSlider(
                                    "Umbral de inclinación",
                                    settings.dynamicsProfile.tiltThreshold,
                                    0f..0.9f,
                                    "${(settings.dynamicsProfile.tiltThreshold * 90f).toInt()}°",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dynamicsProfile = settings.dynamicsProfile.copy(tiltThreshold = it),
                                        ),
                                    )
                                }
                                SettingSlider(
                                    "Inclinación → opacidad",
                                    settings.dynamicsProfile.tiltOpacity,
                                    0f..1f,
                                    "${(settings.dynamicsProfile.tiltOpacity * 100).toInt()}%",
                                ) {
                                    onSettingsChanged(
                                        settings.copy(
                                            dynamicsProfile = settings.dynamicsProfile.copy(tiltOpacity = it),
                                        ),
                                    )
                                }
                            }
                            BrushToggle("Presión controla tamaño", settings.pressureSize) {
                                onSettingsChanged(settings.copy(pressureSize = it))
                            }
                            BrushToggle("Presión controla opacidad", settings.pressureOpacity) {
                                onSettingsChanged(settings.copy(pressureOpacity = it))
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar pincel") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(40) },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(preset, renameText.trim())
                        renameTarget = null
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BrushDock(
    selectedPreset: BrushPreset,
    brushes: List<BrushPreset>,
    settings: BrushSettings,
    resetTick: Int,
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
    val dockScrollState = rememberScrollState()
    LaunchedEffect(selectedPreset.id, resetTick) {
        dockScrollState.scrollTo(0)
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

    Column(Modifier.fillMaxSize().verticalScroll(dockScrollState).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca de pinceles", color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Motor 3.0 · punta, grano, render y dinámica del S Pen",
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(visibleBrushes, key = BrushPreset::id) { preset ->
                BrushPresetRow(
                    preset,
                    selected = preset.id == selectedPreset.id,
                ) { onPresetSelected(preset) }
            }
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
        if (settings.kind in setOf(
                BrushKind.AIRBRUSH,
                BrushKind.CHARCOAL,
                BrushKind.CHALK,
                BrushKind.DRY_BRUSH,
                BrushKind.BRISTLE,
                BrushKind.WATERCOLOR,
            )
        ) {
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
        PressureCurvePreview(settings.pressureCurve)
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
            onSettingsChanged(
                settings.copy(
                    grain = it,
                    grainProfile = settings.grainProfile.copy(depth = it),
                ),
            )
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
private fun PressureCurvePreview(curve: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(vertical = 8.dp),
    ) {
        drawLine(StudioPalette.Border, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 1f)
        val path = Path()
        repeat(65) { index ->
            val input = index / 64f
            val output = Math.pow(input.toDouble(), curve.toDouble()).toFloat()
            val point = Offset(input * size.width, size.height - output * size.height)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, StudioPalette.Accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
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
internal fun BrushPresetRow(
    preset: BrushPreset,
    selected: Boolean,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Pincel ${preset.name}, ${preset.category}"
                this.selected = selected
                stateDescription = if (selected) "Seleccionado" else "No seleccionado"
            }
            .clickable(role = Role.Button, onClick = onClick),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) StudioPalette.Accent else StudioPalette.Border),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(95.dp)) {
                Text(preset.name, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (preset.category == "Experimental") "Experimental · en desarrollo" else preset.category,
                    color = if (preset.category == "Experimental") StudioPalette.Accent else StudioPalette.TextMuted,
                    fontSize = 10.sp,
                )
            }
            BrushStrokePreview(preset, Modifier.weight(1f).height(34.dp))
            onToggleFavorite?.let { toggle ->
                IconButton(onClick = toggle, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (favorite) StudioPalette.Accent else StudioPalette.TextMuted,
                    )
                }
            }
            onDuplicate?.let { duplicate ->
                IconButton(onClick = duplicate, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Duplicar pincel", tint = StudioPalette.TextMuted)
                }
            }
            onRename?.let { rename ->
                IconButton(onClick = rename, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, "Renombrar pincel", tint = StudioPalette.TextMuted)
                }
            }
            onDelete?.let { delete ->
                IconButton(onClick = delete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Delete, "Eliminar pincel", tint = StudioPalette.TextMuted)
                }
            }
        }
    }
}

private fun BrushKind.displayName(): String = when (this) {
    BrushKind.PENCIL -> "Lápiz"
    BrushKind.INK -> "Tinta"
    BrushKind.MARKER -> "Rotulador"
    BrushKind.PAINT -> "Pintura"
    BrushKind.AIRBRUSH -> "Aerógrafo"
    BrushKind.CHARCOAL -> "Carboncillo"
    BrushKind.CHALK -> "Tiza"
    BrushKind.DRY_BRUSH -> "Pincel seco"
    BrushKind.BRISTLE -> "Cerdas"
    BrushKind.WATERCOLOR -> "Acuarela"
    BrushKind.OIL -> "Óleo"
}

internal fun BrushPreset.withSettings(settings: BrushSettings): BrushPreset = copy(
    kind = settings.kind,
    sizePx = settings.sizePx,
    opacity = settings.opacity,
    hardness = settings.hardness,
    spacing = settings.spacing,
    stabilization = settings.stabilization,
    flow = settings.flow,
    minSize = settings.minSize,
    pressureSize = settings.pressureSize,
    pressureOpacity = settings.pressureOpacity,
    pressureCurve = settings.pressureCurve,
    tiltResponse = settings.tiltResponse,
    taperStart = settings.taperStart,
    taperEnd = settings.taperEnd,
    scatter = settings.scatter,
    grain = settings.grain,
    velocitySize = settings.velocitySize,
    tipAssetPath = settings.tipAssetPath,
    tipProfile = settings.tipProfile,
    grainProfile = settings.grainProfile,
    renderProfile = settings.renderProfile,
    dynamicsProfile = settings.dynamicsProfile,
    dualBrushProfile = settings.dualBrushProfile,
)

@Composable
internal fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValueChange: (Float) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = display
            }
            .padding(vertical = 5.dp),
    ) {
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
private fun ColorDock(
    currentColor: Int,
    recentColors: List<Int>,
    onClearRecentColors: () -> Unit,
    onColorChanged: (Int) -> Unit,
) {
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Colores recientes", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (recentColors.isNotEmpty()) {
                TextButton(onClick = onClearRecentColors) { Text("Limpiar") }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (recentColors.isEmpty()) {
            Text(
                "Aquí aparecerán los colores que uses al pintar.",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recentColors.forEach { swatch ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(swatch), RoundedCornerShape(8.dp))
                            .border(1.dp, StudioPalette.Border, RoundedCornerShape(8.dp))
                            .semantics {
                                contentDescription = String.format(Locale.US, "Color reciente #%06X", swatch and 0xFFFFFF)
                            }
                            .clickable { onColorChanged(swatch) },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Paleta básica", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                String.format(Locale.US, "#%06X", 0xFFFFFF and currentColor),
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        val baseSwatches = remember {
            listOf(0xFF20242B, 0xFFF4F1E8, 0xFFE25555, 0xFFF2A65A, 0xFFE6D04A, 0xFF57B879, 0xFF4A90E2, 0xFF7257D9, 0xFFB052A1, 0xFF8A6751)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            baseSwatches.forEach { swatch ->
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
internal fun HueWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    modifier: Modifier,
    onHueChanged: (Float) -> Unit,
    onSaturationValueChanged: (Float, Float) -> Unit,
) {
    val latestOnHueChanged by rememberUpdatedState(onHueChanged)
    val latestOnSaturationValueChanged by rememberUpdatedState(onSaturationValueChanged)
    // Three gradient draws replace the old 120 arcs + 576 swatch rectangles per frame.
    // The two composited gradients are mathematically equivalent to HSV(saturation, value).
    val hueRing = remember {
        Brush.sweepGradient((0..6).map { step ->
            Color(AndroidColor.HSVToColor(floatArrayOf(step * 60f, 1f, 1f)))
        })
    }
    val saturationBrush = remember(hue) {
        Brush.horizontalGradient(
            listOf(Color.White, Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))),
        )
    }
    val valueBrush = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) }

    Canvas(
        modifier = modifier
            .testTag("hsv_color_wheel")
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    when (val selection = colorWheelSelection(
                        point.x, point.y, size.width.toFloat(), size.height.toFloat(),
                    )) {
                        is ColorWheelSelection.Hue -> latestOnHueChanged(selection.degrees)
                        is ColorWheelSelection.SaturationValue ->
                            latestOnSaturationValueChanged(selection.saturation, selection.value)
                        null -> Unit
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point ->
                        when (val selection = colorWheelSelection(
                            point.x, point.y, size.width.toFloat(), size.height.toFloat(),
                        )) {
                            is ColorWheelSelection.Hue -> latestOnHueChanged(selection.degrees)
                            is ColorWheelSelection.SaturationValue ->
                                latestOnSaturationValueChanged(selection.saturation, selection.value)
                            null -> Unit
                        }
                    },
                    onDrag = { change, _ ->
                        when (val selection = colorWheelSelection(
                            change.position.x,
                            change.position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )) {
                            is ColorWheelSelection.Hue -> latestOnHueChanged(selection.degrees)
                            is ColorWheelSelection.SaturationValue ->
                                latestOnSaturationValueChanged(selection.saturation, selection.value)
                            null -> Unit
                        }
                        change.consume()
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = min(size.width, size.height) / 2f
        val ringWidth = outerRadius * .19f
        val ringRadius = outerRadius - ringWidth / 2f
        drawCircle(
            brush = hueRing,
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringWidth),
        )
        val squareHalf = outerRadius * .42f
        val topLeft = Offset(center.x - squareHalf, center.y - squareHalf)
        val squareSize = Size(squareHalf * 2f, squareHalf * 2f)
        drawRect(brush = saturationBrush, topLeft = topLeft, size = squareSize)
        drawRect(brush = valueBrush, topLeft = topLeft, size = squareSize)
        val pointerAngle = hue * PI.toFloat() / 180f
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = Offset(
                center.x + cos(pointerAngle.toDouble()).toFloat() * ringRadius,
                center.y + sin(pointerAngle.toDouble()).toFloat() * ringRadius,
            ),
            style = Stroke(2.5f),
        )
        drawCircle(
            color = Color.White,
            radius = 5f,
            center = Offset(
                center.x - squareHalf + saturation * squareHalf * 2f,
                center.y + squareHalf - value * squareHalf * 2f,
            ),
            style = Stroke(2f),
        )
    }
}

@Composable
private fun LayersDock(
    layers: List<LayerUiModel>,
    groups: List<LayerGroupUiModel>,
    onSelectLayer: (String) -> Unit,
    onToggleLayerSelection: (String) -> Unit,
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
    onHideWithMask: (String) -> Unit,
    onRevealWithMask: (String) -> Unit,
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
    val clippingBase = active?.let { layers.getOrNull(layers.indexOf(it) + 1) }
    val clippingBases = remember(layers) {
        layers.zipWithNext().filter { (layer, _) -> layer.clipping }
            .associate { (layer, base) -> layer.id to base }
    }
    val selectedCount = layers.count { it.isSelected }
    val activeGroup = groups.firstOrNull { it.id == active?.groupId }
    var layerQuery by remember { mutableStateOf("") }
    var propertiesExpanded by remember { mutableStateOf(false) }
    var inspectorPage by remember { mutableIntStateOf(0) }
    var actionsMenuExpanded by remember { mutableStateOf(false) }
    val layerOutline = remember(layers, groups, layerQuery) {
        buildLayerOutline(layers, groups, layerQuery)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Capas", color = StudioPalette.Text, style = MaterialTheme.typography.titleLarge)
                    Text(
                        active?.let { "${it.name} · ${layers.size} capas" } ?: "${layers.size} capas · $selectedCount seleccionadas",
                        color = StudioPalette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCreateGroup) { Icon(Icons.Outlined.Layers, "Crear grupo", tint = StudioPalette.TextMuted) }
                IconButton(onClick = onAdd, modifier = Modifier.editorTutorialAnchor("layer_add")) { Icon(Icons.Outlined.Add, "Añadir capa", tint = StudioPalette.TextMuted) }
            }
            if (active != null) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LayerQuickChip(
                            icon = if (active.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            label = if (active.visible) "Visible" else "Oculta",
                            selected = active.visible,
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleVisibility(active.id) },
                        )
                        LayerQuickChip(
                            icon = if (active.alphaLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            label = "Pintar dentro",
                            selected = active.alphaLocked,
                            modifier = Modifier.weight(1f),
                            onClick = { onAlphaLock(active.id, !active.alphaLocked) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LayerQuickChip(
                            icon = Icons.Outlined.AutoFixNormal,
                            label = when {
                                active.editingMask -> "Salir de máscara"
                                active.hasMask -> "Editar máscara"
                                else -> "Crear máscara"
                            },
                            selected = active.editingMask,
                            modifier = Modifier.weight(1.15f),
                            onClick = {
                                if (active.hasMask) onEditMask(active.id, !active.editingMask) else onAddMask()
                            },
                        )
                        LayerQuickChip(
                            icon = Icons.Outlined.Layers,
                            label = "Recorte",
                            selected = active.clipping,
                            enabled = clippingBase != null || active.clipping,
                            anchorId = "layer_clipping",
                            modifier = Modifier.weight(1f),
                            onClick = { onClipping(active.id, !active.clipping) },
                        )
                        LayerQuickChip(
                            icon = Icons.Outlined.ArrowDownward,
                            label = "Bajar",
                            anchorId = "layer_down",
                            modifier = Modifier.weight(.8f),
                            onClick = onMoveDown,
                        )
                    }
                }
            }
            if (active?.clipping == true && clippingBase != null) {
                Text(
                    "Recortada a «${clippingBase.name}»",
                    modifier = Modifier.padding(top = 6.dp),
                    color = StudioPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (layers.size >= 6) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = layerQuery,
                    onValueChange = { layerQuery = it.take(48) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, "Buscar capas") },
                    placeholder = { Text("Buscar capas") },
                )
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { propertiesExpanded = !propertiesExpanded },
                color = StudioPalette.SurfaceHover,
                shape = RoundedCornerShape(9.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text("Ajustes de la capa activa", color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(if (propertiesExpanded) "Cerrar" else "Abrir", color = StudioPalette.Accent, fontSize = 11.sp)
                }
            }
            if (propertiesExpanded) {
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LayerInspectorTab("General", inspectorPage == 0, Modifier.weight(1f)) { inspectorPage = 0 }
                    LayerInspectorTab("Recorte", inspectorPage == 1, Modifier.weight(1f)) { inspectorPage = 1 }
                    LayerInspectorTab("Máscara", inspectorPage == 2, Modifier.weight(1f)) { inspectorPage = 2 }
                }
                Spacer(Modifier.height(7.dp))
                Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
                    if (active != null) when (inspectorPage) {
                        0 -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val modes = LayerBlendMode.values()
                                    onBlendMode(active.id, modes[(active.blendMode.ordinal + 1) % modes.size])
                                },
                                color = StudioPalette.SurfaceRaised,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(active.blendMode.displayName(), color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.weight(1f))
                                    Text("Modo de mezcla", color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            SettingSlider("Opacidad de capa", active.opacity, 0f..1f, "${(active.opacity * 100).toInt()}%") {
                                onOpacity(active.id, it)
                            }
                            LayerConceptToggle(
                                title = "Pintar dentro de esta capa",
                                explanation = "Evita salirte: el pincel solo modifica los píxeles que ya existen aquí.",
                                checked = active.alphaLocked,
                                onCheckedChange = { onAlphaLock(active.id, it) },
                            )
                            activeGroup?.let { group ->
                                Spacer(Modifier.height(7.dp))
                                SettingSlider("Opacidad del grupo", group.opacity, 0f..1f, "${(group.opacity * 100).toInt()}%") {
                                    onGroupOpacity(group.id, it)
                                }
                            }
                        }
                        1 -> {
                            if (clippingBase == null && !active.clipping) {
                                Text("Añade una capa debajo para usarla como base del recorte.", color = StudioPalette.TextMuted)
                            } else {
                                LayerConceptToggle(
                                    title = clippingBase?.let { "Recortar a «${it.name}»" } ?: "Recorte activo",
                                    explanation = if (clippingBase?.visible == false) {
                                        "La capa base está oculta. Muéstrala para ver el resultado."
                                    } else {
                                        "La capa de abajo hace de molde. Puedes pintar aquí; lo que quede fuera de su forma no se verá."
                                    },
                                    checked = active.clipping,
                                    onCheckedChange = { onClipping(active.id, it) },
                                )
                            }
                        }
                        else -> {
                            if (active.hasMask) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = StudioPalette.AccentSoft,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Accent),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(
                                            if (active.maskEnabled) "Máscara activa · ${active.maskStrength}%" else "Máscara pausada",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            "Oculta partes de esta capa sin borrarlas. Al editarla puedes ajustar su intensidad.",
                                            color = StudioPalette.Text,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { onHideWithMask(active.id) }, modifier = Modifier.weight(1f)) {
                                                Text("Ocultar")
                                            }
                                            TextButton(onClick = { onRevealWithMask(active.id) }, modifier = Modifier.weight(1f)) {
                                                Text("Recuperar")
                                            }
                                        }
                                        if (active.editingMask) {
                                            Button(
                                                onClick = { onEditMask(active.id, false) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("Volver a pintar la capa")
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(7.dp))
                                LayerConceptToggle(
                                    title = "Usar máscara",
                                    explanation = "Desactívala para mostrar la capa completa sin perder la máscara.",
                                    checked = active.maskEnabled,
                                    onCheckedChange = { onToggleMask(active.id) },
                                )
                                TextButton(onClick = onDeleteMask) {
                                    Text("Quitar máscara")
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().editorTutorialAnchor("mask_add"),
                                    color = StudioPalette.SurfaceRaised,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Ocultar partes sin borrar", color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "Crea una máscara y pinta sobre lo que quieres ocultar. Puedes recuperar cualquier parte después.",
                                            color = StudioPalette.TextMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        TextButton(onClick = onAddMask) {
                                            Text("Crear máscara")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = StudioPalette.Border)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(layerOutline, key = { it.key }) { entry ->
                when (entry) {
                    is LayerOutlineEntry.Group -> LayerGroupRow(
                        group = entry.group,
                        onToggleVisibility = onToggleGroupVisibility,
                        onToggleCollapsed = onToggleGroupCollapsed,
                    )
                    is LayerOutlineEntry.Layer -> LayerRow(
                        layer = entry.layer,
                        clippingBase = clippingBases[entry.layer.id],
                        onSelect = onSelectLayer,
                        onToggleSelection = onToggleLayerSelection,
                        onToggleVisibility = onToggleVisibility,
                        onToggleMaskEditing = onEditMask,
                        onMove = { id, up ->
                            onSelectLayer(id)
                            if (up) onMoveUp() else onMoveDown()
                        },
                        indentDepth = entry.indentDepth,
                    )
                }
            }
            if (layerOutline.isEmpty()) {
                item("empty") {
                    Text(
                        "No hay capas que coincidan con la búsqueda.",
                        color = StudioPalette.TextMuted,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            item("paper") {
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
        }
        HorizontalDivider(color = StudioPalette.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LayerFooterAction(Icons.Outlined.Add, "Nueva", true, "layer_add", onAdd)
            LayerFooterAction(Icons.Outlined.ContentCopy, "Duplicar", active != null, null, onDuplicate)
            LayerFooterAction(Icons.Outlined.MoreHoriz, "Nombre", active != null, null, onRename)
            Box {
                LayerFooterAction(Icons.Outlined.Tune, "Más", active != null, null) { actionsMenuExpanded = true }
                DropdownMenu(expanded = actionsMenuExpanded, onDismissRequest = { actionsMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Subir capa") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                        onClick = { actionsMenuExpanded = false; onMoveUp() },
                        modifier = Modifier.editorTutorialAnchor("layer_up"),
                    )
                    DropdownMenuItem(
                        text = { Text("Bajar capa") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                        onClick = { actionsMenuExpanded = false; onMoveDown() },
                        modifier = Modifier.editorTutorialAnchor("layer_down"),
                    )
                    DropdownMenuItem(
                        text = { Text("Agrupar selección") },
                        leadingIcon = { Icon(Icons.Outlined.Layers, null) },
                        onClick = { actionsMenuExpanded = false; onCreateGroup() },
                    )
                    if (activeGroup != null) {
                        DropdownMenuItem(
                            text = { Text("Sacar del grupo") },
                            leadingIcon = { Icon(Icons.Outlined.Remove, null) },
                            onClick = { actionsMenuExpanded = false; onUngroup() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Limpiar contenido") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                        onClick = { actionsMenuExpanded = false; onClear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar capa") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { actionsMenuExpanded = false; onDelete() },
                    )
                }
            }
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
    LayerBlendMode.SOFT_LIGHT -> "Luz suave"
    LayerBlendMode.HARD_LIGHT -> "Luz fuerte"
    LayerBlendMode.DIFFERENCE -> "Diferencia"
    LayerBlendMode.COLOR_DODGE -> "Sobreexponer color"
    LayerBlendMode.COLOR_BURN -> "Subexponer color"
}

@Composable
private fun LayerInspectorTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics {
                contentDescription = "Ajustes $label"
                this.selected = selected
                role = Role.Tab
            }
            .clickable(onClick = onClick),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) StudioPalette.Accent else StudioPalette.Border,
        ),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else StudioPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LayerQuickChip(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    anchorId: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp)
            .let { if (anchorId != null) it.editorTutorialAnchor(anchorId) else it }
            .semantics {
                contentDescription = label
                this.selected = selected
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) StudioPalette.Accent else StudioPalette.Border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (selected) Color.White else StudioPalette.TextMuted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) Color.White else StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LayerConceptToggle(
    title: String,
    explanation: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        color = if (checked) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) StudioPalette.Accent else StudioPalette.Border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
                Text(explanation, color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
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
                when {
                    label.contains("Resultado") -> Icons.Outlined.Visibility
                    label.contains("Viendo") -> Icons.Outlined.VisibilityOff
                    checked -> Icons.Outlined.Brush
                    else -> Icons.Outlined.AutoFixNormal
                },
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
        modifier = Modifier.fillMaxWidth().padding(start = (group.depth * 14).dp),
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
    clippingBase: LayerUiModel?,
    onSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleMaskEditing: (String, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    indentDepth: Int = 0,
) {
    var dragDistance by remember(layer.id) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentDepth * 18).dp)
            .semantics {
                contentDescription = "Capa ${layer.name}"
                selected = layer.isSelected
                stateDescription = buildString {
                    append(if (layer.visible) "Visible" else "Oculta")
                    append(", ${(layer.opacity * 100).toInt()} por ciento")
                    if (layer.isActive) append(if (layer.editingMask) ", pintando en la máscara" else ", pintando en la capa")
                    if (layer.hasMask) append(if (layer.maskEnabled) ", máscara al ${layer.maskStrength}%" else ", máscara pausada")
                    if (layer.alphaLocked) append(", pintar dentro")
                    if (layer.clipping) append(", ${clippingBase?.let { "recortada a ${it.name}" } ?: "recorte sin base"}")
                }
            }
            .clickable(role = Role.Button) { onSelect(layer.id) },
        color = when {
            layer.isActive -> StudioPalette.SurfaceHover
            layer.isSelected -> StudioPalette.SurfaceRaised
            else -> StudioPalette.SurfaceRaised
        },
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (layer.isActive) Color(0xFF74A3FF) else StudioPalette.Border),
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (layer.clipping) {
                Column(
                    modifier = Modifier.width(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(2.dp).height(13.dp).background(StudioPalette.Accent))
                    Icon(
                        Icons.Outlined.ArrowDownward,
                        null,
                        tint = StudioPalette.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            IconButton(onClick = { onToggleSelection(layer.id) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (layer.isSelected) Icons.Outlined.SelectAll else Icons.Outlined.RadioButtonUnchecked,
                    if (layer.isSelected) "Quitar ${layer.name} de la selección" else "Añadir ${layer.name} a la selección",
                    tint = if (layer.isSelected) Color.White else StudioPalette.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(
                onClick = { onToggleVisibility(layer.id) },
                modifier = Modifier.size(34.dp).let { if (layer.isActive) it.editorTutorialAnchor("layer_visibility") else it },
            ) {
                Icon(
                    if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if (layer.visible) "Ocultar ${layer.name}" else "Mostrar ${layer.name}",
                    tint = if (layer.isActive) Color.White else StudioPalette.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Surface(
                onClick = {
                    if (layer.editingMask) onToggleMaskEditing(layer.id, false) else onSelect(layer.id)
                },
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Pintar en la capa ${layer.name}" },
                color = StudioPalette.SurfaceHover,
                shape = RoundedCornerShape(7.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (layer.isActive && !layer.editingMask) 2.dp else 1.dp,
                    if (layer.isActive && !layer.editingMask) StudioPalette.Accent else StudioPalette.Border,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Image, null, tint = StudioPalette.TextMuted, modifier = Modifier.size(20.dp))
                }
            }
            if (layer.hasMask) {
                Spacer(Modifier.width(5.dp))
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (layer.editingMask) "Dejar de editar máscara de ${layer.name}"
                            else "Editar máscara de ${layer.name}"
                            selected = layer.editingMask
                        }
                        .clickable { onToggleMaskEditing(layer.id, !layer.editingMask) },
                    color = if (layer.maskEnabled) Color(0xFFE7E7E7) else StudioPalette.SurfaceHover,
                    shape = RoundedCornerShape(7.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (layer.editingMask) StudioPalette.Accent else StudioPalette.Border,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (layer.maskEnabled) Icons.Outlined.AutoFixNormal else Icons.Outlined.VisibilityOff,
                            null,
                            tint = if (layer.maskEnabled) Color(0xFF394450) else StudioPalette.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(layer.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val attributes = buildList {
                    add("${(layer.opacity * 100).toInt()}%")
                    if (layer.alphaLocked) add("Pintar dentro")
                    if (layer.hasMask) add(
                        when {
                            layer.editingMask -> "Editando máscara"
                            !layer.maskEnabled -> "Máscara pausada"
                            else -> "Máscara ${layer.maskStrength}%"
                        },
                    )
                }.joinToString(" · ")
                Text(attributes, color = if (layer.isActive) Color.White.copy(alpha = .72f) else StudioPalette.TextMuted, fontSize = 10.sp)
                if (layer.clipping) {
                    val relation = clippingBase?.let { "Recorte → ${it.name}" } ?: "Recorte sin base"
                    Text(
                        if (clippingBase?.visible == false) "$relation (oculta)" else relation,
                        color = StudioPalette.Accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Outlined.DragHandle,
                "Mantén y arrastra para ordenar ${layer.name}",
                tint = StudioPalette.TextMuted,
                modifier = Modifier
                    .size(34.dp)
                    .pointerInput(layer.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragDistance = 0f },
                            onDragCancel = { dragDistance = 0f },
                            onDragEnd = { dragDistance = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDistance += dragAmount.y
                                if (abs(dragDistance) >= 42.dp.toPx()) {
                                    onMove(layer.id, dragDistance < 0f)
                                    dragDistance = 0f
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun LayerAction(icon: ImageVector, description: String, anchorId: String? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).let { if (anchorId != null) it.editorTutorialAnchor(anchorId) else it }) {
        Icon(icon, description, tint = StudioPalette.TextMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LayerFooterAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    anchorId: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .heightIn(min = 54.dp)
            .let { if (anchorId != null) it.editorTutorialAnchor(anchorId) else it }
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) StudioPalette.TextMuted else StudioPalette.TextMuted.copy(alpha = .35f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (enabled) StudioPalette.TextMuted else StudioPalette.TextMuted.copy(alpha = .35f),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}
