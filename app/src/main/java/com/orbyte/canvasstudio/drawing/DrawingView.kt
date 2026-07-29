package com.orbyte.canvasstudio.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import android.view.View
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Properties
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.orbyte.canvasstudio.model.ProjectRepository
import com.orbyte.canvasstudio.model.ProjectVersionStore
import com.orbyte.canvasstudio.model.constrainCanvasSize
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DrawingView(context: Context) : View(context) {
    private data class LayerData(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        var visible: Boolean = true,
        var opacity: Float = 1f,
        var blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
        var alphaLocked: Boolean = false,
        var clipping: Boolean = false,
        var groupId: String? = null,
        var maskEnabled: Boolean = true,
        var editingMask: Boolean = false,
        val surface: SparseTileSurface,
        val baseTileDirectory: File,
        var maskSurface: SparseTileSurface? = null,
        var maskBaseTileDirectory: File? = null,
        val commands: MutableList<DrawCommand> = mutableListOf(),
        val maskCommands: MutableList<DrawCommand> = mutableListOf(),
    )

    private data class LayerGroupData(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        var visible: Boolean = true,
        var opacity: Float = 1f,
        var collapsed: Boolean = false,
        var parentGroupId: String? = null,
    )

    private enum class HistoryTarget { CONTENT, MASK }

    private data class HistoryEntry(
        val layerId: String,
        val target: HistoryTarget,
        val commands: List<DrawCommand>,
    )

    private val workspacePaint = Paint().apply { color = Color.rgb(24, 27, 32) }
    private val pagePaint = Paint().apply { color = Color.rgb(250, 249, 246) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val checkerLight = Paint().apply { color = Color.rgb(242, 242, 239) }
    private val checkerDark = Paint().apply { color = Color.rgb(231, 232, 229) }
    // Tiles share exact edges. Bilinear sampling blends the transparent outer pixel of a tile
    // with its neighbour at fractional zoom and produces a visible 512 px checker/grid.
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clippingMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val rasterMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val sourceOverXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    private val multiplyXfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val screenXfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val tipBitmapCache = object : LinkedHashMap<String, Bitmap>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val remove = size > 8
            if (remove) eldest?.value?.recycle()
            return remove
        }
    }
    private data class GrainTextureEntry(
        val bitmap: Bitmap,
        val shader: BitmapShader,
    )
    private val grainTextureCache =
        object : LinkedHashMap<BrushTextureKey, GrainTextureEntry>(12, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<BrushTextureKey, GrainTextureEntry>?,
            ): Boolean {
                val remove = size > 12
                if (remove) eldest?.value?.bitmap?.recycle()
                return remove
            }
        }
    private val grainShaderMatrix = Matrix()
    private var cachedMaskFilterKey: Int = Int.MIN_VALUE
    private var cachedMaskFilter: BlurMaskFilter? = null
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val deferredStrokeOverlay = object : Drawable() {
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.concat(transform)
            canvas.clipRect(documentBounds)
            drawDeferredStrokePreview(canvas)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Android")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
    private val cursorOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 16, 18, 22)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cursorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.25f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 48, 58, 72)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val symmetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 47, 117, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val selectionShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 20, 24, 30)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 255, 180, 70)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(185, 47, 117, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 47, 117, 255)
        style = Paint.Style.FILL
        textSize = 11f
    }

    private var documentWidth = 1800
    private var documentHeight = 1200
    private var documentBounds = RectF(0f, 0f, documentWidth.toFloat(), documentHeight.toFloat())
    @Volatile private var documentGeneration = 0L
    private val layers = mutableListOf<LayerData>()
    private val layerGroups = mutableListOf<LayerGroupData>()
    private var activeLayerId: String = ""
    private val selectedLayerIds = linkedSetOf<String>()
    private val undoStack = mutableListOf<HistoryEntry>()
    private val redoStack = mutableListOf<HistoryEntry>()

    private val transform = Matrix()
    private val inverse = Matrix()
    private var currentScale = 1f
    private var currentRotationDegrees = 0f
    private var fittedOnce = false
    private var navigationActive = false
    private var navigationInitialized = false
    private var lastGestureCentroidX = 0f
    private var lastGestureCentroidY = 0f
    private var lastGestureSpan = 0f
    private var lastGestureAngle = 0f
    private var hoverX = 0f
    private var hoverY = 0f
    private var hoverVisible = false
    private var gridVisible = false
    private var rulersVisible = false
    private var rulersUseCentimeters = false
    private val verticalRulerGuides = mutableListOf<Float>()
    private val horizontalRulerGuides = mutableListOf<Float>()
    private var draggedRulerGuideAxis = 0
    private var draggedRulerGuideIndex = -1
    private var documentDpi = 300
    private var angleSnappingEnabled = false
    private var perspectiveSnappingEnabled = false
    private var verticalSymmetry = false
    private var radialSymmetryCount = 1
    private var guideMode = GuideMode.NONE
    private var perspectiveEditing = false
    private var perspectivePoint1X = documentWidth * 0.5f
    private var perspectivePoint1Y = documentHeight * 0.42f
    private var perspectivePoint2X = documentWidth * 0.88f
    private var perspectivePoint2Y = documentHeight * 0.42f
    private var draggedPerspectivePoint = 0
    private val prefetchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "canvas-tile-prefetch").apply { isDaemon = true }
    }
    private val prefetchGeneration = AtomicInteger(0)
    @Volatile private var prefetchInFlight = false
    private var lastPrefetchBounds = RectF()
    private val prefetchedBounds = RectF()
    private val saveLock = Any()
    @Volatile private var saveGeneration = 0
    private var lastEngineStatus = ""
    private var engineStatusUpdatePosted = false
    private var lastEngineStatusUpdateUptime = 0L
    private val globalTileCacheBudgetBytes: Long =
        (Runtime.getRuntime().maxMemory() * 0.12)
            .toLong()
            .coerceIn(20L * 1024L * 1024L, 80L * 1024L * 1024L)

    private var activeStrokePoints: MutableList<StrokePoint>? = null
    private var activeInputTool: DrawingTool? = null
    private var activeStrokeSettings: BrushSettings? = null
    private var activeStrokeDefersRaster: Boolean = false
    private var activeRenderedPointCount: Int = 0
    private var strokeFramePosted: Boolean = false
    private var shapeStart: StrokePoint? = null
    private var shapeEnd: StrokePoint? = null
    private var selectionPath: Path? = null
    private var selectionInverted = false
    private var selectionFeatherPx = 0f
    private var selectionPoints: MutableList<StrokePoint>? = null
    private var selectionStart: StrokePoint? = null
    private var selectionEnd: StrokePoint? = null
    private var transformBitmap: Bitmap? = null
    private var transformSourcePath: Path? = null
    private var transformSourcePoints: List<StrokePoint> = emptyList()
    private var transformSourceBounds: RectF? = null
    private val selectionTransform = Matrix()
    private var transformGestureInitialized = false
    private var transformLastCentroidX = 0f
    private var transformLastCentroidY = 0f
    private var transformLastSpan = 0f
    private var transformLastAngle = 0f

    var tool: DrawingTool = DrawingTool.BRUSH
    var brushSettings: BrushSettings = BrushSettings()
    var onLayersChanged: ((List<LayerUiModel>) -> Unit)? = null
    var onLayerGroupsChanged: ((List<LayerGroupUiModel>) -> Unit)? = null
    var onDocumentChanged: (() -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null
    var onZoomChanged: ((Int) -> Unit)? = null
    var onRotationChanged: ((Int) -> Unit)? = null
    var onToolShortcut: ((DrawingTool) -> Unit)? = null
    var onBrushSettingsShortcut: ((BrushSettings) -> Unit)? = null
    var onSaveShortcut: (() -> Unit)? = null
    var onTogglePanelsShortcut: (() -> Unit)? = null
    var onProjectSaved: ((Boolean) -> Unit)? = null
    var onEngineStatusChanged: ((String) -> Unit)? = null
    var onEngineMessage: ((String) -> Unit)? = null
    var onSelectionChanged: ((Boolean) -> Unit)? = null
    var onRasterFramePresented: (() -> Unit)? = null
    var usePlatformLowLatencyPreview: Boolean = false

    fun supportsPlatformLowLatencyPreview(): Boolean =
        tool == DrawingTool.BRUSH &&
            shouldDeferActiveRaster(brushSettings) &&
            platformInkPreviewCompatible(brushSettings.kind)

    private fun hasCompatiblePlatformPreview(settings: BrushSettings): Boolean =
        usePlatformLowLatencyPreview && platformInkPreviewCompatible(settings.kind)

    fun platformPreviewSizePx(): Float = brushSettings.sizePx * currentScale

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        overlay.add(deferredStrokeOverlay)
        createEmptyDocument(documentWidth, documentHeight)
    }

    fun configureDocument(width: Int, height: Int) {
        val (safeWidth, safeHeight) = constrainCanvasSize(width, height)
        if (safeWidth == documentWidth && safeHeight == documentHeight) return
        createEmptyDocument(safeWidth, safeHeight)
    }


    fun seedDemoArtwork(styleName: String?) {
        if (styleName == null) return
        val base = layers.firstOrNull() ?: return
        val generation = documentGeneration
        base.name = "Arte base"
        val paintLayer = createLayer("Pinceladas")
        layers += paintLayer
        selectOnly(paintLayer.id)
        updateCacheBudgets()
        notifyLayers()

        // A 4K demo seeds and persists many tiles. Keeping this off the UI thread avoids a
        // multi-second stall when a gallery example is opened on a tablet.
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            if (generation != documentGeneration) return@Thread
            val w = documentWidth.toFloat()
            val h = documentHeight.toFloat()
            // Demo gradients must be painted in one coordinate space. Rendering them tile by
            // tile restarts the shader at each 512 px origin and leaves a visible grid.
            val demoBitmap = Bitmap.createBitmap(documentWidth, documentHeight, Bitmap.Config.ARGB_8888)
            try {
            val canvas = Canvas(demoBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            fun gradient(top: Int, bottom: Int) {
                paint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
            }

            when (styleName) {
            "MOUNTAIN" -> {
                gradient(Color.rgb(103, 155, 207), Color.rgb(237, 196, 139))
                paint.color = Color.rgb(230, 235, 235)
                val back = Path().apply {
                    moveTo(0f, h * .82f)
                    lineTo(w * .25f, h * .37f)
                    lineTo(w * .42f, h * .72f)
                    lineTo(w * .63f, h * .18f)
                    lineTo(w, h * .78f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                canvas.drawPath(back, paint)
                paint.color = Color.rgb(42, 60, 62)
                val front = Path().apply {
                    moveTo(0f, h)
                    lineTo(w * .31f, h * .55f)
                    lineTo(w * .49f, h * .86f)
                    lineTo(w * .66f, h * .43f)
                    lineTo(w, h)
                    close()
                }
                canvas.drawPath(front, paint)
                paint.color = Color.rgb(248, 219, 154)
                canvas.drawCircle(w * .82f, h * .21f, h * .11f, paint)
            }
            "PORTRAIT" -> {
                gradient(Color.rgb(91, 113, 105), Color.rgb(186, 159, 126))
                paint.color = Color.rgb(228, 191, 167)
                canvas.drawOval(RectF(w * .32f, h * .14f, w * .68f, h * .78f), paint)
                paint.color = Color.rgb(69, 78, 73)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = h * .1f
                canvas.drawArc(RectF(w * .28f, h * .08f, w * .72f, h * .68f), 188f, 168f, false, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(40, 48, 46)
                canvas.drawCircle(w * .45f, h * .44f, h * .018f, paint)
                canvas.drawCircle(w * .57f, h * .44f, h * .018f, paint)
                paint.color = Color.rgb(118, 62, 72)
                val body = Path().apply {
                    moveTo(w * .2f, h)
                    lineTo(w * .37f, h * .63f)
                    lineTo(w * .63f, h * .63f)
                    lineTo(w * .8f, h)
                    close()
                }
                canvas.drawPath(body, paint)
            }
            "CITY" -> {
                gradient(Color.rgb(39, 54, 99), Color.rgb(239, 148, 126))
                paint.color = Color.rgb(23, 32, 53)
                repeat(11) { index ->
                    val bw = w / 12f
                    val left = index * bw + bw * .2f
                    val top = h * (.34f + (index % 4) * .09f)
                    canvas.drawRect(left, top, left + bw * .72f, h, paint)
                    paint.color = Color.rgb(248, 201, 112)
                    canvas.drawRect(left + bw * .16f, top + h * .12f, left + bw * .27f, top + h * .16f, paint)
                    paint.color = Color.rgb(23, 32, 53)
                }
            }
            "FOREST" -> {
                gradient(Color.rgb(27, 45, 77), Color.rgb(94, 59, 105))
                repeat(14) { index ->
                    val x = w * (index / 13f)
                    val treeHeight = h * (.34f + (index % 3) * .08f)
                    paint.color = if (index % 2 == 0) Color.rgb(15, 36, 32) else Color.rgb(21, 48, 41)
                    val tree = Path().apply {
                        moveTo(x, h)
                        lineTo(x + w * .04f, h - treeHeight)
                        lineTo(x + w * .09f, h)
                        close()
                    }
                    canvas.drawPath(tree, paint)
                }
                paint.color = Color.rgb(120, 225, 204)
                canvas.drawCircle(w * .71f, h * .58f, h * .065f, paint)
            }
            "CHARACTER" -> {
                gradient(Color.rgb(218, 189, 153), Color.rgb(126, 96, 126))
                paint.color = Color.rgb(216, 155, 139)
                canvas.drawCircle(w * .5f, h * .36f, h * .19f, paint)
                paint.color = Color.rgb(61, 53, 67)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = h * .08f
                canvas.drawArc(RectF(w * .31f, h * .12f, w * .69f, h * .58f), 190f, 160f, false, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(131, 61, 82)
                val body = Path().apply {
                    moveTo(w * .24f, h)
                    lineTo(w * .38f, h * .57f)
                    lineTo(w * .62f, h * .57f)
                    lineTo(w * .77f, h)
                    close()
                }
                canvas.drawPath(body, paint)
            }
            else -> {
                canvas.drawColor(Color.rgb(219, 210, 190))
                paint.color = Color.rgb(103, 94, 80)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, min(w, h) * .003f)
                repeat(10) { index ->
                    val y = h * (.16f + index * .065f)
                    canvas.drawLine(w * .08f, y, w * (.45f + (index % 3) * .16f), y + h * .035f, paint)
                }
                canvas.drawRect(RectF(w * .57f, h * .27f, w * .82f, h * .72f), paint)
                canvas.drawCircle(w * .38f, h * .51f, min(w, h) * .08f, paint)
                paint.style = Paint.Style.FILL
            }
        }
            base.surface.replaceFromBitmap(demoBitmap, markProjectDirty = false)
            } finally {
                demoBitmap.recycle()
            }
            if (generation != documentGeneration) return@Thread
            check(base.surface.copyCurrentTo(base.baseTileDirectory))
            base.surface.markAllProjectDirty()
            post {
                if (generation != documentGeneration) return@post
                notifyLayers()
                invalidate()
            }
        }.apply {
            name = "canvas-demo-seed"
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private fun createEmptyDocument(width: Int, height: Int) {
        documentGeneration += 1L
        clearActiveStrokeState()
        cancelSelectionTransform(rebuild = false)
        selectionPath = null
        selectionInverted = false
        selectionFeatherPx = 0f
        selectionPoints = null
        selectionStart = null
        selectionEnd = null
        onSelectionChanged?.invoke(false)
        prefetchGeneration.incrementAndGet()
        layers.flatMap { it.commands + it.maskCommands }.distinctBy { it.id }.forEach(::recycleCommand)
        layers.forEach { layer ->
            layer.surface.recycle()
            layer.maskSurface?.recycle()
        }
        sessionTileRoot().deleteRecursively()
        layers.clear()
        layerGroups.clear()
        selectedLayerIds.clear()
        verticalRulerGuides.clear()
        horizontalRulerGuides.clear()
        undoStack.clear()
        clearRedoHistory()
        documentWidth = width
        documentHeight = height
        documentBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        perspectivePoint1X = width * 0.5f
        perspectivePoint1Y = height * 0.42f
        perspectivePoint2X = width * 0.88f
        perspectivePoint2Y = height * 0.42f
        perspectiveEditing = false
        draggedPerspectivePoint = 0
        val layer = createLayer("Capa 1")
        layers += layer
        selectOnly(layer.id)
        fittedOnce = false
        updateCacheBudgets()
        notifyLayers()
        requestLayout()
        invalidate()
    }

    private fun createLayer(
        name: String,
        id: String = UUID.randomUUID().toString(),
        sourceDirectory: File? = null,
    ): LayerData {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val layerRoot = File(sessionTileRoot(), safeId)
        val workingDirectory = File(layerRoot, "work")
        val baseDirectory = File(layerRoot, "base")
        if (sourceDirectory?.isDirectory == true) {
            check(TileStorage.copyTileDirectory(sourceDirectory, workingDirectory))
            check(TileStorage.copyTileDirectory(sourceDirectory, baseDirectory))
        } else {
            workingDirectory.mkdirs()
            baseDirectory.mkdirs()
        }
        return LayerData(
            id = id,
            name = name,
            surface = SparseTileSurface(
                width = documentWidth,
                height = documentHeight,
                workingDirectory = workingDirectory,
                cacheBudgetBytes = perSurfaceCacheBudget(),
            ),
            baseTileDirectory = baseDirectory,
        )
    }

    private fun createMaskSurface(
        layerId: String,
        sourceDirectory: File? = null,
    ): Pair<SparseTileSurface, File> {
        val safeId = layerId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val maskRoot = File(sessionTileRoot(), "$safeId-mask")
        val workingDirectory = File(maskRoot, "work")
        val baseDirectory = File(maskRoot, "base")
        if (sourceDirectory?.isDirectory == true) {
            check(TileStorage.copyTileDirectory(sourceDirectory, workingDirectory))
            check(TileStorage.copyTileDirectory(sourceDirectory, baseDirectory))
        } else {
            workingDirectory.mkdirs()
            baseDirectory.mkdirs()
        }
        return SparseTileSurface(
            width = documentWidth,
            height = documentHeight,
            workingDirectory = workingDirectory,
            cacheBudgetBytes = perSurfaceCacheBudget(),
        ) to baseDirectory
    }

    private fun sessionTileRoot(): File =
        File(context.cacheDir, "canvasstudio/session-tiles").apply { mkdirs() }

    private fun layerTileDirectory(projectDirectory: File, layerId: String): File =
        File(projectDirectory, "layers/${layerId}/tiles")

    private fun maskTileDirectory(projectDirectory: File, layerId: String): File =
        File(projectDirectory, "layers/${layerId}/mask")

    private fun activeHistoryTarget(layer: LayerData): HistoryTarget =
        if (layer.editingMask && layer.maskSurface != null) HistoryTarget.MASK else HistoryTarget.CONTENT

    private fun surfaceFor(layer: LayerData, target: HistoryTarget): SparseTileSurface = when (target) {
        HistoryTarget.CONTENT -> layer.surface
        HistoryTarget.MASK -> layer.maskSurface ?: layer.surface
    }

    private fun commandsFor(layer: LayerData, target: HistoryTarget): MutableList<DrawCommand> = when (target) {
        HistoryTarget.CONTENT -> layer.commands
        HistoryTarget.MASK -> layer.maskCommands
    }

    private fun baseDirectoryFor(layer: LayerData, target: HistoryTarget): File = when (target) {
        HistoryTarget.CONTENT -> layer.baseTileDirectory
        HistoryTarget.MASK -> layer.maskBaseTileDirectory ?: layer.baseTileDirectory
    }

    private fun recordCommands(layer: LayerData, commands: List<DrawCommand>) {
        val target = activeHistoryTarget(layer)
        commandsFor(layer, target) += commands
        undoStack += HistoryEntry(layer.id, target, commands)
        clearRedoHistory()
    }

    private fun markLayerDirty(layer: LayerData, bounds: RectF, target: HistoryTarget = activeHistoryTarget(layer)) {
        surfaceFor(layer, target).markProjectDirty(bounds)
        scheduleEngineStatusUpdate()
    }

    private fun markLayerFullyDirty(layer: LayerData, target: HistoryTarget = HistoryTarget.CONTENT) {
        surfaceFor(layer, target).markAllProjectDirty()
        scheduleEngineStatusUpdate()
    }

    private fun commandBounds(command: DrawCommand): RectF = when (command) {
        is StrokeCommand -> {
            val points = command.points
            if (points.isEmpty()) {
                RectF()
            } else {
                val radius = maximumBrushExtent(command.settings)
                RectF(
                    points.minOf { it.x } - radius,
                    points.minOf { it.y } - radius,
                    points.maxOf { it.x } + radius,
                    points.maxOf { it.y } + radius,
                )
            }
        }
        is ShapeCommand -> {
            val radius = command.settings.sizePx * 0.7f + 8f
            RectF(
                min(command.startX, command.endX) - radius,
                min(command.startY, command.endY) - radius,
                max(command.startX, command.endX) + radius,
                max(command.startY, command.endY) + radius,
            )
        }
        is GradientCommand -> {
            if (command.clipPoints.isNotEmpty() && !command.clipInverted) {
                RectF(
                    command.clipPoints.minOf { it.x },
                    command.clipPoints.minOf { it.y },
                    command.clipPoints.maxOf { it.x },
                    command.clipPoints.maxOf { it.y },
                )
            } else {
                RectF(documentBounds)
            }
        }
        is PixelPatchCommand -> RectF(
            command.left,
            command.top,
            command.left + command.bitmap.width,
            command.top + command.bitmap.height,
        )
        is TransformSelectionCommand -> {
            val matrix = Matrix().apply { setValues(command.matrixValues) }
            val destination = RectF(0f, 0f, command.bitmap.width.toFloat(), command.bitmap.height.toFloat())
            matrix.mapRect(destination)
            RectF(
                min(command.sourceBoundsLeft, destination.left),
                min(command.sourceBoundsTop, destination.top),
                max(command.sourceBoundsRight, destination.right),
                max(command.sourceBoundsBottom, destination.bottom),
            )
        }
    }

    private fun combinedBounds(commands: List<DrawCommand>): RectF {
        val valid = commands.map(::commandBounds).filterNot { it.isEmpty }
        if (valid.isEmpty()) return RectF()
        return RectF(valid.first()).apply {
            valid.drop(1).forEach { union(it) }
            intersect(documentBounds)
        }
    }

    private fun strokeSegmentBounds(from: StrokePoint, to: StrokePoint, settings: BrushSettings): RectF {
        val radius = maximumBrushExtent(settings)
        return RectF(
            min(from.x, to.x) - radius,
            min(from.y, to.y) - radius,
            max(from.x, to.x) + radius,
            max(from.y, to.y) + radius,
        )
    }

    private fun maximumBrushExtent(settings: BrushSettings): Float {
        val tilt = max(
            settings.tiltResponse,
            settings.dynamicsProfile.tiltSize,
        ).coerceIn(0f, 1f)
        val primaryRadius = settings.sizePx * .5f * (1f + tilt * .9f)
        val scatterExtent = primaryRadius * settings.scatter.coerceIn(0f, 1f)
        val particleExtent = if (
            settings.tipProfile.shape == BrushTipShape.PARTICLE ||
            settings.kind == BrushKind.CHARCOAL ||
            settings.kind == BrushKind.CHALK
        ) {
            primaryRadius * (1.3f + settings.scatter.coerceIn(0f, 1f))
        } else {
            primaryRadius
        }
        val dual = settings.dualBrushProfile
        val dualExtent = if (dual.enabled) {
            primaryRadius * (
                dual.sizeScale.coerceIn(.1f, 2f) +
                    dual.offset.coerceIn(-1f, 1f).let(::abs) +
                    dual.scatter.coerceIn(0f, 1f)
                )
        } else {
            0f
        }
        val blurExtent = settings.sizePx * (1f - settings.hardness.coerceIn(0f, 1f)) * .24f
        val wetExtent = settings.sizePx * settings.renderProfile.bleed.coerceIn(0f, 1f) * .28f
        return max(max(primaryRadius + scatterExtent, particleExtent), dualExtent) +
            blurExtent + wetExtent + 10f
    }

    private fun isStampBrush(kind: BrushKind): Boolean = when (kind) {
        BrushKind.PENCIL,
        BrushKind.INK,
        BrushKind.MARKER,
        BrushKind.PAINT,
        BrushKind.AIRBRUSH,
        BrushKind.CHARCOAL,
        BrushKind.CHALK,
        BrushKind.DRY_BRUSH,
        BrushKind.BRISTLE,
        BrushKind.WATERCOLOR,
        BrushKind.OIL,
        -> true
    }

    private fun shouldDeferActiveRaster(settings: BrushSettings): Boolean =
        (isStampBrush(settings.kind) && settings.sizePx >= 48f) ||
            requiresFinalStrokeRebuild(settings)

    private fun minimumInputDistance(settings: BrushSettings): Float {
        return inputSamplingDistance(settings, isStampBrush(settings.kind))
    }

    private fun requiresFinalStrokeRebuild(settings: BrushSettings): Boolean {
        // Replaying every overlapping textured stroke after each pen-up grows quadratically.
        // Stamp brushes already look correct incrementally; reserve final taper correction for
        // line-based pencil and ink brushes where the visual difference is noticeable.
        if (isStampBrush(settings.kind)) return false
        return settings.taperEnd > 0f || settings.velocitySize > 0f
    }

    private fun stampSpacing(settings: BrushSettings): Float {
        val requested = settings.spacing.coerceIn(0.025f, 0.5f)
        val floor = when (settings.kind) {
            BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH -> 0.13f
            BrushKind.BRISTLE -> 0.1f
            BrushKind.PAINT, BrushKind.WATERCOLOR, BrushKind.OIL -> 0.09f
            BrushKind.AIRBRUSH -> 0.1f
            BrushKind.MARKER -> 0.08f
            else -> requested
        }
        return max(requested, floor)
    }

    private fun perSurfaceCacheBudget(): Long {
        val surfaceCount = layers.sumOf { 1 + if (it.maskSurface != null) 1 else 0 }.coerceAtLeast(1)
        return (globalTileCacheBudgetBytes / surfaceCount)
            .coerceAtLeast(1L * 1024L * 1024L)
    }

    private fun updateCacheBudgets() {
        val budget = perSurfaceCacheBudget()
        layers.forEach { layer ->
            layer.surface.setCacheBudget(budget)
            layer.maskSurface?.setCacheBudget(budget)
        }
    }

    private fun updateEngineStatus() {
        val stats = layers.flatMap { layer -> buildList {
            add(layer.surface.stats())
            layer.maskSurface?.let { add(it.stats()) }
        } }
        val residentTiles = stats.sumOf { it.residentTiles }
        val storedTiles = stats.sumOf { it.storedTiles }
        val dirtyTiles = stats.sumOf { it.dirtyTiles }
        val cacheMegabytes = stats.sumOf { it.cacheBytes } / (1024L * 1024L)
        val detail = when {
            dirtyTiles > 0 -> "$residentTiles residentes · $dirtyTiles pendientes · ${cacheMegabytes} MB"
            storedTiles > 0 -> "$residentTiles residentes / $storedTiles en disco · ${cacheMegabytes} MB"
            else -> "superficie dispersa preparada"
        }
        val status = "Tiles ${TileStorage.TILE_SIZE} · $detail"
        if (status != lastEngineStatus) {
            lastEngineStatus = status
            onEngineStatusChanged?.invoke(status)
        }
    }

    private fun layerLimit(): Int =
        (globalTileCacheBudgetBytes / (1024L * 1024L)).toInt().coerceIn(16, 64)

    private fun canAllocateAnotherLayer(extraSurfaces: Int = 1): Boolean =
        layers.size + extraSurfaces <= layerLimit()


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Z) {
            if (event.isShiftPressed) redo() else undo()
            return true
        }
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Y) {
            redo()
            return true
        }
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_S) {
            onSaveShortcut?.invoke()
            return true
        }
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_A) {
            selectAll()
            return true
        }
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_D) {
            deselect()
            return true
        }
        val shortcutTool = ShortcutPreferences.toolForKey(
            profile = ShortcutPreferences.load(context),
            keyCode = keyCode,
            shiftPressed = event.isShiftPressed,
        )
        if (shortcutTool != null) {
            tool = shortcutTool
            onToolShortcut?.invoke(shortcutTool)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET || keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET) {
            val multiplier = if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET) 0.86f else 1.16f
            brushSettings = brushSettings.copy(sizePx = (brushSettings.sizePx * multiplier).coerceIn(2f, 180f))
            onBrushSettingsShortcut?.invoke(brushSettings)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            deselect()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            onTogglePanelsShortcut?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_0) {
            resetView()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            deleteSelectionContents()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        prefetchGeneration.incrementAndGet()
        prefetchExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        deferredStrokeOverlay.setBounds(0, 0, w, h)
        if (w > 0 && h > 0 && !fittedOnce) {
            resetView()
            fittedOnce = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), workspacePaint)
        canvas.save()
        canvas.concat(transform)
        canvas.save()
        canvas.clipRect(documentBounds)
        drawCheckerboard(canvas)
        val visibleBounds = visibleDocumentRect()
        prefetchedBounds.set(
            (visibleBounds.left - TileStorage.TILE_SIZE).coerceAtLeast(0f),
            (visibleBounds.top - TileStorage.TILE_SIZE).coerceAtLeast(0f),
            (visibleBounds.right + TileStorage.TILE_SIZE).coerceAtMost(documentWidth.toFloat()),
            (visibleBounds.bottom + TileStorage.TILE_SIZE).coerceAtMost(documentHeight.toFloat()),
        )
        layers.forEachIndexed { index, layer ->
            drawVisibleLayer(canvas, visibleBounds, index, layer)
        }
        drawGridOverlay(canvas)
        drawPerspectiveGuides(canvas)
        drawRulers(canvas)
        drawShapePreview(canvas)
        drawSelectionOverlay(canvas)
        canvas.restore()
        canvas.drawRect(documentBounds, borderPaint)
        canvas.restore()
        drawBrushCursor(canvas)
        scheduleTilePrefetch(prefetchedBounds, force = hasMissingVisibleTiles(prefetchedBounds))
        scheduleEngineStatusUpdate()
        onRasterFramePresented?.invoke()
    }

    private fun hasMissingVisibleTiles(bounds: RectF): Boolean = layers
        .filter(::isLayerEffectivelyVisible)
        .any { layer ->
            layer.surface.hasMissingVisibleTiles(bounds) ||
                (layer.maskEnabled && layer.maskSurface?.hasMissingVisibleTiles(bounds) == true)
        }

    private fun groupFor(layer: LayerData): LayerGroupData? =
        layer.groupId?.let { groupId -> layerGroups.firstOrNull { it.id == groupId } }

    private fun groupAncestors(groupId: String?): Sequence<LayerGroupData> = sequence {
        val visited = mutableSetOf<String>()
        var currentId = groupId
        while (currentId != null && visited.add(currentId)) {
            val group = layerGroups.firstOrNull { it.id == currentId } ?: break
            yield(group)
            currentId = group.parentGroupId
        }
    }

    private fun isLayerEffectivelyVisible(layer: LayerData): Boolean =
        layer.visible && groupAncestors(layer.groupId).all { it.visible }

    private fun effectiveLayerOpacity(layer: LayerData): Float =
        groupAncestors(layer.groupId)
            .fold(layer.opacity) { opacity, group -> opacity * group.opacity }
            .coerceIn(0f, 1f)

    private fun applyRasterMaskVisible(canvas: Canvas, layer: LayerData, bounds: RectF) {
        if (!layer.maskEnabled) return
        val mask = layer.maskSurface ?: return
        rasterMaskPaint.alpha = 255
        mask.drawVisible(canvas, bounds, rasterMaskPaint)
    }

    private fun drawVisibleLayer(canvas: Canvas, visibleBounds: RectF, index: Int, layer: LayerData) {
        if (!isLayerEffectivelyVisible(layer)) return
        val hasMask = layer.maskEnabled && layer.maskSurface != null
        val hasClippingBase = layer.clipping && index > 0

        // Avoid an off-screen saveLayer for ordinary layers. Textured brushes already put
        // pressure on the GPU; allocating a temporary layer for every raster layer made the
        // editor progressively less responsive on devices such as the Galaxy Tab S8.
        if (!hasMask && !hasClippingBase) {
            configureLayerPaint(layer, effectiveLayerOpacity(layer))
            layer.surface.drawVisible(canvas, visibleBounds, layerPaint)
            if (layer.id == activeLayerId && !layer.editingMask) drawTransformPreview(canvas, layerPaint)
            return
        }

        val checkpoint = canvas.saveLayer(documentBounds, null)
        configureLayerPaint(layer, effectiveLayerOpacity(layer))
        layer.surface.drawVisible(canvas, visibleBounds, layerPaint)
        if (layer.id == activeLayerId && !layer.editingMask) drawTransformPreview(canvas, layerPaint)
        if (hasMask) applyRasterMaskVisible(canvas, layer, visibleBounds)
        if (hasClippingBase) {
            val base = layers[index - 1]
            if (!isLayerEffectivelyVisible(base)) {
                canvas.restoreToCount(checkpoint)
                return
            }
            clippingMaskPaint.alpha = (effectiveLayerOpacity(base) * 255f).toInt().coerceIn(0, 255)
            base.surface.drawVisible(canvas, visibleBounds, clippingMaskPaint)
            if (base.maskEnabled) base.maskSurface?.drawVisible(canvas, visibleBounds, rasterMaskPaint)
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun scheduleEngineStatusUpdate() {
        if (engineStatusUpdatePosted) return
        val now = SystemClock.uptimeMillis()
        val delay = (ENGINE_STATUS_INTERVAL_MS - (now - lastEngineStatusUpdateUptime)).coerceAtLeast(0L)
        engineStatusUpdatePosted = true
        postDelayed({
            engineStatusUpdatePosted = false
            lastEngineStatusUpdateUptime = SystemClock.uptimeMillis()
            updateEngineStatus()
        }, delay)
    }

    private fun scheduleTilePrefetch(bounds: RectF, force: Boolean = false) {
        if (bounds.isEmpty) return
        val movement = max(
            abs(bounds.centerX() - lastPrefetchBounds.centerX()),
            abs(bounds.centerY() - lastPrefetchBounds.centerY()),
        )
        val sizeChanged = abs(bounds.width() - lastPrefetchBounds.width()) > TileStorage.TILE_SIZE / 2f ||
            abs(bounds.height() - lastPrefetchBounds.height()) > TileStorage.TILE_SIZE / 2f
        if (!force && !lastPrefetchBounds.isEmpty && movement < TileStorage.TILE_SIZE / 3f && !sizeChanged) return
        if (prefetchInFlight) return
        lastPrefetchBounds = RectF(bounds)
        val generation = prefetchGeneration.incrementAndGet()
        prefetchInFlight = true
        val snapshot = layers
            .filter(::isLayerEffectivelyVisible)
            .flatMap { layer -> buildList {
                add(layer.surface)
                if (layer.maskEnabled) layer.maskSurface?.let { add(it) }
            } }
        val requestedBounds = RectF(bounds)
        prefetchExecutor.execute {
            try {
                snapshot.forEach { surface ->
                    if (generation != prefetchGeneration.get()) return@execute
                    surface.prefetch(requestedBounds)
                }
                if (generation == prefetchGeneration.get()) {
                    post {
                        updateEngineStatus()
                        postInvalidateOnAnimation()
                    }
                }
            } finally {
                post { prefetchInFlight = false }
            }
        }
    }

    private fun drawPerspectiveGuides(canvas: Canvas) {
        if (guideMode == GuideMode.NONE) return
        val vanishingPoints = when (guideMode) {
            GuideMode.PERSPECTIVE_ONE_POINT -> listOf(perspectivePoint1X to perspectivePoint1Y)
            GuideMode.PERSPECTIVE_TWO_POINT -> listOf(
                perspectivePoint1X to perspectivePoint1Y,
                perspectivePoint2X to perspectivePoint2Y,
            )
            GuideMode.NONE -> emptyList()
        }
        val horizonY = vanishingPoints.map { it.second }.average().toFloat()
        canvas.drawLine(0f, horizonY, documentWidth.toFloat(), horizonY, guidePaint)
        val edgePoints = buildList {
            repeat(9) { index ->
                val x = documentWidth * index / 8f
                add(x to 0f)
                add(x to documentHeight.toFloat())
            }
            repeat(7) { index ->
                val y = documentHeight * index / 6f
                add(0f to y)
                add(documentWidth.toFloat() to y)
            }
        }
        vanishingPoints.forEachIndexed { index, (vx, vy) ->
            edgePoints.forEach { (x, y) -> canvas.drawLine(vx, vy, x, y, guidePaint) }
            val radius = if (perspectiveEditing) 15f else 9f
            canvas.drawCircle(vx, vy, radius / currentScale.coerceAtLeast(.1f), symmetryPaint)
            if (perspectiveEditing) {
                val labelPaint = Paint(guidePaint).apply {
                    style = Paint.Style.FILL
                    textSize = 20f / currentScale.coerceAtLeast(.1f)
                }
                canvas.drawText("P${index + 1}", vx + radius, vy - radius, labelPaint)
            }
        }
    }

    private fun handlePerspectiveEdit(event: MotionEvent): Boolean {
        if (!perspectiveEditing || guideMode == GuideMode.NONE) return false
        val point = mapToDocument(event.x, event.y)
        val x = point[0].coerceIn(0f, documentWidth.toFloat())
        val y = point[1].coerceIn(0f, documentHeight.toFloat())
        val threshold = 42f / currentScale.coerceAtLeast(.1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val d1 = hypot(x - perspectivePoint1X, y - perspectivePoint1Y)
                val d2 = hypot(x - perspectivePoint2X, y - perspectivePoint2Y)
                draggedPerspectivePoint = when {
                    d1 <= threshold -> 1
                    guideMode == GuideMode.PERSPECTIVE_TWO_POINT && d2 <= threshold -> 2
                    else -> 0
                }
                parent?.requestDisallowInterceptTouchEvent(draggedPerspectivePoint != 0)
            }
            MotionEvent.ACTION_MOVE -> {
                when (draggedPerspectivePoint) {
                    1 -> {
                        perspectivePoint1X = x
                        perspectivePoint1Y = y
                    }
                    2 -> {
                        perspectivePoint2X = x
                        perspectivePoint2Y = y
                    }
                }
                if (draggedPerspectivePoint != 0) invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val changed = draggedPerspectivePoint != 0
                draggedPerspectivePoint = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                if (changed) onDocumentChanged?.invoke()
            }
        }
        // While guide editing is active, consume the gesture even when the user misses a
        // handle. This prevents an accidental paint stroke below the perspective overlay.
        return true
    }

    private fun drawSelectionOverlay(canvas: Canvas) {
        val path = currentSelectionDisplayPath() ?: run {
            val start = selectionStart
            val end = selectionEnd
            if (start != null && end != null && tool == DrawingTool.SELECT_RECTANGLE) {
                Path().apply { addRect(normalizedRect(start, end), Path.Direction.CW) }
            } else if (start != null && end != null && tool == DrawingTool.SELECT_ELLIPSE) {
                pathFromPoints(ellipseSelectionPoints(normalizedRect(start, end)), close = true)
            } else if (selectionPoints?.isNotEmpty() == true) {
                pathFromPoints(selectionPoints.orEmpty(), close = false)
            } else null
        } ?: return
        val scale = currentScale.coerceAtLeast(.08f)
        selectionShadowPaint.strokeWidth = 4f / scale
        selectionPaint.strokeWidth = 2f / scale
        selectionShadowPaint.pathEffect = DashPathEffect(floatArrayOf(12f / scale, 8f / scale), 0f)
        selectionPaint.pathEffect = DashPathEffect(floatArrayOf(12f / scale, 8f / scale), 0f)
        canvas.drawPath(path, selectionShadowPaint)
        canvas.drawPath(path, selectionPaint)
    }

    private fun drawTransformPreview(canvas: Canvas, paint: Paint) {
        val bitmap = transformBitmap ?: return
        canvas.drawBitmap(bitmap, selectionTransform, paint)
    }

    private fun visibleDocumentRect(): RectF {
        if (width <= 0 || height <= 0) return RectF(documentBounds)
        transform.invert(inverse)
        val points = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
        )
        inverse.mapPoints(points)
        val left = min(min(points[0], points[2]), min(points[4], points[6])).coerceIn(0f, documentWidth.toFloat())
        val top = min(min(points[1], points[3]), min(points[5], points[7])).coerceIn(0f, documentHeight.toFloat())
        val right = max(max(points[0], points[2]), max(points[4], points[6])).coerceIn(0f, documentWidth.toFloat())
        val bottom = max(max(points[1], points[3]), max(points[5], points[7])).coerceIn(0f, documentHeight.toFloat())
        return RectF(left, top, right, bottom)
    }

    private fun drawCheckerboard(canvas: Canvas) {
        canvas.drawRect(documentBounds, pagePaint)
        val visible = visibleDocumentRect()
        val tile = 48f
        val firstColumn = (visible.left / tile).toInt().coerceAtLeast(0)
        val lastColumn = ceil(visible.right / tile).toInt().coerceAtMost(ceil(documentWidth / tile).toInt())
        val firstRow = (visible.top / tile).toInt().coerceAtLeast(0)
        val lastRow = ceil(visible.bottom / tile).toInt().coerceAtMost(ceil(documentHeight / tile).toInt())
        for (row in firstRow until lastRow) {
            for (column in firstColumn until lastColumn) {
                val x = column * tile
                val y = row * tile
                canvas.drawRect(
                    x,
                    y,
                    min(x + tile, documentWidth.toFloat()),
                    min(y + tile, documentHeight.toFloat()),
                    if ((row + column) % 2 == 0) checkerLight else checkerDark,
                )
            }
        }
    }

    private fun drawGridOverlay(canvas: Canvas) {
        if (gridVisible) {
            val spacing = when {
                currentScale >= 1.4f -> 64f
                currentScale >= 0.65f -> 128f
                else -> 256f
            }
            var x = 0f
            while (x <= documentWidth) {
                canvas.drawLine(x, 0f, x, documentHeight.toFloat(), gridPaint)
                x += spacing
            }
            var y = 0f
            while (y <= documentHeight) {
                canvas.drawLine(0f, y, documentWidth.toFloat(), y, gridPaint)
                y += spacing
            }
        }
        if (verticalSymmetry) {
            val centerX = documentWidth / 2f
            canvas.drawLine(centerX, 0f, centerX, documentHeight.toFloat(), symmetryPaint)
        }
        if (radialSymmetryCount > 1) {
            val centerX = documentWidth / 2f
            val centerY = documentHeight / 2f
            val radius = hypot(documentWidth.toFloat(), documentHeight.toFloat())
            repeat(radialSymmetryCount) { index ->
                val angle = (2.0 * PI * index / radialSymmetryCount).toFloat()
                canvas.drawLine(
                    centerX,
                    centerY,
                    centerX + cos(angle) * radius,
                    centerY + sin(angle) * radius,
                    symmetryPaint,
                )
            }
        }
    }

    private fun drawRulers(canvas: Canvas) {
        if (!rulersVisible) return
        val scale = currentScale.coerceAtLeast(.1f)
        val baseSpacing = if (rulersUseCentimeters) documentDpi / 2.54f else 100f
        val spacing = baseSpacing * when {
            scale >= 1.2f -> 1f
            scale >= .55f -> if (rulersUseCentimeters) 2f else 2.5f
            else -> 5f
        }
        val tick = 12f / scale
        rulerPaint.strokeWidth = 1.5f / scale
        rulerTextPaint.textSize = 11f / scale
        var x = 0f
        while (x <= documentWidth) {
            canvas.drawLine(x, 0f, x, tick, rulerPaint)
            if (x > 0f) {
                val label = if (rulersUseCentimeters) {
                    "${(x * 2.54f / documentDpi).toInt()} cm"
                } else x.toInt().toString()
                canvas.drawText(label, x + 4f / scale, tick, rulerTextPaint)
            }
            x += spacing
        }
        var y = 0f
        while (y <= documentHeight) {
            canvas.drawLine(0f, y, tick, y, rulerPaint)
            if (y > 0f) {
                val label = if (rulersUseCentimeters) {
                    "${(y * 2.54f / documentDpi).toInt()} cm"
                } else y.toInt().toString()
                canvas.drawText(label, tick + 3f / scale, y - 3f / scale, rulerTextPaint)
            }
            y += spacing
        }
        rulerPaint.strokeWidth = 1.25f / scale
        verticalRulerGuides.forEach { guideX ->
            canvas.drawLine(guideX, 0f, guideX, documentHeight.toFloat(), rulerPaint)
        }
        horizontalRulerGuides.forEach { guideY ->
            canvas.drawLine(0f, guideY, documentWidth.toFloat(), guideY, rulerPaint)
        }
    }

    private fun handleRulerGuide(event: MotionEvent): Boolean {
        if (!rulersVisible) return false
        val point = mapToDocument(event.x, event.y)
        val x = point[0]
        val y = point[1]
        val scale = currentScale.coerceAtLeast(.1f)
        val rulerBand = 28f / scale
        val hitRadius = 18f / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val verticalIndex = verticalRulerGuides.indices.minByOrNull {
                    abs(verticalRulerGuides[it] - x)
                }?.takeIf { abs(verticalRulerGuides[it] - x) <= hitRadius }
                val horizontalIndex = horizontalRulerGuides.indices.minByOrNull {
                    abs(horizontalRulerGuides[it] - y)
                }?.takeIf { abs(horizontalRulerGuides[it] - y) <= hitRadius }
                when {
                    verticalIndex != null -> {
                        draggedRulerGuideAxis = 1
                        draggedRulerGuideIndex = verticalIndex
                    }
                    horizontalIndex != null -> {
                        draggedRulerGuideAxis = 2
                        draggedRulerGuideIndex = horizontalIndex
                    }
                    y in 0f..rulerBand && x >= rulerBand -> {
                        verticalRulerGuides += x.coerceIn(0f, documentWidth.toFloat())
                        draggedRulerGuideAxis = 1
                        draggedRulerGuideIndex = verticalRulerGuides.lastIndex
                    }
                    x in 0f..rulerBand && y >= rulerBand -> {
                        horizontalRulerGuides += y.coerceIn(0f, documentHeight.toFloat())
                        draggedRulerGuideAxis = 2
                        draggedRulerGuideIndex = horizontalRulerGuides.lastIndex
                    }
                    else -> return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (draggedRulerGuideAxis) {
                    1 -> if (draggedRulerGuideIndex in verticalRulerGuides.indices) {
                        verticalRulerGuides[draggedRulerGuideIndex] = x
                    }
                    2 -> if (draggedRulerGuideIndex in horizontalRulerGuides.indices) {
                        horizontalRulerGuides[draggedRulerGuideIndex] = y
                    }
                    else -> return false
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedRulerGuideAxis == 0) return false
                if (draggedRulerGuideAxis == 1 &&
                    verticalRulerGuides.getOrNull(draggedRulerGuideIndex)?.let {
                        it !in 0f..documentWidth.toFloat()
                    } == true
                ) {
                    verticalRulerGuides.removeAt(draggedRulerGuideIndex)
                } else if (draggedRulerGuideAxis == 2 &&
                    horizontalRulerGuides.getOrNull(draggedRulerGuideIndex)?.let {
                        it !in 0f..documentHeight.toFloat()
                    } == true
                ) {
                    horizontalRulerGuides.removeAt(draggedRulerGuideIndex)
                }
                draggedRulerGuideAxis = 0
                draggedRulerGuideIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                onDocumentChanged?.invoke()
                invalidate()
                return true
            }
        }
        return draggedRulerGuideAxis != 0
    }

    private fun drawShapePreview(canvas: Canvas) {
        val start = shapeStart ?: return
        val end = shapeEnd ?: return
        if (tool == DrawingTool.GRADIENT) {
            previewPaint.shader = null
            previewPaint.color = brushSettings.color
            previewPaint.strokeWidth = 2f / currentScale.coerceAtLeast(.1f)
            canvas.drawLine(start.x, start.y, end.x, end.y, previewPaint)
            previewPaint.style = Paint.Style.FILL
            canvas.drawCircle(start.x, start.y, 6f / currentScale.coerceAtLeast(.1f), previewPaint)
            previewPaint.style = Paint.Style.STROKE
            canvas.drawCircle(end.x, end.y, 6f / currentScale.coerceAtLeast(.1f), previewPaint)
            return
        }
        configurePaint(previewPaint, tool, brushSettings, 1f, 0f, isPreview = true)
        val selection = selectionPath
        val selectionSave = selection?.let {
            beginSelectionMask(canvas, it, selectionInverted, selectionFeatherPx)
        }
        symmetryMatrices().forEach { matrix ->
            drawShapeWithPaint(
                canvas,
                tool,
                transformPoint(start, matrix),
                transformPoint(end, matrix),
                previewPaint,
            )
        }
        if (selection != null && selectionSave != null) {
            endSelectionMask(canvas, selectionSave, selection, selectionInverted, selectionFeatherPx)
        }
    }

    private fun drawShapeWithPaint(
        canvas: Canvas,
        drawingTool: DrawingTool,
        start: StrokePoint,
        end: StrokePoint,
        paint: Paint,
    ) {
        when (drawingTool) {
            DrawingTool.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            DrawingTool.RECTANGLE -> canvas.drawRect(normalizedRect(start, end), paint)
            DrawingTool.ELLIPSE -> canvas.drawOval(normalizedRect(start, end), paint)
            else -> Unit
        }
    }

    private fun symmetryMatrices(): List<Matrix> {
        val centerX = documentWidth / 2f
        val centerY = documentHeight / 2f
        if (radialSymmetryCount > 1) {
            return List(radialSymmetryCount) { index ->
                Matrix().apply { setRotate(360f * index / radialSymmetryCount, centerX, centerY) }
            }
        }
        if (verticalSymmetry) {
            return listOf(
                Matrix(),
                Matrix().apply { setScale(-1f, 1f, centerX, centerY) },
            )
        }
        return listOf(Matrix())
    }

    private fun transformPoint(point: StrokePoint, matrix: Matrix): StrokePoint {
        val values = floatArrayOf(point.x, point.y)
        matrix.mapPoints(values)
        return point.copy(x = values[0], y = values[1])
    }

    private fun transformCommand(command: DrawCommand, matrix: Matrix, keepId: Boolean): DrawCommand = when (command) {
        is StrokeCommand -> command.copy(
            id = if (keepId) command.id else UUID.randomUUID().toString(),
            points = command.points.map { transformPoint(it, matrix) },
        )
        is ShapeCommand -> {
            val start = transformPoint(
                StrokePoint(command.startX, command.startY, 1f, 0f, 0L),
                matrix,
            )
            val end = transformPoint(
                StrokePoint(command.endX, command.endY, 1f, 0f, 0L),
                matrix,
            )
            command.copy(
                id = if (keepId) command.id else UUID.randomUUID().toString(),
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
            )
        }
        else -> command
    }

    private fun symmetryCommands(command: DrawCommand): List<DrawCommand> =
        symmetryMatrices().mapIndexed { index, matrix -> transformCommand(command, matrix, keepId = index == 0) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val canceledPalmPointer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
            event.flags and MotionEvent.FLAG_CANCELED == MotionEvent.FLAG_CANCELED
        if (canceledPalmPointer) {
            clearActiveStrokeState()
            shapeStart = null
            shapeEnd = null
            rebuildAllLayers()
            parent?.requestDisallowInterceptTouchEvent(false)
            onEngineMessage?.invoke("Contacto de palma ignorado.")
            return true
        }
        if (handlePerspectiveEdit(event)) return true
        if (handleRulerGuide(event)) return true
        val active = activeLayer()
        if (active?.editingMask == true && tool !in setOf(DrawingTool.BRUSH, DrawingTool.ERASER, DrawingTool.HAND)) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onEngineMessage?.invoke("La máscara se edita con Pincel o Borrador.")
            }
            return true
        }
        if (tool == DrawingTool.TRANSFORM && selectionPath != null) {
            hoverVisible = false
            handleSelectionTransformGesture(event)
            return true
        }
        val stylusIndex = (0 until event.pointerCount).firstOrNull { index ->
            val pointerTool = event.getToolType(index)
            pointerTool == MotionEvent.TOOL_TYPE_STYLUS || pointerTool == MotionEvent.TOOL_TYPE_ERASER
        }
        val stylusPresent = stylusIndex != null
        val activePointerIndex = stylusIndex ?: 0

        val oneFingerPan = tool == DrawingTool.HAND
        val navigationGesture = oneFingerPan || (!stylusPresent && (event.pointerCount >= 2 || navigationActive))
        if (navigationGesture) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                (activeStrokePoints != null || shapeStart != null)
            ) {
                val strokeHadRasterPreview = activeStrokePoints != null &&
                    !activeStrokeDefersRaster &&
                    activeStrokeSettings?.let(::shouldDeferActiveRaster) != true
                clearActiveStrokeState()
                shapeStart = null
                shapeEnd = null
                // A finger-down is kept as an overlay until it is known to be drawing.
                // Therefore the common two-finger navigation transition has not touched
                // the tile surfaces and must not rebuild the whole document. Rebuilding
                // here made every layer briefly disappear on real tablets.
                if (strokeHadRasterPreview) rebuildAllLayers()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            hoverVisible = false
            handleNavigationGesture(event)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) requestFocus()
        transform.invert(inverse)
        val current = mapToDocument(event.getX(activePointerIndex), event.getY(activePointerIndex))
        val pressure = event.getPressure(activePointerIndex).coerceIn(0f, 1f)
        val tilt = normalizedStylusTilt(
            event.getAxisValue(MotionEvent.AXIS_TILT, activePointerIndex),
        )
        val orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION, activePointerIndex)
        val eventTool = if (event.getToolType(activePointerIndex) == MotionEvent.TOOL_TYPE_ERASER) {
            DrawingTool.ERASER
        } else {
            tool
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        ) {
            sampleColor(current[0], current[1])
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                requestUnbufferedDispatch(event)
                val isInsideDocument = documentBounds.contains(current[0], current[1])
                if (!isInsideDocument) return true
                when (eventTool) {
                    DrawingTool.EYEDROPPER -> sampleColor(current[0], current[1])
                    DrawingTool.BRUSH, DrawingTool.ERASER -> beginStroke(
                        drawingTool = eventTool,
                        x = current[0],
                        y = current[1],
                        pressure = pressure,
                        tilt = tilt,
                        orientation = orientation,
                        time = event.eventTime,
                        deferUntilCommit = !stylusPresent,
                    )
                    DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.GRADIENT -> {
                        shapeStart = StrokePoint(current[0], current[1], pressure, tilt, event.eventTime)
                        shapeEnd = shapeStart
                    }
                    DrawingTool.FILL -> performFloodFill(current[0].toInt(), current[1].toInt())
                    DrawingTool.SELECT_RECTANGLE, DrawingTool.SELECT_ELLIPSE -> {
                        cancelSelectionTransform(rebuild = true)
                        selectionPath = null
                        onSelectionChanged?.invoke(false)
                        selectionStart = StrokePoint(current[0], current[1], pressure, tilt, event.eventTime)
                        selectionEnd = selectionStart
                        selectionPoints = null
                    }
                    DrawingTool.SELECT_LASSO -> {
                        cancelSelectionTransform(rebuild = true)
                        selectionPath = null
                        onSelectionChanged?.invoke(false)
                        selectionStart = null
                        selectionEnd = null
                        selectionPoints = mutableListOf(
                            StrokePoint(current[0], current[1], pressure, tilt, event.eventTime),
                        )
                    }
                    DrawingTool.TRANSFORM -> Unit
                    DrawingTool.HAND -> Unit
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (activeInputTool ?: eventTool) {
                    DrawingTool.BRUSH, DrawingTool.ERASER -> {
                        val points = activeStrokePoints ?: return true
                        for (historyIndex in 0 until event.historySize) {
                            val mapped = mapToDocument(
                                event.getHistoricalX(activePointerIndex, historyIndex),
                                event.getHistoricalY(activePointerIndex, historyIndex),
                            )
                            appendStrokePoint(
                                points,
                                mapped[0],
                                mapped[1],
                                event.getHistoricalPressure(
                                    activePointerIndex,
                                    historyIndex,
                                ).coerceIn(0f, 1f),
                                normalizedStylusTilt(
                                    event.getHistoricalAxisValue(
                                        MotionEvent.AXIS_TILT,
                                        activePointerIndex,
                                        historyIndex,
                                    ),
                                ),
                                event.getHistoricalAxisValue(
                                    MotionEvent.AXIS_ORIENTATION,
                                    activePointerIndex,
                                    historyIndex,
                                ),
                                event.getHistoricalEventTime(historyIndex),
                            )
                        }
                        appendStrokePoint(
                            points,
                            current[0],
                            current[1],
                            pressure,
                            tilt,
                            orientation,
                            event.eventTime,
                        )
                    }
                    DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.GRADIENT -> {
                        shapeEnd = snappedShapePoint(
                            x = current[0],
                            y = current[1],
                            pressure = pressure,
                            tilt = tilt,
                            time = event.eventTime,
                        )
                        invalidate()
                    }
                    DrawingTool.SELECT_RECTANGLE, DrawingTool.SELECT_ELLIPSE -> {
                        selectionEnd = StrokePoint(current[0], current[1], pressure, tilt, event.eventTime)
                        invalidate()
                    }
                    DrawingTool.SELECT_LASSO -> {
                        val points = selectionPoints ?: return true
                        val previous = points.last()
                        if (hypot(current[0] - previous.x, current[1] - previous.y) >= 2.5f / currentScale.coerceAtLeast(.1f)) {
                            points += StrokePoint(current[0], current[1], pressure, tilt, event.eventTime)
                            invalidate()
                        }
                    }
                    else -> Unit
                }
            }

            MotionEvent.ACTION_UP -> {
                when (activeInputTool ?: eventTool) {
                    DrawingTool.BRUSH, DrawingTool.ERASER -> finishStroke()
                    DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE -> finishShape()
                    DrawingTool.GRADIENT -> finishGradient()
                    DrawingTool.SELECT_RECTANGLE -> finishRectangleSelection()
                    DrawingTool.SELECT_ELLIPSE -> finishEllipseSelection()
                    DrawingTool.SELECT_LASSO -> finishLassoSelection()
                    else -> Unit
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                clearActiveStrokeState()
                shapeStart = null
                shapeEnd = null
                selectionStart = null
                selectionEnd = null
                selectionPoints = null
                rebuildAllLayers()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun snappedShapePoint(
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        time: Long,
    ): StrokePoint {
        val start = shapeStart ?: return StrokePoint(x, y, pressure, tilt, time)
        if (
            perspectiveSnappingEnabled &&
            guideMode != GuideMode.NONE &&
            tool in setOf(DrawingTool.LINE, DrawingTool.GRADIENT)
        ) {
            val vanishingPoints = if (guideMode == GuideMode.PERSPECTIVE_TWO_POINT) {
                listOf(perspectivePoint1X to perspectivePoint1Y, perspectivePoint2X to perspectivePoint2Y)
            } else {
                listOf(perspectivePoint1X to perspectivePoint1Y)
            }
            val candidate = vanishingPoints.mapNotNull { (vx, vy) ->
                val rayX = vx - start.x
                val rayY = vy - start.y
                val lengthSquared = rayX * rayX + rayY * rayY
                if (lengthSquared < 1f) null else {
                    val projection = ((x - start.x) * rayX + (y - start.y) * rayY) / lengthSquared
                    val snappedX = start.x + rayX * projection
                    val snappedY = start.y + rayY * projection
                    Triple(snappedX, snappedY, hypot(x - snappedX, y - snappedY))
                }
            }.minByOrNull { it.third }
            if (candidate != null) {
                return StrokePoint(candidate.first, candidate.second, pressure, tilt, time)
            }
        }
        if (!angleSnappingEnabled) return StrokePoint(x, y, pressure, tilt, time)
        val dx = x - start.x
        val dy = y - start.y
        return when (tool) {
            DrawingTool.LINE, DrawingTool.GRADIENT -> {
                val distance = hypot(dx, dy)
                val step = Math.PI / 12.0
                val angle = kotlin.math.round(atan2(dy.toDouble(), dx.toDouble()) / step) * step
                StrokePoint(
                    x = start.x + kotlin.math.cos(angle).toFloat() * distance,
                    y = start.y + kotlin.math.sin(angle).toFloat() * distance,
                    pressure = pressure,
                    tilt = tilt,
                    timestampMillis = time,
                )
            }
            DrawingTool.RECTANGLE, DrawingTool.ELLIPSE -> {
                val side = max(abs(dx), abs(dy))
                StrokePoint(
                    x = start.x + side * if (dx < 0f) -1f else 1f,
                    y = start.y + side * if (dy < 0f) -1f else 1f,
                    pressure = pressure,
                    tilt = tilt,
                    timestampMillis = time,
                )
            }
            else -> StrokePoint(x, y, pressure, tilt, time)
        }
    }

    private fun clearActiveStrokeState() {
        activeStrokePoints = null
        activeInputTool = null
        activeStrokeSettings = null
        activeStrokeDefersRaster = false
        activeRenderedPointCount = 0
        strokeFramePosted = false
        deferredStrokeOverlay.invalidateSelf()
    }

    private fun beginStroke(
        drawingTool: DrawingTool,
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
        time: Long,
        deferUntilCommit: Boolean = false,
    ) {
        val settings = brushSettings
        val points = mutableListOf(
            StrokePoint(x, y, pressure, tilt, time, orientation),
        )
        activeStrokePoints = points
        activeInputTool = drawingTool
        activeStrokeSettings = settings
        activeStrokeDefersRaster = deferUntilCommit
        activeRenderedPointCount = 1
        val layer = activeLayer() ?: run {
            clearActiveStrokeState()
            return
        }
        if (!layer.editingMask && layer.alphaLocked && drawingTool == DrawingTool.ERASER) {
            clearActiveStrokeState()
            onEngineMessage?.invoke("Desactiva Bloquear alfa para usar el borrador en esta capa.")
            return
        }
        if (activeStrokeDefersRaster || shouldDeferActiveRaster(settings)) {
            if (activeStrokeDefersRaster || !hasCompatiblePlatformPreview(settings)) {
                deferredStrokeOverlay.invalidateSelf()
            }
            return
        }
        val point = points.first()
        val matrices = symmetryMatrices()
        matrices.forEachIndexed { index, matrix ->
            val transformed = transformPoint(point, matrix)
            drawOnLayer(layer, strokeSegmentBounds(transformed, transformed, settings)) { canvas ->
                drawBrushStamp(
                    canvas = canvas,
                    x = transformed.x,
                    y = transformed.y,
                    pressure = pressure * taperFactor(settings, 0f),
                    tilt = tilt,
                    drawingTool = drawingTool,
                    settings = settings,
                    stampIndex = 0,
                    angleRadians = (2.0 * PI * index / max(1, matrices.size)).toFloat(),
                    orientation = orientation,
                )
            }
        }
        invalidate()
    }

    private fun appendStrokePoint(
        points: MutableList<StrokePoint>,
        rawX: Float,
        rawY: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
        time: Long,
    ) {
        val previous = points.lastOrNull() ?: return
        val settings = activeStrokeSettings ?: brushSettings
        val response = (1f - settings.stabilization.coerceIn(0f, 0.92f)) * 0.86f + 0.08f
        val x = previous.x + (rawX - previous.x) * response
        val y = previous.y + (rawY - previous.y) * response
        val distance = hypot(x - previous.x, y - previous.y)
        if (distance < minimumInputDistance(settings)) return
        points += StrokePoint(x, y, pressure, tilt, time, orientation)
        if (activeStrokeDefersRaster || shouldDeferActiveRaster(settings)) {
            if (activeStrokeDefersRaster || !hasCompatiblePlatformPreview(settings)) {
                deferredStrokeOverlay.invalidateSelf()
            }
        } else {
            scheduleActiveStrokeRender()
        }
    }

    /**
     * Preview large textured strokes without touching sparse tiles for every input sample.
     * The exact brush is rasterized once in finishStroke(), avoiding overdraw and tile churn.
     */
    private fun drawDeferredStrokePreview(canvas: Canvas) {
        val points = activeStrokePoints ?: return
        val settings = activeStrokeSettings ?: return
        if ((!activeStrokeDefersRaster && !shouldDeferActiveRaster(settings)) || points.isEmpty()) return
        val drawingTool = activeInputTool ?: tool
        val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (drawingTool == DrawingTool.ERASER) Color.WHITE else settings.color
            alpha = if (drawingTool == DrawingTool.ERASER) {
                150
            } else {
                (settings.opacity.coerceIn(0.15f, 1f) * 220f).toInt()
            }
        }
        symmetryMatrices().forEach { matrix ->
            var previous = transformPoint(points.first(), matrix)
            if (points.size == 1) {
                previewPaint.strokeWidth =
                    settings.sizePx * previous.pressure.coerceIn(settings.minSize, 1f)
                canvas.drawPoint(previous.x, previous.y, previewPaint)
            } else {
                for (index in 1 until points.size) {
                    val current = transformPoint(points[index], matrix)
                    previewPaint.strokeWidth = settings.sizePx *
                        ((previous.pressure + current.pressure) * 0.5f)
                            .coerceIn(settings.minSize, 1f)
                    canvas.drawLine(previous.x, previous.y, current.x, current.y, previewPaint)
                    previous = current
                }
            }
        }
    }

    private fun scheduleActiveStrokeRender() {
        if (strokeFramePosted) return
        strokeFramePosted = true
        postOnAnimation {
            strokeFramePosted = false
            renderActiveStrokePending(MAX_STROKE_SEGMENTS_PER_FRAME)
            val points = activeStrokePoints
            if (points != null && activeRenderedPointCount < points.size) {
                scheduleActiveStrokeRender()
            }
        }
    }

    private fun renderActiveStrokePending(maxSegments: Int) {
        val points = activeStrokePoints ?: return
        val layer = activeLayer() ?: return
        val settings = activeStrokeSettings ?: brushSettings
        val drawingTool = activeInputTool ?: tool
        val firstEndPointIndex = activeRenderedPointCount.coerceAtLeast(1)
        if (firstEndPointIndex >= points.size) return

        // Do not add an unbounded segment count directly to the current index.
        // finishStroke() intentionally requests every pending segment, and adding
        // Int.MAX_VALUE here used to overflow to a negative value on Android.
        val remainingSegments = points.size - firstEndPointIndex
        val requestedSegments = maxSegments.coerceAtLeast(1)
        val endExclusive = if (requestedSegments >= remainingSegments) {
            points.size
        } else {
            firstEndPointIndex + requestedSegments
        }
        val matrices = symmetryMatrices()

        matrices.forEach { matrix ->
            val transformedCapacity = (endExclusive - firstEndPointIndex + 1).coerceAtLeast(2)
            val transformed = ArrayList<StrokePoint>(transformedCapacity)
            for (pointIndex in (firstEndPointIndex - 1) until endExclusive) {
                transformed += transformPoint(points[pointIndex], matrix)
            }
            if (transformed.size < 2) return@forEach

            val bounds = strokeSegmentBounds(transformed[0], transformed[1], settings)
            for (index in 2 until transformed.size) {
                bounds.union(strokeSegmentBounds(transformed[index - 1], transformed[index], settings))
            }
            val pickupSurface = surfaceFor(layer, activeHistoryTarget(layer))
            val pickupColors = if (settings.renderProfile.colorPickup > .001f) {
                IntArray(transformed.size - 1) { localIndex ->
                    pickupSurface.samplePixel(
                        transformed[localIndex + 1].x,
                        transformed[localIndex + 1].y,
                    ) ?: 0
                }
            } else {
                null
            }
            drawOnLayer(layer, bounds) { canvas ->
                for (localIndex in 1 until transformed.size) {
                    val globalEndPointIndex = firstEndPointIndex - 1 + localIndex
                    val progress = ((globalEndPointIndex + 1) / 28f).coerceIn(0f, 0.5f)
                    drawStrokeSegment(
                        canvas = canvas,
                        from = transformed[localIndex - 1],
                        to = transformed[localIndex],
                        drawingTool = drawingTool,
                        settings = settings,
                        progress = progress,
                        segmentIndex = globalEndPointIndex - 1,
                        sampledColor = pickupColors?.get(localIndex - 1),
                    )
                }
            }
        }
        activeRenderedPointCount = endExclusive
        invalidate()
    }

    private fun finishStroke() {
        val points = activeStrokePoints ?: return
        val completedTool = activeInputTool ?: tool
        val settings = activeStrokeSettings ?: brushSettings
        val deferredRaster = activeStrokeDefersRaster || shouldDeferActiveRaster(settings)
        if (!deferredRaster) renderActiveStrokePending(Int.MAX_VALUE)
        val completedPoints = points.toList()
        clearActiveStrokeState()
        if (completedPoints.isEmpty()) return
        val command = StrokeCommand(
            points = completedPoints,
            tool = completedTool,
            settings = settings,
            clipPoints = persistentSelectionPoints(),
            clipInverted = selectionInverted,
            clipFeatherPx = selectionFeatherPx,
        )
        activeLayer()?.let { layer ->
            val commands = symmetryCommands(command)
            recordCommands(layer, commands)
            if (deferredRaster) {
                val surface = surfaceFor(layer, activeHistoryTarget(layer))
                commands.forEach { drawCommand(surface, it) }
            } else if (requiresFinalStrokeRebuild(command.settings)) {
                rebuildLayerRegion(layer, combinedBounds(commands))
            }
        }
        commitDocumentChange()
    }

    private fun finishShape() {
        val start = shapeStart
        val end = shapeEnd
        shapeStart = null
        shapeEnd = null
        if (start == null || end == null) return
        if (abs(start.x - end.x) + abs(start.y - end.y) < 1f) return
        val command = ShapeCommand(
            tool = tool,
            startX = start.x,
            startY = start.y,
            endX = end.x,
            endY = end.y,
            settings = brushSettings,
            clipPoints = persistentSelectionPoints(),
            clipInverted = selectionInverted,
            clipFeatherPx = selectionFeatherPx,
        )
        activeLayer()?.let { layer ->
            val commands = symmetryCommands(command)
            commands.forEach { drawCommand(layer.surface, it) }
            recordCommands(layer, commands)
        }
        commitDocumentChange()
    }

    private fun finishGradient() {
        val start = shapeStart
        val end = shapeEnd
        shapeStart = null
        shapeEnd = null
        if (start == null || end == null) return
        if (hypot(start.x - end.x, start.y - end.y) < 2f) return
        val color = brushSettings.color
        val transparent = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val command = GradientCommand(
            startX = start.x,
            startY = start.y,
            endX = end.x,
            endY = end.y,
            startColor = color,
            endColor = transparent,
            clipPoints = persistentSelectionPoints(),
            clipInverted = selectionInverted,
            clipFeatherPx = selectionFeatherPx,
        )
        activeLayer()?.let { layer ->
            drawCommand(layer.surface, command)
            recordCommands(layer, listOf(command))
            markLayerDirty(layer, commandBounds(command))
        }
        commitDocumentChange()
    }

    private fun finishRectangleSelection() {
        val start = selectionStart
        val end = selectionEnd
        selectionStart = null
        selectionEnd = null
        if (start == null || end == null) return
        val bounds = normalizedRect(start, end)
        if (bounds.width() < 2f || bounds.height() < 2f) {
            deselect()
            return
        }
        val points = mutableListOf(
            StrokePoint(bounds.left, bounds.top, 1f, 0f, 0L),
            StrokePoint(bounds.right, bounds.top, 1f, 0f, 0L),
            StrokePoint(bounds.right, bounds.bottom, 1f, 0f, 0L),
            StrokePoint(bounds.left, bounds.bottom, 1f, 0f, 0L),
        )
        selectionPoints = points
        selectionPath = pathFromPoints(points, close = true)
        selectionInverted = false
        selectionFeatherPx = 0f
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    private fun finishEllipseSelection() {
        val start = selectionStart
        val end = selectionEnd
        selectionStart = null
        selectionEnd = null
        if (start == null || end == null) return
        val bounds = normalizedRect(start, end)
        if (bounds.width() < 2f || bounds.height() < 2f) {
            deselect()
            return
        }
        val points = ellipseSelectionPoints(bounds)
        selectionPoints = points.toMutableList()
        selectionPath = pathFromPoints(points, close = true)
        selectionInverted = false
        selectionFeatherPx = 0f
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    private fun ellipseSelectionPoints(bounds: RectF, segments: Int = 48): List<StrokePoint> {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val rx = bounds.width() / 2f
        val ry = bounds.height() / 2f
        return List(segments.coerceAtLeast(12)) { index ->
            val angle = (Math.PI * 2.0 * index / segments.coerceAtLeast(12)).toFloat()
            StrokePoint(
                x = cx + kotlin.math.cos(angle) * rx,
                y = cy + kotlin.math.sin(angle) * ry,
                pressure = 1f,
                tilt = 0f,
                timestampMillis = 0L,
            )
        }
    }

    private fun finishLassoSelection() {
        val points = selectionPoints?.toList().orEmpty()
        if (points.size < 3) {
            deselect()
            return
        }
        selectionPath = pathFromPoints(points, close = true)
        selectionPoints = points.toMutableList()
        selectionInverted = false
        selectionFeatherPx = 0f
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    private fun pathFromPoints(points: List<StrokePoint>, close: Boolean): Path = Path().apply {
        points.firstOrNull()?.let { first ->
            moveTo(first.x, first.y)
            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
            if (close) close()
        }
    }

    private fun persistentSelectionPoints(): List<StrokePoint> =
        selectionPoints?.toList().orEmpty().takeIf { selectionPath != null }.orEmpty()

    private fun currentSelectionDisplayPath(): Path? {
        if (transformBitmap != null) {
            val bounds = transformSourceBounds ?: return null
            val transformed = transformSourcePoints.map { point ->
                val values = floatArrayOf(point.x - bounds.left, point.y - bounds.top)
                selectionTransform.mapPoints(values)
                point.copy(x = values[0], y = values[1])
            }
            return pathFromPoints(transformed, close = true)
        }
        return selectionPath?.let(::Path)
    }

    private fun beginSelectionTransform(): Boolean {
        if (transformBitmap != null) return true
        if (selectionInverted) {
            onEngineMessage?.invoke("Invierte nuevamente la selecciÃ³n antes de transformarla.")
            return false
        }
        val layer = activeLayer() ?: return false
        if (layer.alphaLocked) {
            onEngineMessage?.invoke("Desactiva Bloquear alfa para transformar la selección.")
            return false
        }
        val path = selectionPath ?: return false
        val bounds = RectF().also { path.computeBounds(it, true) }
        val bitmap = runCatching { layer.surface.extract(bounds, path) }
            .getOrElse { error ->
                onEngineMessage?.invoke(error.message ?: "No se pudo preparar la selección.")
                return false
            } ?: return false
        transformBitmap = bitmap
        transformSourcePath = Path(path)
        transformSourcePoints = persistentSelectionPoints()
        transformSourceBounds = RectF(bounds)
        selectionTransform.reset()
        selectionTransform.postTranslate(bounds.left, bounds.top)
        layer.surface.clearPath(bounds, path)
        transformGestureInitialized = false
        invalidate()
        return true
    }

    private fun handleSelectionTransformGesture(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            requestFocus()
            parent?.requestDisallowInterceptTouchEvent(true)
            if (!beginSelectionTransform()) return
        }
        if (transformBitmap == null) return

        fun gestureValues(): FloatArray {
            val count = event.pointerCount.coerceAtLeast(1)
            val mappedPoints = FloatArray(count * 2)
            repeat(count) { index ->
                val point = mapToDocument(event.getX(index), event.getY(index))
                mappedPoints[index * 2] = point[0]
                mappedPoints[index * 2 + 1] = point[1]
            }
            var cx = 0f
            var cy = 0f
            repeat(count) { index ->
                cx += mappedPoints[index * 2]
                cy += mappedPoints[index * 2 + 1]
            }
            cx /= count
            cy /= count
            val span: Float
            val angle: Float
            if (count >= 2) {
                val dx = mappedPoints[2] - mappedPoints[0]
                val dy = mappedPoints[3] - mappedPoints[1]
                span = hypot(dx, dy)
                angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            } else {
                span = 0f
                angle = 0f
            }
            return floatArrayOf(cx, cy, span, angle)
        }

        fun resetBaseline() {
            val values = gestureValues()
            transformLastCentroidX = values[0]
            transformLastCentroidY = values[1]
            transformLastSpan = values[2]
            transformLastAngle = values[3]
            transformGestureInitialized = true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> resetBaseline()
            MotionEvent.ACTION_MOVE -> {
                val values = gestureValues()
                if (!transformGestureInitialized) {
                    resetBaseline()
                    return
                }
                val cx = values[0]
                val cy = values[1]
                selectionTransform.postTranslate(cx - transformLastCentroidX, cy - transformLastCentroidY)
                if (event.pointerCount >= 2 && transformLastSpan > 0.5f && values[2] > 0.5f) {
                    val factor = (values[2] / transformLastSpan).coerceIn(0.65f, 1.55f)
                    selectionTransform.postScale(factor, factor, cx, cy)
                    val rotation = normalizeDegrees(values[3] - transformLastAngle).coerceIn(-28f, 28f)
                    selectionTransform.postRotate(rotation, cx, cy)
                }
                transformLastCentroidX = cx
                transformLastCentroidY = cy
                transformLastSpan = values[2]
                transformLastAngle = values[3]
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> transformGestureInitialized = false
            MotionEvent.ACTION_UP -> {
                commitSelectionTransform()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelSelectionTransform(rebuild = true)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun commitSelectionTransform() {
        val bitmap = transformBitmap ?: return
        val sourceBounds = transformSourceBounds ?: return
        val sourcePoints = transformSourcePoints
        val values = FloatArray(9).also(selectionTransform::getValues)
        val command = TransformSelectionCommand(
            sourcePoints = sourcePoints,
            sourceBoundsLeft = sourceBounds.left,
            sourceBoundsTop = sourceBounds.top,
            sourceBoundsRight = sourceBounds.right,
            sourceBoundsBottom = sourceBounds.bottom,
            bitmap = bitmap,
            matrixValues = values,
        )
        activeLayer()?.let { layer ->
            layer.surface.drawBitmap(commandBounds(command), bitmap, selectionTransform)
            recordCommands(layer, listOf(command))
            markLayerDirty(layer, commandBounds(command))
        }
        val transformedPoints = sourcePoints.map { point ->
            val mapped = floatArrayOf(point.x - sourceBounds.left, point.y - sourceBounds.top)
            selectionTransform.mapPoints(mapped)
            point.copy(x = mapped[0], y = mapped[1])
        }
        selectionPoints = transformedPoints.toMutableList()
        selectionPath = pathFromPoints(transformedPoints, close = true)
        transformBitmap = null
        transformSourcePath = null
        transformSourcePoints = emptyList()
        transformSourceBounds = null
        selectionTransform.reset()
        transformGestureInitialized = false
        onSelectionChanged?.invoke(selectionPath != null)
        commitDocumentChange()
    }

    private fun cancelSelectionTransform(rebuild: Boolean) {
        val hadTransform = transformBitmap != null
        transformBitmap?.recycle()
        transformBitmap = null
        transformSourcePath = null
        transformSourcePoints = emptyList()
        transformSourceBounds = null
        selectionTransform.reset()
        transformGestureInitialized = false
        if (hadTransform && rebuild) activeLayer()?.let(::rebuildLayer)
    }

    fun deselect() {
        cancelSelectionTransform(rebuild = true)
        selectionPath = null
        selectionInverted = false
        selectionFeatherPx = 0f
        selectionPoints = null
        selectionStart = null
        selectionEnd = null
        onSelectionChanged?.invoke(false)
        invalidate()
    }

    fun selectAll() {
        cancelSelectionTransform(rebuild = true)
        val points = mutableListOf(
            StrokePoint(0f, 0f, 1f, 0f, 0L),
            StrokePoint(documentWidth.toFloat(), 0f, 1f, 0f, 0L),
            StrokePoint(documentWidth.toFloat(), documentHeight.toFloat(), 1f, 0f, 0L),
            StrokePoint(0f, documentHeight.toFloat(), 1f, 0f, 0L),
        )
        selectionPoints = points
        selectionPath = pathFromPoints(points, close = true)
        selectionInverted = false
        selectionFeatherPx = 0f
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    fun adjustSelectionBounds(deltaPx: Float) {
        cancelSelectionTransform(rebuild = true)
        val current = persistentSelectionPoints()
        if (current.size < 3 || deltaPx == 0f) return
        val bounds = RectF().apply {
            current.forEachIndexed { index, point ->
                if (index == 0) set(point.x, point.y, point.x, point.y) else union(point.x, point.y)
            }
        }
        val targetWidth = bounds.width() + deltaPx * 2f
        val targetHeight = bounds.height() + deltaPx * 2f
        if (targetWidth < 4f || targetHeight < 4f) {
            onEngineMessage?.invoke("La selección ya es demasiado pequeña para contraerla.")
            return
        }
        val scaleX = targetWidth / bounds.width().coerceAtLeast(1f)
        val scaleY = targetHeight / bounds.height().coerceAtLeast(1f)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val adjusted = current.map { point ->
            point.copy(
                x = (centerX + (point.x - centerX) * scaleX).coerceIn(0f, documentWidth.toFloat()),
                y = (centerY + (point.y - centerY) * scaleY).coerceIn(0f, documentHeight.toFloat()),
            )
        }
        selectionPoints = adjusted.toMutableList()
        selectionPath = pathFromPoints(adjusted, close = true)
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    fun invertSelection() {
        cancelSelectionTransform(rebuild = true)
        if (selectionPath == null) {
            selectAll()
            return
        }
        selectionInverted = !selectionInverted
        onSelectionChanged?.invoke(true)
        invalidate()
    }

    fun setSelectionFeather(radiusPx: Float) {
        if (selectionPath == null) return
        val normalized = radiusPx.coerceIn(0f, 64f)
        if (selectionFeatherPx == normalized) return
        selectionFeatherPx = normalized
        onEngineMessage?.invoke(
            if (normalized == 0f) "Suavizado de selecciÃ³n desactivado."
            else "Borde de selecciÃ³n suavizado ${normalized.toInt()} px.",
        )
        invalidate()
    }

    fun flipSelection(horizontal: Boolean) {
        if (selectionInverted) {
            onEngineMessage?.invoke("Invierte nuevamente la selecciÃ³n antes de transformarla.")
            return
        }
        if (!beginSelectionTransform()) return
        val path = currentSelectionDisplayPath() ?: return
        val bounds = RectF().also { path.computeBounds(it, true) }
        selectionTransform.postScale(
            if (horizontal) -1f else 1f,
            if (horizontal) 1f else -1f,
            bounds.centerX(),
            bounds.centerY(),
        )
        commitSelectionTransform()
    }

    fun deleteSelectionContents() {
        val layer = activeLayer() ?: return
        val path = selectionPath ?: return
        if (layer.alphaLocked) {
            onEngineMessage?.invoke("Desactiva Bloquear alfa para borrar la selección.")
            return
        }
        val bounds = if (selectionInverted) RectF(documentBounds) else RectF().also { path.computeBounds(it, true) }
        val empty = Bitmap.createBitmap(
            if (selectionInverted) 1 else max(1, ceil(bounds.width()).toInt()),
            if (selectionInverted) 1 else max(1, ceil(bounds.height()).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        val matrix = Matrix().apply { postTranslate(bounds.left, bounds.top) }
        val values = FloatArray(9).also(matrix::getValues)
        val command = TransformSelectionCommand(
            sourcePoints = persistentSelectionPoints(),
            sourceBoundsLeft = bounds.left,
            sourceBoundsTop = bounds.top,
            sourceBoundsRight = bounds.right,
            sourceBoundsBottom = bounds.bottom,
            bitmap = empty,
            matrixValues = values,
            sourceInverted = selectionInverted,
        )
        drawCommand(layer.surface, command)
        recordCommands(layer, listOf(command))
        markLayerDirty(layer, bounds)
        deselectWithoutRebuild()
        commitDocumentChange()
    }

    private fun deselectWithoutRebuild() {
        selectionPath = null
        selectionInverted = false
        selectionFeatherPx = 0f
        selectionPoints = null
        selectionStart = null
        selectionEnd = null
        onSelectionChanged?.invoke(false)
        invalidate()
    }

    private fun performFloodFill(x: Int, y: Int) {
        val layer = activeLayer() ?: return
        if (x !in 0 until documentWidth || y !in 0 until documentHeight) return
        val pixelCount = documentWidth.toLong() * documentHeight.toLong()
        val estimatedBytes = pixelCount * 13L
        if (pixelCount > MAX_FLOOD_FILL_PIXELS || estimatedBytes > Runtime.getRuntime().maxMemory() * 0.38) {
            onEngineMessage?.invoke("El relleno contiguo está limitado a lienzos medianos; usa una selección o un lienzo menor.")
            return
        }
        val layerId = layer.id
        val fillColor = brushSettings.color
        val fillSelection = selectionPath?.let(::Path)
        val fillSelectionInverted = selectionInverted
        onEngineMessage?.invoke("Calculando relleno…")
        prefetchExecutor.execute {
            val bitmap = runCatching { layer.surface.renderBitmap(MAX_FLOOD_FILL_PIXELS) }.getOrNull()
                ?: return@execute
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val seed = y * width + x
                val selectionRegion = fillSelection?.let { path ->
                    val documentRegion = Region(0, 0, width, height)
                    Region().apply {
                        setPath(path, documentRegion)
                        if (fillSelectionInverted) {
                            val inside = Region(this)
                            set(documentRegion)
                            op(inside, Region.Op.DIFFERENCE)
                        }
                    }
                }
                if (selectionRegion != null && !selectionRegion.contains(x, y)) return@execute
                val targetColor = pixels[seed]
                if (colorsNear(targetColor, fillColor, 4)) return@execute
                val queue = IntArray(pixels.size)
                val filled = java.util.BitSet(pixels.size)
                var head = 0
                var tail = 0
                queue[tail++] = seed
                pixels[seed] = fillColor
                filled.set(seed)
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                while (head < tail) {
                    val index = queue[head++]
                    val px = index % width
                    val py = index / width
                    fun enqueue(next: Int, nx: Int, ny: Int) {
                        if ((selectionRegion == null || selectionRegion.contains(nx, ny)) &&
                            !filled.get(next) && colorsNear(pixels[next], targetColor, 28)
                        ) {
                            filled.set(next)
                            pixels[next] = fillColor
                            queue[tail++] = next
                            minX = min(minX, nx)
                            maxX = max(maxX, nx)
                            minY = min(minY, ny)
                            maxY = max(maxY, ny)
                        }
                    }
                    if (px > 0) enqueue(index - 1, px - 1, py)
                    if (px + 1 < width) enqueue(index + 1, px + 1, py)
                    if (py > 0) enqueue(index - width, px, py - 1)
                    if (py + 1 < height) enqueue(index + width, px, py + 1)
                }
                val patchWidth = maxX - minX + 1
                val patchHeight = maxY - minY + 1
                val patchPixels = IntArray(patchWidth * patchHeight)
                for (py in minY..maxY) {
                    for (px in minX..maxX) {
                        val sourceIndex = py * width + px
                        if (filled.get(sourceIndex)) {
                            patchPixels[(py - minY) * patchWidth + (px - minX)] = fillColor
                        }
                    }
                }
                val patch = Bitmap.createBitmap(patchPixels, patchWidth, patchHeight, Bitmap.Config.ARGB_8888)
                post {
                    val currentLayer = layers.firstOrNull { it.id == layerId }
                    if (currentLayer == null) {
                        patch.recycle()
                        return@post
                    }
                    val command = PixelPatchCommand(bitmap = patch, left = minX.toFloat(), top = minY.toFloat())
                    drawCommand(currentLayer.surface, command)
                    recordCommands(currentLayer, listOf(command))
                    markLayerDirty(currentLayer, commandBounds(command))
                    commitDocumentChange()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun colorsNear(first: Int, second: Int, tolerance: Int): Boolean =
        abs(Color.alpha(first) - Color.alpha(second)) <= tolerance &&
            abs(Color.red(first) - Color.red(second)) <= tolerance &&
            abs(Color.green(first) - Color.green(second)) <= tolerance &&
            abs(Color.blue(first) - Color.blue(second)) <= tolerance

    private fun drawCommand(
        surface: SparseTileSurface,
        command: DrawCommand,
        replayClipBounds: RectF? = null,
    ) {
        val layer = layers.firstOrNull { it.surface === surface || it.maskSurface === surface }
        val target = if (layer?.maskSurface === surface) HistoryTarget.MASK else HistoryTarget.CONTENT
        val commandBounds = commandBounds(command)
        val bounds = if (replayClipBounds == null) {
            commandBounds
        } else {
            RectF(commandBounds).apply { intersect(replayClipBounds) }
        }
        if (bounds.isEmpty) return
        val pickupColors = (command as? StrokeCommand)
            ?.takeIf { it.settings.renderProfile.colorPickup > .001f && it.points.size > 1 }
            ?.let { stroke ->
                IntArray(stroke.points.size - 1) { index ->
                    surface.samplePixel(stroke.points[index + 1].x, stroke.points[index + 1].y) ?: 0
                }
            }
        if (target == HistoryTarget.CONTENT && layer?.alphaLocked == true && command !is TransformSelectionCommand) {
            surface.drawPreservingAlpha(bounds) { canvas ->
                val checkpoint = replayClipBounds?.let {
                    canvas.save().also { canvas.clipRect(replayClipBounds) }
                }
                drawCommand(canvas, command, pickupColors, replayClipBounds)
                if (checkpoint != null) canvas.restoreToCount(checkpoint)
            }
        } else {
            surface.drawPerTile(bounds) { canvas, tileBounds ->
                val renderBounds = if (replayClipBounds == null) {
                    tileBounds
                } else {
                    RectF(tileBounds).apply { intersect(replayClipBounds) }
                }
                if (!renderBounds.isEmpty) {
                    val checkpoint = canvas.save()
                    canvas.clipRect(renderBounds)
                    drawCommand(canvas, command, pickupColors, renderBounds)
                    canvas.restoreToCount(checkpoint)
                }
            }
        }
    }

    private fun drawOnLayer(layer: LayerData, bounds: RectF, block: (Canvas) -> Unit) {
        val target = activeHistoryTarget(layer)
        val surface = surfaceFor(layer, target)
        val selection = selectionPath?.let(::Path)
        val clippedBlock: (Canvas) -> Unit = { canvas ->
            if (selection != null) {
                val saveCount = beginSelectionMask(
                    canvas,
                    selection,
                    selectionInverted,
                    selectionFeatherPx,
                )
                block(canvas)
                endSelectionMask(
                    canvas,
                    saveCount,
                    selection,
                    selectionInverted,
                    selectionFeatherPx,
                )
            } else {
                block(canvas)
            }
        }
        if (target == HistoryTarget.CONTENT && layer.alphaLocked) {
            surface.drawPreservingAlpha(bounds, clippedBlock)
        } else {
            surface.draw(bounds, clippedBlock)
        }
    }

    private fun drawCommand(
        canvas: Canvas,
        command: DrawCommand,
        pickupColors: IntArray? = null,
        renderClipBounds: RectF? = null,
    ) {
        when (command) {
            is StrokeCommand -> {
                val clipPath = command.clipPoints.takeIf { it.isNotEmpty() }
                    ?.let { pathFromPoints(it, close = true) }
                val clipSave = clipPath?.let {
                    beginSelectionMask(
                        canvas,
                        it,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                }
                command.points.firstOrNull()?.takeIf { first ->
                    renderClipBounds == null ||
                        RectF.intersects(
                            strokeSegmentBounds(first, first, command.settings),
                            renderClipBounds,
                        )
                }?.let { first ->
                    drawBrushStamp(
                        canvas = canvas,
                        x = first.x,
                        y = first.y,
                        pressure = first.pressure * taperFactor(command.settings, 0f),
                        tilt = first.tilt,
                        drawingTool = command.tool,
                        settings = command.settings,
                        stampIndex = 0,
                        angleRadians = 0f,
                        orientation = first.orientation,
                    )
                }
                val segmentCount = (command.points.size - 1).coerceAtLeast(0)
                for (index in 0 until segmentCount) {
                    if (
                        renderClipBounds != null &&
                        !RectF.intersects(
                            strokeSegmentBounds(
                                command.points[index],
                                command.points[index + 1],
                                command.settings,
                            ),
                            renderClipBounds,
                        )
                    ) {
                        continue
                    }
                    val progress = (index + 1f) / segmentCount.coerceAtLeast(1).toFloat()
                    drawStrokeSegment(
                        canvas,
                        command.points[index],
                        command.points[index + 1],
                        command.tool,
                        command.settings,
                        progress,
                        index,
                        pickupColors?.getOrNull(index),
                    )
                }
                if (clipPath != null && clipSave != null) {
                    endSelectionMask(
                        canvas,
                        clipSave,
                        clipPath,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                }
            }
            is ShapeCommand -> {
                val clipPath = command.clipPoints.takeIf { it.isNotEmpty() }
                    ?.let { pathFromPoints(it, close = true) }
                val clipSave = clipPath?.let {
                    beginSelectionMask(
                        canvas,
                        it,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                }
                configurePaint(strokePaint, command.tool, command.settings, 1f, 0f)
                val start = StrokePoint(command.startX, command.startY, 1f, 0f, 0L)
                val end = StrokePoint(command.endX, command.endY, 1f, 0f, 0L)
                drawShapeWithPaint(canvas, command.tool, start, end, strokePaint)
                if (clipPath != null && clipSave != null) {
                    endSelectionMask(
                        canvas,
                        clipSave,
                        clipPath,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                }
            }
            is GradientCommand -> {
                val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = LinearGradient(
                        command.startX,
                        command.startY,
                        command.endX,
                        command.endY,
                        command.startColor,
                        command.endColor,
                        Shader.TileMode.CLAMP,
                    )
                }
                if (command.clipPoints.isNotEmpty()) {
                    val clipPath = pathFromPoints(command.clipPoints, close = true)
                    val clipSave = beginSelectionMask(
                        canvas,
                        clipPath,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                    canvas.drawRect(documentBounds, gradientPaint)
                    endSelectionMask(
                        canvas,
                        clipSave,
                        clipPath,
                        command.clipInverted,
                        command.clipFeatherPx,
                    )
                } else {
                    canvas.drawRect(documentBounds, gradientPaint)
                }
                gradientPaint.shader = null
            }
            is PixelPatchCommand -> {
                canvas.drawBitmap(command.bitmap, command.left, command.top, null)
            }
            is TransformSelectionCommand -> {
                val sourcePath = pathFromPoints(command.sourcePoints, close = true)
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                if (command.sourceInverted) {
                    canvas.save()
                    applySelectionClip(canvas, sourcePath, inverted = true)
                    canvas.drawRect(documentBounds, clearPaint)
                    canvas.restore()
                } else {
                    canvas.drawPath(sourcePath, clearPaint)
                }
                clearPaint.xfermode = null
                val matrix = Matrix().apply { setValues(command.matrixValues) }
                canvas.drawBitmap(
                    command.bitmap,
                    matrix,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
        }
    }

    private fun applySelectionClip(canvas: Canvas, path: Path, inverted: Boolean) {
        if (inverted) canvas.clipOutPath(path) else canvas.clipPath(path)
    }

    private fun beginSelectionMask(
        canvas: Canvas,
        path: Path,
        inverted: Boolean,
        featherPx: Float,
    ): Int {
        val feather = featherPx.coerceAtLeast(0f)
        return if (feather <= 0.01f) {
            canvas.save().also { applySelectionClip(canvas, path, inverted) }
        } else {
            val clipBounds = Rect()
            canvas.getClipBounds(clipBounds)
            canvas.saveLayer(RectF(clipBounds), null)
        }
    }

    private fun endSelectionMask(
        canvas: Canvas,
        saveCount: Int,
        path: Path,
        inverted: Boolean,
        featherPx: Float,
    ) {
        val feather = featherPx.coerceAtLeast(0f)
        if (feather > 0.01f) {
            val maskPath = if (inverted) {
                path
            } else {
                Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(documentBounds, Path.Direction.CW)
                    addPath(path)
                }
            }
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            canvas.drawPath(maskPath, maskPaint)
            maskPaint.xfermode = null
            maskPaint.maskFilter = null
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawStrokeSegment(
        canvas: Canvas,
        from: StrokePoint,
        to: StrokePoint,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        progress: Float = 1f,
        segmentIndex: Int = 0,
        sampledColor: Int? = null,
    ) {
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val distance = hypot(deltaX, deltaY)
        val elapsed = (to.timestampMillis - from.timestampMillis).coerceAtLeast(1L).toFloat()
        val speed = distance / elapsed
        val speedFactor = (speed / 2.4f).coerceIn(0f, 1f)
        val effectivePressure = ((from.pressure + to.pressure) / 2f) * taperFactor(settings, progress)
        val angleRadians = atan2(deltaY.toDouble(), deltaX.toDouble()).toFloat()
        val stampBased = isStampBrush(settings.kind)
        val pigmentColor = mixPigmentColor(
            settings.color,
            sampledColor,
            settings.renderProfile.colorPickup,
        )

        if (stampBased) {
            val step = max(1.5f, settings.sizePx * stampSpacing(settings))
            val stampCount = max(1, ceil(distance / step).toInt()).coerceAtMost(MAX_STAMPS_PER_SEGMENT)
            repeat(stampCount) { index ->
                val stampProgress = (index + 1f) / stampCount.toFloat()
                drawBrushStamp(
                    canvas = canvas,
                    x = from.x + deltaX * stampProgress,
                    y = from.y + deltaY * stampProgress,
                    pressure = (from.pressure + (to.pressure - from.pressure) * stampProgress) *
                        taperFactor(settings, progress),
                    tilt = from.tilt + (to.tilt - from.tilt) * stampProgress,
                    drawingTool = drawingTool,
                    settings = settings,
                    stampIndex = segmentIndex.coerceAtLeast(0) * MAX_STAMPS_PER_SEGMENT + index,
                    angleRadians = angleRadians,
                    orientation = interpolateCircularRadians(
                        from.orientation,
                        to.orientation,
                        stampProgress,
                    ),
                    speedFactor = speedFactor,
                    resolvedColor = pigmentColor,
                    strokeProgress = progress,
                )
            }
            return
        }

        val tilt = (from.tilt + to.tilt) / 2f
        configurePaint(
            strokePaint,
            drawingTool,
            settings,
            effectivePressure,
            tilt,
            speedFactor = speedFactor,
            resolvedColor = pigmentColor,
        )
        canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)

        if (settings.kind == BrushKind.PENCIL && drawingTool != DrawingTool.ERASER) {
            val originalAlpha = strokePaint.alpha
            val originalWidth = strokePaint.strokeWidth
            strokePaint.alpha = (originalAlpha * (0.18f + settings.grain * 0.28f)).toInt().coerceIn(1, 255)
            strokePaint.strokeWidth = max(0.7f, originalWidth * 0.28f)
            val offset = max(0.45f, originalWidth * 0.09f)
            canvas.drawLine(from.x + offset, from.y - offset, to.x + offset, to.y - offset, strokePaint)
            strokePaint.alpha = originalAlpha
            strokePaint.strokeWidth = originalWidth
        }
    }

    private fun drawBrushStamp(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        stampIndex: Int,
        angleRadians: Float,
        orientation: Float = 0f,
        speedFactor: Float = 0f,
        resolvedColor: Int = settings.color,
        strokeProgress: Float = 0f,
    ) {
        configurePaint(
            strokePaint,
            drawingTool,
            settings,
            pressure,
            tilt,
            speedFactor = speedFactor,
            resolvedColor = resolvedColor,
        )
        if (drawingTool != DrawingTool.ERASER && settings.renderProfile.wetness > .001f) {
            val charge = settings.renderProfile.charge.coerceIn(0f, 1f)
            val attack = settings.renderProfile.attack.coerceIn(0f, 1f)
            val pigmentLoad = charge * (
                1f - strokeProgress.coerceIn(0f, 1f) * (1f - attack) * .72f
                )
            strokePaint.alpha = (strokePaint.alpha * pigmentLoad.coerceIn(.08f, 1f))
                .toInt().coerceIn(1, 255)
        }
        if (drawingTool != DrawingTool.ERASER) {
            strokePaint.alpha = (
                strokePaint.alpha *
                    grainCoverage(settings.grainProfile, x, y, stampIndex)
                ).toInt().coerceIn(1, 255)
        }
        val diameter = strokePaint.strokeWidth
        val radius = max(0.6f, diameter / 2f)
        strokePaint.style = Paint.Style.FILL

        val seed = x.toInt() * 73856093 xor y.toInt() * 19349663 xor stampIndex * 83492791
        val noiseX = ((seed and 0xFFFF) / 32767.5f) - 1f
        val noiseY = (((seed ushr 16) and 0xFFFF) / 32767.5f) - 1f
        val scatterRadius = radius * settings.scatter.coerceIn(0f, 1f)
        val stampX = x + noiseX * scatterRadius
        val stampY = y + noiseY * scatterRadius
        val resolvedAngle = resolveBrushRotation(
            settings = settings,
            strokeAngle = angleRadians,
            stylusOrientation = orientation,
            seed = seed,
        )
        val customTip = settings.tipAssetPath?.let(::loadTipBitmap)
        if (customTip != null) {
            canvas.save()
            canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
            val aspect = customTip.width.toFloat() / customTip.height.coerceAtLeast(1)
            val halfWidth = if (aspect >= 1f) radius else radius * aspect
            val halfHeight = if (aspect >= 1f) radius / aspect else radius
            strokePaint.colorFilter = if (drawingTool == DrawingTool.ERASER) {
                null
            } else {
                PorterDuffColorFilter(resolvedColor, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(
                customTip,
                null,
                RectF(
                    stampX - halfWidth,
                    stampY - halfHeight,
                    stampX + halfWidth,
                    stampY + halfHeight,
                ),
                strokePaint,
            )
            strokePaint.colorFilter = null
            canvas.restore()
            drawDualBrushStamp(
                canvas = canvas,
                x = stampX,
                y = stampY,
                radius = radius,
                settings = settings,
                drawingTool = drawingTool,
                stampIndex = stampIndex,
                angleRadians = resolvedAngle,
                seed = seed,
                resolvedColor = resolvedColor,
            )
            strokePaint.style = Paint.Style.STROKE
            return
        }
        applyGrainTexture(
            paint = strokePaint,
            settings = settings,
            drawingTool = drawingTool,
            x = stampX,
            y = stampY,
            stampIndex = stampIndex,
            angleRadians = resolvedAngle,
            resolvedColor = resolvedColor,
        )

        when (settings.kind) {
            BrushKind.PENCIL -> {
                val baseAlpha = strokePaint.alpha
                val roundness = settings.tipProfile.roundness.coerceIn(.08f, 1f)
                val tiltWidth = 1f + tilt * settings.tiltResponse.coerceIn(0f, 1f) * 1.35f
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                canvas.drawOval(
                    RectF(
                        stampX - radius * tiltWidth,
                        stampY - radius * roundness,
                        stampX + radius * tiltWidth,
                        stampY + radius * roundness,
                    ),
                    strokePaint,
                )
                if (settings.grainProfile.depth > .08f) {
                    strokePaint.alpha = (baseAlpha * .24f).toInt().coerceIn(1, 255)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = max(.55f, diameter * .055f)
                    val toothOffset = radius * roundness * .42f
                    canvas.drawLine(
                        stampX - radius * .74f,
                        stampY - toothOffset,
                        stampX + radius * .74f,
                        stampY - toothOffset,
                        strokePaint,
                    )
                    strokePaint.style = Paint.Style.FILL
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.INK -> {
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                when (settings.tipProfile.shape) {
                    BrushTipShape.ROUND -> canvas.drawCircle(stampX, stampY, radius, strokePaint)
                    BrushTipShape.OVAL, BrushTipShape.CHISEL -> canvas.drawOval(
                        RectF(
                            stampX - radius,
                            stampY - radius * settings.tipProfile.roundness.coerceIn(.08f, 1f),
                            stampX + radius,
                            stampY + radius * settings.tipProfile.roundness.coerceIn(.08f, 1f),
                        ),
                        strokePaint,
                    )
                    BrushTipShape.BRISTLE, BrushTipShape.PARTICLE -> {
                        val count = settings.tipProfile.count.coerceIn(2, 8)
                        repeat(count) { part ->
                            val unit = part / (count - 1f)
                            val offset = (unit - .5f) * radius * 1.2f
                            canvas.drawOval(
                                RectF(
                                    stampX - radius,
                                    stampY + offset - max(.4f, radius * .05f),
                                    stampX + radius,
                                    stampY + offset + max(.4f, radius * .05f),
                                ),
                                strokePaint,
                            )
                        }
                    }
                }
                canvas.restore()
            }

            BrushKind.CHARCOAL, BrushKind.CHALK -> {
                val adaptiveCount = when {
                    diameter >= 144f -> 2
                    diameter >= 96f -> 3
                    else -> settings.tipProfile.count
                }
                val countNoise = (((seed ushr 5) and 0xFF) / 255f - .5f) *
                    settings.tipProfile.countJitter.coerceIn(0f, 1f)
                val particles = (adaptiveCount * (1f + countNoise)).toInt().coerceIn(2, 12)
                val baseAlpha = strokePaint.alpha
                // A translucent directional core keeps very large charcoal/chalk strokes
                // continuous; particles alone look like disconnected circles above ~100 px.
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                strokePaint.alpha = (
                    baseAlpha * if (settings.kind == BrushKind.CHARCOAL) .2f else .14f
                ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    RectF(
                        stampX - radius * .82f,
                        stampY - radius * .2f,
                        stampX + radius * .82f,
                        stampY + radius * .2f,
                    ),
                    strokePaint,
                )
                canvas.restore()
                strokePaint.alpha = baseAlpha
                val spread = radius * (0.48f + tilt * settings.tiltResponse + settings.scatter)
                repeat(particles) { particle ->
                    val particleSeed = seed xor (particle + 1) * 83492791
                    val xNoise = ((particleSeed and 0xFFFF) / 32767.5f) - 1f
                    val yNoise = (((particleSeed ushr 16) and 0xFFFF) / 32767.5f) - 1f
                    val grain = settings.grain.coerceIn(0f, 1f)
                    val particleRadius = radius * (0.08f + ((particleSeed ushr 8) and 0xFF) / 255f * (0.18f + grain * 0.22f))
                    strokePaint.alpha = (baseAlpha * (0.18f + grain * 0.28f + particle * 0.045f)).toInt().coerceIn(1, 255)
                    val particleX = stampX + xNoise * spread
                    val particleY = stampY + yNoise * spread
                    val aspect = .32f + ((particleSeed ushr 20) and 0xFF) / 255f * .54f
                    canvas.save()
                    canvas.rotate(
                        (((particleSeed ushr 12) and 0xFF) / 255f * 180f) +
                            resolvedAngle * 180f / PI.toFloat(),
                        particleX,
                        particleY,
                    )
                    canvas.drawOval(
                        RectF(
                            particleX - max(.55f, particleRadius),
                            particleY - max(.45f, particleRadius * aspect),
                            particleX + max(.55f, particleRadius),
                            particleY + max(.45f, particleRadius * aspect),
                        ),
                        strokePaint,
                    )
                    canvas.restore()
                }
                strokePaint.alpha = baseAlpha
            }

            BrushKind.MARKER -> {
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val roundness = settings.tipProfile.roundness.coerceIn(.12f, 1f)
                canvas.drawOval(
                    RectF(
                        stampX - radius,
                        stampY - radius * roundness,
                        stampX + radius,
                        stampY + radius * roundness,
                    ),
                    strokePaint,
                )
                canvas.restore()
            }

            BrushKind.PAINT -> {
                val baseAlpha = strokePaint.alpha
                canvas.drawCircle(stampX, stampY, radius, strokePaint)
                if (settings.grain > 0f) {
                    val grainParticles = if (diameter >= 120f) 1 else 2
                    repeat(grainParticles) { particle ->
                        val particleSeed = seed xor particle * 265443576
                        val px = (((particleSeed and 0xFFFF) / 32767.5f) - 1f) * radius * .55f
                        val py = ((((particleSeed ushr 16) and 0xFFFF) / 32767.5f) - 1f) * radius * .55f
                        strokePaint.alpha = (baseAlpha * settings.grain * .18f).toInt().coerceIn(1, 255)
                        canvas.drawCircle(stampX + px, stampY + py, max(.8f, radius * .12f), strokePaint)
                    }
                    strokePaint.alpha = baseAlpha
                }
            }

            BrushKind.DRY_BRUSH -> {
                val baseAlpha = strokePaint.alpha
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val bristleCount = settings.tipProfile.count.coerceIn(3, 12)
                repeat(bristleCount) { bristle ->
                    if (((seed ushr (bristle * 3)) and 0x3) != 0) {
                        val offset = (bristle - (bristleCount - 1) / 2f) *
                            radius * 1.12f / bristleCount
                        strokePaint.alpha = (
                            baseAlpha * (.3f + bristle * .36f / bristleCount)
                            ).toInt().coerceIn(1, 255)
                        canvas.drawOval(
                            RectF(
                                stampX - radius * .7f,
                                stampY + offset - radius * .08f,
                                stampX + radius * .7f,
                                stampY + offset + radius * .08f,
                            ),
                            strokePaint,
                        )
                    }
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.BRISTLE -> {
                val baseAlpha = strokePaint.alpha
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val bristleCount = settings.tipProfile.count.coerceIn(3, 14)
                repeat(bristleCount) { bristle ->
                    val offset = (bristle - (bristleCount - 1) / 2f) *
                        radius * 1.05f / bristleCount
                    strokePaint.alpha = (
                        baseAlpha * (.42f + bristle * .4f / bristleCount)
                        ).toInt().coerceIn(1, 255)
                    canvas.drawOval(
                        RectF(
                            stampX - radius,
                            stampY + offset - radius * .055f,
                            stampX + radius,
                            stampY + offset + radius * .055f,
                        ),
                        strokePaint,
                    )
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.WATERCOLOR -> {
                val baseAlpha = strokePaint.alpha
                val wetness = settings.renderProfile.wetness.coerceIn(0f, 1f)
                val dilution = settings.renderProfile.dilution.coerceIn(0f, 1f)
                val bleed = settings.renderProfile.bleed.coerceIn(0f, 1f)
                val wetRadius = radius * (1f + bleed * .12f)
                strokePaint.alpha = (
                    baseAlpha * (.64f - dilution * .24f)
                    ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    RectF(
                        stampX - wetRadius,
                        stampY - wetRadius * (.78f + wetness * .18f),
                        stampX + wetRadius,
                        stampY + wetRadius * (.78f + wetness * .18f),
                    ),
                    strokePaint,
                )
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = max(1f, radius * (.035f + wetness * .055f))
                strokePaint.alpha = (
                    baseAlpha * (.1f + wetness * .18f + settings.grain * .12f)
                    ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    RectF(
                        stampX - radius * .91f + noiseX * radius * .06f,
                        stampY - radius * .77f + noiseY * radius * .06f,
                        stampX + radius * .91f + noiseX * radius * .06f,
                        stampY + radius * .77f + noiseY * radius * .06f,
                    ),
                    strokePaint,
                )
                strokePaint.style = Paint.Style.FILL
                strokePaint.alpha = baseAlpha
            }

            BrushKind.OIL -> {
                val baseAlpha = strokePaint.alpha
                val bristleCount = settings.tipProfile.count.coerceIn(4, 14)
                val drag = settings.renderProfile.drag.coerceIn(0f, 1f)
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                repeat(bristleCount) { bristle ->
                    val unit = bristle / (bristleCount - 1f)
                    val offset = (unit - .5f) * radius * 1.1f
                    val bristleSeed = seed xor (bristle + 3) * 1103515245
                    val lengthNoise = .72f + ((bristleSeed ushr 9) and 0xFF) / 255f * .28f
                    val thickness = radius * (.035f + (1f - drag) * .035f)
                    strokePaint.alpha = (
                        baseAlpha * (.48f + ((bristleSeed ushr 18) and 0xFF) / 255f * .42f)
                        ).toInt().coerceIn(1, 255)
                    canvas.drawOval(
                        RectF(
                            stampX - radius * lengthNoise,
                            stampY + offset - thickness,
                            stampX + radius,
                            stampY + offset + thickness,
                        ),
                        strokePaint,
                    )
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.AIRBRUSH -> canvas.drawCircle(stampX, stampY, radius, strokePaint)
        }
        drawDualBrushStamp(
            canvas = canvas,
            x = stampX,
            y = stampY,
            radius = radius,
            settings = settings,
            drawingTool = drawingTool,
            stampIndex = stampIndex,
            angleRadians = resolvedAngle,
            seed = seed,
            resolvedColor = resolvedColor,
        )
        strokePaint.shader = null
        strokePaint.colorFilter = null
        strokePaint.style = Paint.Style.STROKE
    }

    private fun drawDualBrushStamp(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        settings: BrushSettings,
        drawingTool: DrawingTool,
        stampIndex: Int,
        angleRadians: Float,
        seed: Int,
        resolvedColor: Int,
    ) {
        val dual = settings.dualBrushProfile
        if (!dual.enabled || dual.opacity <= .001f) return

        val originalAlpha = strokePaint.alpha
        val originalXfermode = strokePaint.xfermode
        val originalStyle = strokePaint.style
        val secondaryRadius = max(.45f, radius * dual.sizeScale.coerceIn(.1f, 2f))
        val noiseX = ((seed ushr 3 and 0xFFFF) / 32767.5f) - 1f
        val noiseY = ((seed ushr 11 and 0xFFFF) / 32767.5f) - 1f
        val normalX = -sin(angleRadians)
        val normalY = cos(angleRadians)
        val offset = radius * dual.offset.coerceIn(-1f, 1f)
        val scatter = radius * dual.scatter.coerceIn(0f, 1f)
        val centerX = x + normalX * offset + noiseX * scatter
        val centerY = y + normalY * offset + noiseY * scatter
        val dualAngle = when (dual.tip.rotationMode) {
            BrushRotationMode.FOLLOW_STROKE -> angleRadians
            BrushRotationMode.FIXED -> 0f
            BrushRotationMode.STYLUS -> angleRadians
            BrushRotationMode.RANDOM -> ((seed ushr 8) and 0xFFFF) / 65535f * 2f * PI.toFloat()
        } + Math.toRadians(dual.tip.angleDegrees.toDouble()).toFloat()

        strokePaint.alpha = (originalAlpha * dual.opacity.coerceIn(0f, 1f))
            .toInt().coerceIn(1, 255)
        if (drawingTool != DrawingTool.ERASER) {
            strokePaint.xfermode = when (dual.blendMode) {
                DualBrushBlendMode.NORMAL -> sourceOverXfermode
                DualBrushBlendMode.MULTIPLY -> multiplyXfermode
                DualBrushBlendMode.SCREEN -> screenXfermode
            }
        }
        strokePaint.style = Paint.Style.FILL
        applyGrainTexture(
            paint = strokePaint,
            settings = settings,
            drawingTool = drawingTool,
            x = centerX,
            y = centerY,
            stampIndex = stampIndex,
            angleRadians = dualAngle,
            profile = dual.grain,
            resolvedColor = resolvedColor,
        )
        canvas.save()
        canvas.rotate(dualAngle * 180f / PI.toFloat(), centerX, centerY)
        val roundness = dual.tip.roundness.coerceIn(.08f, 1f)
        when (dual.tip.shape) {
            BrushTipShape.ROUND -> canvas.drawCircle(centerX, centerY, secondaryRadius, strokePaint)
            BrushTipShape.OVAL, BrushTipShape.CHISEL -> canvas.drawOval(
                RectF(
                    centerX - secondaryRadius,
                    centerY - secondaryRadius * roundness,
                    centerX + secondaryRadius,
                    centerY + secondaryRadius * roundness,
                ),
                strokePaint,
            )
            BrushTipShape.BRISTLE -> {
                val count = dual.tip.count.coerceIn(2, 8)
                repeat(count) { bristle ->
                    val unit = bristle / (count - 1f)
                    val bristleY = centerY + (unit - .5f) * secondaryRadius * 1.4f
                    canvas.drawOval(
                        RectF(
                            centerX - secondaryRadius,
                            bristleY - max(.35f, secondaryRadius * .045f),
                            centerX + secondaryRadius,
                            bristleY + max(.35f, secondaryRadius * .045f),
                        ),
                        strokePaint,
                    )
                }
            }
            BrushTipShape.PARTICLE -> {
                val count = dual.tip.count.coerceIn(1, 6)
                repeat(count) { particle ->
                    val particleSeed = seed xor (particle + 7) * 1103515245
                    val px = ((particleSeed and 0xFFFF) / 32767.5f - 1f) * secondaryRadius
                    val py = (((particleSeed ushr 16) and 0xFFFF) / 32767.5f - 1f) *
                        secondaryRadius
                    val particleRadius = secondaryRadius * (
                        .12f + ((particleSeed ushr 9) and 0xFF) / 255f * .24f
                        )
                    canvas.drawOval(
                        RectF(
                            centerX + px - particleRadius,
                            centerY + py - particleRadius * roundness,
                            centerX + px + particleRadius,
                            centerY + py + particleRadius * roundness,
                        ),
                        strokePaint,
                    )
                }
            }
        }
        canvas.restore()
        strokePaint.shader = null
        strokePaint.colorFilter = null
        strokePaint.alpha = originalAlpha
        strokePaint.xfermode = originalXfermode
        strokePaint.style = originalStyle
    }

    private fun applyGrainTexture(
        paint: Paint,
        settings: BrushSettings,
        drawingTool: DrawingTool,
        x: Float,
        y: Float,
        stampIndex: Int,
        angleRadians: Float,
        profile: BrushGrainProfile = settings.grainProfile,
        resolvedColor: Int = settings.color,
    ) {
        if (
            drawingTool == DrawingTool.ERASER ||
            profile.depth <= .01f ||
            profile.source == BrushGrainSource.NONE
        ) {
            paint.shader = null
            paint.colorFilter = null
            return
        }
        val key = brushTextureKey(profile)
        val entry = grainTextureCache[key] ?: run {
            val bitmap = createBrushGrainBitmap(key)
            GrainTextureEntry(
                bitmap = bitmap,
                shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT),
            ).also { grainTextureCache[key] = it }
        }
        val textureScale = profile.scale.coerceIn(.15f, 4f)
        grainShaderMatrix.reset()
        grainShaderMatrix.setScale(textureScale, textureScale)
        if (profile.mode == BrushGrainMode.MOVING) {
            val movement = profile.movement.coerceIn(0f, 1f)
            grainShaderMatrix.postRotate(
                angleRadians * 180f / PI.toFloat() * movement,
                x,
                y,
            )
            grainShaderMatrix.postTranslate(
                x * movement + stampIndex * 1.7f,
                y * movement + stampIndex * .9f,
            )
        }
        entry.shader.setLocalMatrix(grainShaderMatrix)
        paint.shader = entry.shader
        paint.colorFilter = PorterDuffColorFilter(resolvedColor, PorterDuff.Mode.SRC_IN)
    }

    private fun resolveBrushRotation(
        settings: BrushSettings,
        strokeAngle: Float,
        stylusOrientation: Float,
        seed: Int,
    ): Float {
        val profile = settings.tipProfile
        val base = when (profile.rotationMode) {
            BrushRotationMode.FOLLOW_STROKE -> strokeAngle
            BrushRotationMode.FIXED -> 0f
            BrushRotationMode.STYLUS -> stylusOrientation
            BrushRotationMode.RANDOM -> {
                val unit = ((seed ushr 8) and 0xFFFF) / 65535f
                unit * (2f * PI.toFloat())
            }
        }
        val jitterUnit = ((seed and 0xFFFF) / 65535f) - .5f
        return base +
            Math.toRadians(profile.angleDegrees.toDouble()).toFloat() +
            jitterUnit * profile.rotationJitter.coerceIn(0f, 1f) * PI.toFloat()
    }

    private fun loadTipBitmap(path: String): Bitmap? {
        tipBitmapCache[path]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        val file = File(path)
        if (!file.isFile || file.length() > 2L * 1024L * 1024L) return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        tipBitmapCache[path] = bitmap
        return bitmap
    }

    private fun taperFactor(settings: BrushSettings, progress: Float): Float {
        val safeProgress = progress.coerceIn(0f, 1f)
        val start = settings.taperStart.coerceIn(0f, 0.48f)
        val end = settings.taperEnd.coerceIn(0f, 0.48f)
        val startFactor = if (start <= 0.001f) 1f else (safeProgress / start).coerceIn(0.06f, 1f)
        val endFactor = if (end <= 0.001f) 1f else ((1f - safeProgress) / end).coerceIn(0.06f, 1f)
        return min(startFactor, endFactor)
    }

    private fun maskFilterFor(settings: BrushSettings): BlurMaskFilter? {
        val radius = when (settings.kind) {
            BrushKind.AIRBRUSH -> max(
                2f,
                settings.sizePx * (0.12f + (1f - settings.hardness) * 0.22f),
            )
            BrushKind.PAINT, BrushKind.WATERCOLOR -> if (settings.hardness < 0.62f) {
                max(1f, settings.sizePx * (1f - settings.hardness) * 0.08f)
            } else {
                0f
            }
            else -> 0f
        }
        if (radius <= 0f) return null
        val key = settings.kind.ordinal * 100_000 + (radius * 8f).toInt()
        if (key != cachedMaskFilterKey) {
            cachedMaskFilterKey = key
            cachedMaskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        return cachedMaskFilter
    }

    private fun configurePaint(
        paint: Paint,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        pressure: Float,
        tilt: Float,
        isPreview: Boolean = false,
        speedFactor: Float = 0f,
        resolvedColor: Int = settings.color,
    ) {
        val dynamics = settings.dynamicsProfile
        val legacyPressure = calibratedPressure(pressure, settings.pressureCurve)
        val sizePressure = applyInputCurve(legacyPressure, dynamics.sizePressure)
        val opacityPressureValue = applyInputCurve(legacyPressure, dynamics.opacityPressure)
        val flowPressureValue = applyInputCurve(legacyPressure, dynamics.flowPressure)
        val minimum = settings.minSize.coerceIn(0.02f, 1f)
        val pressureFactor = if (settings.pressureSize) {
            minimum + sizePressure * (1f - minimum)
        } else {
            1f
        }
        val resolvedTilt = (
            (tilt.coerceIn(0f, 1f) - dynamics.tiltThreshold.coerceIn(0f, .95f)) /
                (1f - dynamics.tiltThreshold.coerceIn(0f, .95f))
            ).coerceIn(0f, 1f)
        val tiltExpansion = 1f + resolvedTilt *
            max(settings.tiltResponse, dynamics.tiltSize).coerceIn(0f, 1f) * .9f
        val velocityWidth = 1f -
            max(settings.velocitySize, dynamics.velocitySize).coerceIn(0f, 1f) *
            speedFactor.coerceIn(0f, 1f) * .62f
        paint.strokeWidth = settings.sizePx * pressureFactor * tiltExpansion * velocityWidth
        val pressureOpacity = if (settings.pressureOpacity) {
            .08f + opacityPressureValue * .92f
        } else {
            1f
        }
        val velocityOpacity = 1f -
            dynamics.velocityOpacity.coerceIn(0f, 1f) * speedFactor.coerceIn(0f, 1f) * .72f
        val tiltOpacity = 1f -
            dynamics.tiltOpacity.coerceIn(0f, 1f) * resolvedTilt * .45f
        val dynamicFlow = if (settings.pressureOpacity) {
            .18f + flowPressureValue * .82f
        } else {
            1f
        }
        val previewFactor = if (isPreview) 0.72f else 1f
        val renderMultiplier = if (drawingTool == DrawingTool.ERASER) {
            1f
        } else {
            renderAlphaMultiplier(settings.renderProfile) *
                (1f - settings.renderProfile.dilution.coerceIn(0f, 1f) * .48f)
        }
        paint.alpha = (
            settings.opacity * settings.flow * pressureOpacity * dynamicFlow * previewFactor *
                velocityOpacity * tiltOpacity * renderMultiplier * 255f
            )
            .toInt().coerceIn(1, 255)
        paint.color = resolvedColor
        paint.style = Paint.Style.STROKE
        paint.strokeCap = if (settings.kind == BrushKind.MARKER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.maskFilter = maskFilterFor(settings)
        if (drawingTool == DrawingTool.ERASER) {
            paint.xfermode = clearXfermode
            paint.maskFilter = null
        } else {
            paint.xfermode = null
        }
    }

    private fun drawBrushCursor(canvas: Canvas) {
        if (!hoverVisible || (tool != DrawingTool.BRUSH && tool != DrawingTool.ERASER)) return
        val radius = max(4f, brushSettings.sizePx * currentScale / 2f)
        canvas.drawCircle(hoverX, hoverY, radius + 1.5f, cursorOuterPaint)
        canvas.drawCircle(hoverX, hoverY, radius, cursorInnerPaint)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val stylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
            event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        if (!stylus) return super.onHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = event.x
                hoverY = event.y
                hoverVisible = true
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverVisible = false
                invalidate()
            }
        }
        return true
    }


    private fun normalizedRect(start: StrokePoint, end: StrokePoint): RectF = RectF(
        min(start.x, end.x),
        min(start.y, end.y),
        max(start.x, end.x),
        max(start.y, end.y),
    )

    private fun handleNavigationGesture(event: MotionEvent) {
        val activeIndices = (0 until event.pointerCount).filterNot { index ->
            event.actionMasked == MotionEvent.ACTION_POINTER_UP && index == event.actionIndex
        }
        if (activeIndices.isEmpty()) {
            navigationActive = false
            navigationInitialized = false
            return
        }

        val centroidX = activeIndices.map { event.getX(it) }.average().toFloat()
        val centroidY = activeIndices.map { event.getY(it) }.average().toFloat()
        val hasPair = activeIndices.size >= 2
        val first = activeIndices.first()
        val second = activeIndices.getOrNull(1)
        val span = if (second != null) {
            hypot(event.getX(second) - event.getX(first), event.getY(second) - event.getY(first))
        } else {
            0f
        }
        val angle = if (second != null) {
            Math.toDegrees(
                atan2(
                    (event.getY(second) - event.getY(first)).toDouble(),
                    (event.getX(second) - event.getX(first)).toDouble(),
                ),
            ).toFloat()
        } else {
            0f
        }

        fun resetBaseline() {
            lastGestureCentroidX = centroidX
            lastGestureCentroidY = centroidY
            lastGestureSpan = span
            lastGestureAngle = angle
            navigationInitialized = true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                navigationActive = true
                resetBaseline()
            }

            MotionEvent.ACTION_MOVE -> {
                navigationActive = true
                if (!navigationInitialized) {
                    resetBaseline()
                    return
                }

                transform.postTranslate(
                    centroidX - lastGestureCentroidX,
                    centroidY - lastGestureCentroidY,
                )

                if (hasPair && lastGestureSpan > 0.001f && span > 0.001f) {
                    val requestedFactor = span / lastGestureSpan
                    val desiredScale = (currentScale * requestedFactor).coerceIn(0.08f, 10f)
                    val appliedScale = desiredScale / currentScale
                    transform.postScale(appliedScale, appliedScale, centroidX, centroidY)
                    currentScale = desiredScale

                    val rotationDelta = normalizeDegrees(angle - lastGestureAngle)
                    if (abs(rotationDelta) <= 32f) {
                        transform.postRotate(rotationDelta, centroidX, centroidY)
                        currentRotationDegrees = normalizeDegrees(currentRotationDegrees + rotationDelta)
                    }
                    onZoomChanged?.invoke(zoomPercent())
                    onRotationChanged?.invoke(rotationDegrees())
                }

                resetBaseline()
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> resetBaseline()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                navigationActive = false
                navigationInitialized = false
            }
        }
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }


    private fun mapToDocument(x: Float, y: Float): FloatArray {
        transform.invert(inverse)
        return floatArrayOf(x, y).also { inverse.mapPoints(it) }
    }

    private fun sampleColor(x: Float, y: Float) {
        if (!documentBounds.contains(x, y)) return
        val sample = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val sampleCanvas = Canvas(sample)
        sampleCanvas.drawColor(Color.WHITE)
        layers.forEachIndexed { index, layer ->
            if (!isLayerEffectivelyVisible(layer)) return@forEachIndexed
            val checkpoint = sampleCanvas.saveLayer(RectF(0f, 0f, 1f, 1f), null)
            configureLayerPaint(layer, effectiveLayerOpacity(layer))
            layer.surface.drawAtPoint(sampleCanvas, x, y, layerPaint)
            rasterMaskPaint.alpha = 255
            if (layer.maskEnabled) layer.maskSurface?.drawAtPoint(sampleCanvas, x, y, rasterMaskPaint)
            if (layer.clipping && index > 0) {
                val base = layers[index - 1]
                if (!isLayerEffectivelyVisible(base)) {
                    sampleCanvas.restoreToCount(checkpoint)
                    return@forEachIndexed
                }
                clippingMaskPaint.alpha = (effectiveLayerOpacity(base) * 255f).toInt().coerceIn(0, 255)
                base.surface.drawAtPoint(sampleCanvas, x, y, clippingMaskPaint)
            }
            sampleCanvas.restoreToCount(checkpoint)
        }
        onColorPicked?.invoke(sample.getPixel(0, 0))
        sample.recycle()
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        val layer = layers.firstOrNull { it.id == entry.layerId } ?: return
        val commandIds = entry.commands.mapTo(mutableSetOf()) { it.id }
        commandsFor(layer, entry.target).removeAll { it.id in commandIds }
        redoStack += entry
        val affectedBounds = combinedBounds(entry.commands)
        rebuildLayerRegion(layer, affectedBounds, entry.target)
        markLayerDirty(layer, affectedBounds, entry.target)
        commitDocumentChange()
    }

    fun redo() {
        val entry = redoStack.removeLastOrNull() ?: return
        val layer = layers.firstOrNull { it.id == entry.layerId } ?: return
        commandsFor(layer, entry.target) += entry.commands
        undoStack += entry
        val affectedBounds = combinedBounds(entry.commands)
        rebuildLayerRegion(layer, affectedBounds, entry.target)
        markLayerDirty(layer, affectedBounds, entry.target)
        commitDocumentChange()
    }

    fun refreshLayerState() = notifyLayers()

    fun addLayer() {
        if (!canAllocateAnotherLayer()) {
            onEngineMessage?.invoke("Este documento alcanzó el límite de ${layerLimit()} capas para la memoria disponible.")
            return
        }
        val nextNumber = layers.size + 1
        val layer = createLayer("Capa $nextNumber").copy(groupId = activeLayer()?.groupId)
        layers += layer
        selectOnly(layer.id)
        updateCacheBudgets()
        notifyLayers()
        updateEngineStatus()
        commitDocumentChange()
    }

    fun duplicateActiveLayer() {
        if (!canAllocateAnotherLayer()) {
            onEngineMessage?.invoke("Este documento alcanzó el límite de ${layerLimit()} capas para la memoria disponible.")
            return
        }
        val source = activeLayer() ?: return
        if (!source.surface.flushPending()) {
            onEngineMessage?.invoke("No se pudo preparar la capa para duplicarla.")
            return
        }
        val copy = createLayer(
            name = "${source.name} copia",
            sourceDirectory = source.surface.workingDirectory,
        ).copy(
            visible = source.visible,
            opacity = source.opacity,
            blendMode = source.blendMode,
            alphaLocked = source.alphaLocked,
            clipping = source.clipping,
            groupId = source.groupId,
            maskEnabled = source.maskEnabled,
        )
        source.maskSurface?.let { sourceMask ->
            if (sourceMask.flushPending()) {
                val (mask, base) = createMaskSurface(copy.id, sourceMask.workingDirectory)
                copy.maskSurface = mask
                copy.maskBaseTileDirectory = base
                markLayerFullyDirty(copy, HistoryTarget.MASK)
            }
        }
        markLayerFullyDirty(copy)
        val index = layers.indexOf(source)
        layers.add(index + 1, copy)
        selectOnly(copy.id)
        updateCacheBudgets()
        notifyLayers()
        commitDocumentChange()
    }

    fun deleteActiveLayer() {
        val selected = selectedLayerIds.ifEmpty { setOf(activeLayerId) }
        val removable = layers.filter { it.id in selected }.take((layers.size - 1).coerceAtLeast(0))
        if (removable.isEmpty()) return
        val firstIndex = removable.minOf { layers.indexOf(it) }
        val removedIds = removable.mapTo(mutableSetOf()) { it.id }
        layers.removeAll { layer -> layer.id in removedIds }
        removable.forEach { removed ->
            (removed.commands + removed.maskCommands).distinctBy { it.id }.forEach(::recycleCommand)
            removed.surface.recycle()
            removed.maskSurface?.recycle()
            removed.baseTileDirectory.parentFile?.deleteRecursively()
            removed.maskBaseTileDirectory?.parentFile?.deleteRecursively()
        }
        undoStack.removeAll { it.layerId in removedIds }
        redoStack.removeAll { it.layerId in removedIds }
        pruneEmptyGroups()
        selectOnly(layers[min(firstIndex, layers.lastIndex)].id)
        updateCacheBudgets()
        notifyLayers()
        commitDocumentChange()
    }

    fun setActiveLayer(id: String) {
        if (layers.any { it.id == id }) {
            layers.filter { it.id != id }.forEach { it.editingMask = false }
            selectOnly(id)
            notifyLayers()
        }
    }

    fun toggleLayerSelection(id: String) {
        if (layers.none { it.id == id }) return
        if (id in selectedLayerIds && selectedLayerIds.size > 1) {
            selectedLayerIds -= id
            if (activeLayerId == id) activeLayerId = selectedLayerIds.last()
        } else {
            selectedLayerIds += id
            activeLayerId = id
            layers.filter { it.id != id }.forEach { it.editingMask = false }
        }
        notifyLayers()
    }

    fun toggleLayerVisibility(id: String) {
        layers.firstOrNull { it.id == id }?.let {
            it.visible = !it.visible
            notifyLayers()
            commitDocumentChange()
        }
    }

    fun setLayerOpacity(id: String, opacity: Float) {
        layers.firstOrNull { it.id == id }?.let {
            it.opacity = opacity.coerceIn(0f, 1f)
            notifyLayers()
            invalidate()
            onDocumentChanged?.invoke()
        }
    }

    fun setLayerBlendMode(id: String, blendMode: LayerBlendMode) {
        layers.firstOrNull { it.id == id }?.let {
            it.blendMode = blendMode
            notifyLayers()
            commitDocumentChange()
        }
    }

    fun setLayerAlphaLocked(id: String, locked: Boolean) {
        layers.firstOrNull { it.id == id }?.let { layer ->
            layer.alphaLocked = locked
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun setLayerClipping(id: String, clipping: Boolean) {
        layers.firstOrNull { it.id == id }?.let { layer ->
            layer.clipping = clipping
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun createGroupFromActiveLayer() {
        val selected = layers.filter { it.id in selectedLayerIds }.ifEmpty {
            listOfNotNull(activeLayer())
        }
        if (selected.isEmpty()) return
        val parentId = selected.map { it.groupId }.distinct().singleOrNull()
        val group = LayerGroupData(
            name = "Grupo ${layerGroups.size + 1}",
            parentGroupId = parentId,
        )
        layerGroups += group
        selected.forEach { it.groupId = group.id }
        notifyLayers()
        onDocumentChanged?.invoke()
    }

    fun ungroupActiveLayer() {
        val layer = activeLayer() ?: return
        val groupId = layer.groupId ?: return
        val parentId = layerGroups.firstOrNull { it.id == groupId }?.parentGroupId
        layers.filter { it.id in selectedLayerIds }.forEach { selected ->
            if (selected.groupId == groupId) selected.groupId = parentId
        }
        pruneEmptyGroups()
        notifyLayers()
        onDocumentChanged?.invoke()
    }

    fun toggleGroupVisibility(id: String) {
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.visible = !group.visible
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun toggleGroupCollapsed(id: String) {
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.collapsed = !group.collapsed
            notifyLayers()
        }
    }

    fun setGroupOpacity(id: String, opacity: Float) {
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.opacity = opacity.coerceIn(0f, 1f)
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun addMaskToActiveLayer() {
        val layer = activeLayer() ?: return
        if (layer.maskSurface != null) {
            layer.editingMask = true
            notifyLayers()
            return
        }
        val (mask, base) = createMaskSurface(layer.id)
        layer.maskSurface = mask
        layer.maskBaseTileDirectory = base
        layer.maskEnabled = true
        layer.editingMask = true
        updateCacheBudgets()
        notifyLayers()
        onEngineMessage?.invoke("Máscara activa: Pincel oculta y Borrador revela.")
        onDocumentChanged?.invoke()
    }

    fun setEditingLayerMask(id: String, editing: Boolean) {
        layers.forEach { it.editingMask = false }
        layers.firstOrNull { it.id == id && it.maskSurface != null }?.editingMask = editing
        notifyLayers()
    }

    fun toggleLayerMaskEnabled(id: String) {
        layers.firstOrNull { it.id == id && it.maskSurface != null }?.let { layer ->
            layer.maskEnabled = !layer.maskEnabled
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun deleteActiveLayerMask() {
        val layer = activeLayer() ?: return
        val mask = layer.maskSurface ?: return
        layer.maskCommands.distinctBy { it.id }.forEach(::recycleCommand)
        layer.maskCommands.clear()
        mask.recycle()
        layer.maskSurface = null
        layer.maskBaseTileDirectory?.parentFile?.deleteRecursively()
        layer.maskBaseTileDirectory = null
        layer.editingMask = false
        layer.maskEnabled = true
        undoStack.removeAll { it.layerId == layer.id && it.target == HistoryTarget.MASK }
        redoStack.removeAll { it.layerId == layer.id && it.target == HistoryTarget.MASK }
        updateCacheBudgets()
        notifyLayers()
        onDocumentChanged?.invoke()
    }

    fun moveActiveLayer(up: Boolean) {
        val selected = selectedLayerIds.ifEmpty { setOf(activeLayerId) }
        if (up) {
            for (index in layers.lastIndex - 1 downTo 0) {
                if (layers[index].id in selected && layers[index + 1].id !in selected) {
                    val layer = layers.removeAt(index)
                    layers.add(index + 1, layer)
                }
            }
        } else {
            for (index in 1..layers.lastIndex) {
                if (layers[index].id in selected && layers[index - 1].id !in selected) {
                    val layer = layers.removeAt(index)
                    layers.add(index - 1, layer)
                }
            }
        }
        commitDocumentChange()
    }

    fun clearActiveLayer() {
        val layer = activeLayer() ?: return
        val target = activeHistoryTarget(layer)
        commandsFor(layer, target).distinctBy { it.id }.forEach(::recycleCommand)
        commandsFor(layer, target).clear()
        val base = baseDirectoryFor(layer, target)
        base.deleteRecursively()
        base.mkdirs()
        surfaceFor(layer, target).clearAll()
        undoStack.removeAll { it.layerId == layer.id && it.target == target }
        redoStack.removeAll { it.layerId == layer.id && it.target == target }
        updateEngineStatus()
        commitDocumentChange()
    }

    fun renameActiveLayer(name: String) {
        val cleanName = name.trim().take(48)
        if (cleanName.isBlank()) return
        activeLayer()?.let { layer ->
            layer.name = cleanName
            commitDocumentChange()
        }
    }

    fun setGridVisible(visible: Boolean) {
        if (gridVisible == visible) return
        gridVisible = visible
        invalidate()
    }

    fun setRulersVisible(visible: Boolean) {
        if (rulersVisible == visible) return
        rulersVisible = visible
        invalidate()
    }

    fun setRulerUnitCentimeters(enabled: Boolean) {
        if (rulersUseCentimeters == enabled) return
        rulersUseCentimeters = enabled
        invalidate()
    }

    fun setDocumentDpi(dpi: Int) {
        documentDpi = dpi.coerceIn(36, 1200)
        if (rulersVisible && rulersUseCentimeters) invalidate()
    }

    fun setAngleSnappingEnabled(enabled: Boolean) {
        angleSnappingEnabled = enabled
    }

    fun setPerspectiveSnappingEnabled(enabled: Boolean) {
        perspectiveSnappingEnabled = enabled
    }

    fun areRulersVisible(): Boolean = rulersVisible

    fun areRulersUsingCentimeters(): Boolean = rulersUseCentimeters

    fun isAngleSnappingEnabled(): Boolean = angleSnappingEnabled

    fun isPerspectiveSnappingEnabled(): Boolean = perspectiveSnappingEnabled

    fun setVerticalSymmetry(enabled: Boolean) {
        if (verticalSymmetry == enabled) return
        verticalSymmetry = enabled
        if (enabled) radialSymmetryCount = 1
        invalidate()
    }

    fun setRadialSymmetry(count: Int) {
        val coercedCount = count.coerceIn(1, 16)
        if (radialSymmetryCount == coercedCount) return
        radialSymmetryCount = coercedCount
        if (radialSymmetryCount > 1) verticalSymmetry = false
        invalidate()
    }

    fun setGuideMode(mode: GuideMode) {
        if (guideMode == mode) return
        guideMode = mode
        if (mode == GuideMode.NONE) {
            perspectiveEditing = false
            draggedPerspectivePoint = 0
        }
        invalidate()
    }

    fun setPerspectiveEditing(enabled: Boolean) {
        val effectiveEnabled = enabled && guideMode != GuideMode.NONE
        if (perspectiveEditing == effectiveEnabled) return
        perspectiveEditing = effectiveEnabled
        draggedPerspectivePoint = 0
        invalidate()
    }

    fun currentGuideMode(): GuideMode = guideMode

    fun isPerspectiveEditing(): Boolean = perspectiveEditing

    fun resetPerspectiveGuides() {
        perspectivePoint1X = documentWidth * if (guideMode == GuideMode.PERSPECTIVE_TWO_POINT) 0.12f else 0.5f
        perspectivePoint1Y = documentHeight * 0.42f
        perspectivePoint2X = documentWidth * 0.88f
        perspectivePoint2Y = documentHeight * 0.42f
        invalidate()
        onDocumentChanged?.invoke()
    }

    fun resetView() {
        if (width <= 0 || height <= 0) return
        val margin = 64f
        val scale = min(
            (width - margin * 2f) / documentWidth.toFloat(),
            (height - margin * 2f) / documentHeight.toFloat(),
        ).coerceAtLeast(0.08f)
        currentScale = scale
        currentRotationDegrees = 0f
        transform.reset()
        transform.postScale(scale, scale)
        transform.postTranslate(
            (width - documentWidth * scale) / 2f,
            (height - documentHeight * scale) / 2f,
        )
        onZoomChanged?.invoke(zoomPercent())
        onRotationChanged?.invoke(rotationDegrees())
        invalidate()
    }

    fun rotateBy(degrees: Float) {
        if (width <= 0 || height <= 0) return
        val focusX = width / 2f
        val focusY = height / 2f
        transform.postRotate(degrees, focusX, focusY)
        currentRotationDegrees = normalizeDegrees(currentRotationDegrees + degrees)
        onRotationChanged?.invoke(rotationDegrees())
        invalidate()
    }

    fun rotationDegrees(): Int = currentRotationDegrees.toInt()

    fun zoomBy(factor: Float) {
        val focusX = width / 2f
        val focusY = height / 2f
        val desired = (currentScale * factor).coerceIn(0.12f, 8f)
        val applied = desired / currentScale
        transform.postScale(applied, applied, focusX, focusY)
        currentScale = desired
        onZoomChanged?.invoke(zoomPercent())
        invalidate()
    }

    fun zoomPercent(): Int = (currentScale * 100f).toInt().coerceAtLeast(1)

    fun exportCompositeBitmap(includePaper: Boolean = true): Bitmap {
        check(transformBitmap == null) { "Confirma la transformación antes de exportar." }
        val requiredBytes = documentWidth.toLong() * documentHeight.toLong() * 4L
        if (requiredBytes > Runtime.getRuntime().maxMemory() * 0.42) {
            throw IllegalStateException("El lienzo es demasiado grande para exportarlo como un bitmap único en este dispositivo.")
        }
        val output = Bitmap.createBitmap(documentWidth, documentHeight, Bitmap.Config.ARGB_8888)
        val outputCanvas = Canvas(output)
        if (includePaper) outputCanvas.drawColor(Color.WHITE)
        drawAllLayers(outputCanvas)
        updateEngineStatus()
        return output
    }

    fun exportBasicPsd(output: OutputStream) {
        val bitmap = exportCompositeBitmap(includePaper = false)
        try {
            BasicPsdCodec.write(bitmap, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderCompositePreview(maxSize: Int = 720): Bitmap {
        val scale = min(1f, maxSize / max(documentWidth, documentHeight).toFloat())
        val previewWidth = max(1, (documentWidth * scale).toInt())
        val previewHeight = max(1, (documentHeight * scale).toInt())
        val preview = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val previewCanvas = Canvas(preview)
        previewCanvas.drawColor(Color.WHITE)
        previewCanvas.scale(scale, scale)
        drawAllLayers(previewCanvas)
        return preview
    }

    private fun drawAllLayers(canvas: Canvas) {
        layers.forEachIndexed { index, layer ->
            if (!isLayerEffectivelyVisible(layer)) return@forEachIndexed
            val hasMask = layer.maskEnabled && layer.maskSurface != null
            val hasClippingBase = layer.clipping && index > 0
            if (!hasMask && !hasClippingBase) {
                configureLayerPaint(layer, effectiveLayerOpacity(layer))
                layer.surface.drawAll(canvas, layerPaint)
                return@forEachIndexed
            }

            val checkpoint = canvas.saveLayer(documentBounds, null)
            configureLayerPaint(layer, effectiveLayerOpacity(layer))
            layer.surface.drawAll(canvas, layerPaint)
            rasterMaskPaint.alpha = 255
            if (hasMask) layer.maskSurface?.drawAll(canvas, rasterMaskPaint)
            if (hasClippingBase) {
                val base = layers[index - 1]
                if (!isLayerEffectivelyVisible(base)) {
                    canvas.restoreToCount(checkpoint)
                    return@forEachIndexed
                }
                clippingMaskPaint.alpha = (effectiveLayerOpacity(base) * 255f).toInt().coerceIn(0, 255)
                base.surface.drawAll(canvas, clippingMaskPaint)
                if (base.maskEnabled) base.maskSurface?.drawAll(canvas, rasterMaskPaint)
            }
            canvas.restoreToCount(checkpoint)
        }
    }

    fun importBitmapAsLayer(source: Bitmap, name: String = "Imagen importada") {
        if (!canAllocateAnotherLayer()) {
            onEngineMessage?.invoke("Este documento alcanzó el límite de capas para la memoria disponible.")
            return
        }
        val scale = min(
            documentWidth / source.width.toFloat(),
            documentHeight / source.height.toFloat(),
        ).coerceAtMost(1f)
        val targetWidth = max(1, (source.width * scale).toInt())
        val targetHeight = max(1, (source.height * scale).toInt())
        val patch = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            .let { scaled ->
                if (scaled === source) source.copy(Bitmap.Config.ARGB_8888, false) else scaled
            }
        val left = (documentWidth - targetWidth) / 2f
        val top = (documentHeight - targetHeight) / 2f
        val layer = createLayer(name.trim().take(48).ifBlank { "Imagen importada" })
        val command = PixelPatchCommand(bitmap = patch, left = left, top = top)
        drawCommand(layer.surface, command)
        layers += layer
        selectOnly(layer.id)
        recordCommands(layer, listOf(command))
        markLayerDirty(layer, commandBounds(command))
        updateCacheBudgets()
        commitDocumentChange()
    }

    fun commitPendingTransform() {
        if (transformBitmap != null) commitSelectionTransform()
    }

    @androidx.annotation.WorkerThread
    fun exportOpenRaster(output: OutputStream) {
        val pixels = documentWidth.toLong() * documentHeight.toLong()
        require(pixels <= MAX_ORA_LAYER_PIXELS) {
            "El documento es demasiado grande para exportarlo como OpenRaster en este dispositivo."
        }
        ZipOutputStream(output.buffered()).use { zip ->
            val mimeBytes = "image/openraster".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc.value
            })
            zip.write(mimeBytes)
            zip.closeEntry()

            val visibleLayers = layers.asReversed()
            val stackXml = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                append("<image version=\"0.0.1\" w=\"").append(documentWidth)
                    .append("\" h=\"").append(documentHeight).append("\" name=\"Canvas Studio\">\n")
                append("  <stack name=\"root\">\n")
                visibleLayers.forEachIndexed { index, layer ->
                    append("    <layer name=\"").append(xmlEscape(layer.name)).append("\" src=\"data/layer")
                        .append(index.toString().padStart(3, '0')).append(".png\" visibility=\"")
                        .append(if (isLayerEffectivelyVisible(layer)) "visible" else "hidden")
                        .append("\" opacity=\"").append(effectiveLayerOpacity(layer)).append("\"/>\n")
                }
                append("  </stack>\n</image>\n")
            }
            zip.putNextEntry(ZipEntry("stack.xml"))
            zip.write(stackXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            visibleLayers.forEachIndexed { index, layer ->
                val bitmap = renderLayerForExport(layer)
                try {
                    writeBitmapEntry(zip, "data/layer${index.toString().padStart(3, '0')}.png", bitmap)
                } finally {
                    bitmap.recycle()
                }
            }

            val merged = exportCompositeBitmap(includePaper = false)
            try {
                writeBitmapEntry(zip, "mergedimage.png", merged)
                val thumbnailScale = min(1f, 256f / max(documentWidth, documentHeight).toFloat())
                val thumbnail = Bitmap.createScaledBitmap(
                    merged,
                    max(1, (documentWidth * thumbnailScale).toInt()),
                    max(1, (documentHeight * thumbnailScale).toInt()),
                    true,
                )
                try {
                    writeBitmapEntry(zip, "Thumbnails/thumbnail.png", thumbnail)
                } finally {
                    if (thumbnail !== merged) thumbnail.recycle()
                }
            } finally {
                merged.recycle()
            }
        }
    }

    @androidx.annotation.WorkerThread
    private fun renderLayerForExport(layer: LayerData): Bitmap {
        val bitmap = layer.surface.renderBitmap(MAX_ORA_LAYER_PIXELS)
        if (layer.maskEnabled) {
            layer.maskSurface?.let { mask ->
                Paint(rasterMaskPaint).apply { alpha = 255 }.also { maskPaint ->
                    mask.drawAll(Canvas(bitmap), maskPaint)
                }
            }
        }
        return bitmap
    }

    @androidx.annotation.WorkerThread
    private fun writeBitmapEntry(zip: ZipOutputStream, name: String, bitmap: Bitmap) {
        zip.putNextEntry(ZipEntry(name))
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
        zip.closeEntry()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun copyTileAtomically(source: File, destination: File): Boolean {
        if (!source.isFile) return false
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        return runCatching {
            FileInputStream(source).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (destination.exists() && !destination.delete()) error("No se pudo reemplazar el tile")
            if (!temporary.renameTo(destination)) error("No se pudo completar el tile")
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    fun saveProject(
        projectId: String,
        title: String,
        dpi: Int,
        includePreview: Boolean = true,
    ) {
        if (transformBitmap != null) commitSelectionTransform()

        data class LayerReference(
            val id: String,
            val name: String,
            val visible: Boolean,
            val opacity: Float,
            val blendMode: LayerBlendMode,
            val alphaLocked: Boolean,
            val clipping: Boolean,
            val groupId: String?,
            val maskEnabled: Boolean,
            val surface: SparseTileSurface,
            val maskSurface: SparseTileSurface?,
        )

        data class LayerSnapshot(
            val reference: LayerReference,
            val tileSnapshot: SparseTileSurface.SaveSnapshot,
            val maskSnapshot: SparseTileSurface.SaveSnapshot?,
        )

        // Metadata references are captured quickly on the UI thread. Tile PNG encoding is deferred
        // to the worker below, and SparseTileSurface only locks long enough to copy one tile.
        val layerReferences = layers.map { layer ->
            LayerReference(
                id = layer.id,
                name = layer.name,
                visible = layer.visible,
                opacity = layer.opacity,
                blendMode = layer.blendMode,
                alphaLocked = layer.alphaLocked,
                clipping = layer.clipping,
                groupId = layer.groupId,
                maskEnabled = layer.maskEnabled,
                surface = layer.surface,
                maskSurface = layer.maskSurface,
            )
        }
        val groupReferences = layerGroups.map { group -> group.copy() }
        val savedGuideMode = guideMode
        val savedRulersVisible = rulersVisible
        val savedRulersUseCentimeters = rulersUseCentimeters
        val savedVerticalRulerGuides = verticalRulerGuides.toList()
        val savedHorizontalRulerGuides = horizontalRulerGuides.toList()
        val savedAngleSnappingEnabled = angleSnappingEnabled
        val savedPerspectiveSnappingEnabled = perspectiveSnappingEnabled
        val savedPerspectiveEditing = perspectiveEditing
        val savedPerspectivePoint1X = perspectivePoint1X
        val savedPerspectivePoint1Y = perspectivePoint1Y
        val savedPerspectivePoint2X = perspectivePoint2X
        val savedPerspectivePoint2Y = perspectivePoint2Y
        val previewBitmap = if (includePreview) {
            runCatching { renderCompositePreview() }.getOrElse {
                onProjectSaved?.invoke(false)
                return
            }
        } else {
            null
        }
        val savedWidth = documentWidth
        val savedHeight = documentHeight
        val generation = ++saveGeneration

        Thread {
            synchronized(saveLock) {
                if (generation != saveGeneration) {
                    previewBitmap?.recycle()
                    return@synchronized
                }

                // Snapshot and project copy share one worker lock. This prevents two debounced
                // saves from encoding the same tile and competing for its atomic .tmp file.
                val layerSnapshots = runCatching {
                    layerReferences.map { reference ->
                        LayerSnapshot(
                            reference = reference,
                            tileSnapshot = reference.surface.saveSnapshot(),
                            maskSnapshot = reference.maskSurface?.saveSnapshot(),
                        )
                    }
                }.getOrElse {
                    previewBitmap?.recycle()
                    post { onProjectSaved?.invoke(false) }
                    return@synchronized
                }

                if (generation != saveGeneration) {
                    previewBitmap?.recycle()
                    return@synchronized
                }

                val directory = ProjectRepository.projectDirectory(context, projectId)
                try {
                    val layersRoot = File(directory, "layers").apply { mkdirs() }
                    val activeLayerIds = layerSnapshots.mapTo(mutableSetOf()) { it.reference.id }
                    layersRoot.listFiles().orEmpty()
                        .filter { it.isDirectory && it.name !in activeLayerIds }
                        .forEach(File::deleteRecursively)

                    layerSnapshots.forEach { snapshot ->
                        val reference = snapshot.reference
                        val tileDirectory = layerTileDirectory(directory, reference.id).apply { mkdirs() }
                        snapshot.tileSnapshot.dirtyVersions.keys.forEach { key ->
                            check(copyTileAtomically(reference.surface.fileFor(key), File(tileDirectory, key.fileName))) {
                                "No se pudo guardar el tile ${key.fileName}"
                            }
                        }
                        snapshot.tileSnapshot.deletedVersions.keys.forEach { key ->
                            File(tileDirectory, key.fileName).delete()
                            File(tileDirectory, "${key.fileName}.tmp").delete()
                        }
                        val maskSurface = reference.maskSurface
                        val maskSnapshot = snapshot.maskSnapshot
                        val maskDirectory = maskTileDirectory(directory, reference.id)
                        if (maskSurface != null && maskSnapshot != null) {
                            maskDirectory.mkdirs()
                            maskSnapshot.dirtyVersions.keys.forEach { key ->
                                check(copyTileAtomically(maskSurface.fileFor(key), File(maskDirectory, key.fileName))) {
                                    "No se pudo guardar el tile de máscara ${key.fileName}"
                                }
                            }
                            maskSnapshot.deletedVersions.keys.forEach { key ->
                                File(maskDirectory, key.fileName).delete()
                                File(maskDirectory, "${key.fileName}.tmp").delete()
                            }
                        } else {
                            maskDirectory.deleteRecursively()
                        }
                    }

                    if (previewBitmap != null) {
                        val previewTemporary = File(directory, "preview.png.tmp")
                        FileOutputStream(previewTemporary).use { output ->
                            check(previewBitmap.compress(Bitmap.CompressFormat.PNG, 92, output))
                            output.fd.sync()
                        }
                        val previewDestination = File(directory, "preview.png")
                        if (previewDestination.exists() && !previewDestination.delete()) {
                            error("No se pudo reemplazar la vista previa")
                        }
                        if (!previewTemporary.renameTo(previewDestination)) {
                            error("No se pudo completar la vista previa")
                        }
                    }

                    val properties = Properties().apply {
                        setProperty("version", "7")
                        setProperty("id", projectId)
                        setProperty("title", title)
                        setProperty("width", savedWidth.toString())
                        setProperty("height", savedHeight.toString())
                        setProperty("dpi", dpi.toString())
                        setProperty("modifiedEpoch", System.currentTimeMillis().toString())
                        setProperty("layerCount", layerSnapshots.size.toString())
                        setProperty("storageMode", "sparse-tiled-raster")
                        setProperty("tileSize", TileStorage.TILE_SIZE.toString())
                        setProperty("renderer", "low-latency-visible-tiles-lru")
                        setProperty("groupCount", groupReferences.size.toString())
                        groupReferences.forEachIndexed { index, group ->
                            setProperty("group.$index.id", group.id)
                            setProperty("group.$index.name", group.name)
                            setProperty("group.$index.visible", group.visible.toString())
                            setProperty("group.$index.opacity", group.opacity.toString())
                            setProperty("group.$index.collapsed", group.collapsed.toString())
                            setProperty("group.$index.parentGroupId", group.parentGroupId.orEmpty())
                        }
                        setProperty("guideMode", savedGuideMode.name)
                        setProperty("rulersVisible", savedRulersVisible.toString())
                        setProperty("rulersUseCentimeters", savedRulersUseCentimeters.toString())
                        setProperty("rulerGuides.vertical", savedVerticalRulerGuides.joinToString(","))
                        setProperty("rulerGuides.horizontal", savedHorizontalRulerGuides.joinToString(","))
                        setProperty("angleSnappingEnabled", savedAngleSnappingEnabled.toString())
                        setProperty("perspectiveSnappingEnabled", savedPerspectiveSnappingEnabled.toString())
                        setProperty("perspectiveEditing", savedPerspectiveEditing.toString())
                        setProperty("perspective.point1.x", savedPerspectivePoint1X.toString())
                        setProperty("perspective.point1.y", savedPerspectivePoint1Y.toString())
                        setProperty("perspective.point2.x", savedPerspectivePoint2X.toString())
                        setProperty("perspective.point2.y", savedPerspectivePoint2Y.toString())
                        layerSnapshots.forEachIndexed { index, snapshot ->
                            val layer = snapshot.reference
                            setProperty("layer.$index.id", layer.id)
                            setProperty("layer.$index.name", layer.name)
                            setProperty("layer.$index.visible", layer.visible.toString())
                            setProperty("layer.$index.opacity", layer.opacity.toString())
                            setProperty("layer.$index.blendMode", layer.blendMode.name)
                            setProperty("layer.$index.alphaLocked", layer.alphaLocked.toString())
                            setProperty("layer.$index.clipping", layer.clipping.toString())
                            setProperty("layer.$index.groupId", layer.groupId.orEmpty())
                            setProperty("layer.$index.hasMask", (layer.maskSurface != null).toString())
                            setProperty("layer.$index.maskEnabled", layer.maskEnabled.toString())
                            setProperty("layer.$index.tilePath", "layers/${layer.id}/tiles")
                            if (layer.maskSurface != null) {
                                setProperty("layer.$index.maskTilePath", "layers/${layer.id}/mask")
                            }
                        }
                    }
                    val metadataTemporary = File(directory, "project.properties.tmp")
                    FileOutputStream(metadataTemporary).use { output ->
                        properties.store(output, "Canvas Studio sparse tiled project")
                        output.fd.sync()
                    }
                    val metadata = File(directory, "project.properties")
                    val metadataBackup = File(directory, "project.properties.bak")
                    if (metadata.exists()) {
                        metadata.copyTo(metadataBackup, overwrite = true)
                        if (!metadata.delete()) error("No se pudo reemplazar la metadata")
                    }
                    if (!metadataTemporary.renameTo(metadata)) {
                        if (metadataBackup.exists()) metadataBackup.copyTo(metadata, overwrite = true)
                        error("No se pudo completar el guardado del proyecto")
                    }
                    metadataBackup.delete()

                    directory.listFiles { file ->
                        file.name.startsWith("layer-") && (file.extension == "png" || file.extension == "tmp")
                    }.orEmpty().forEach(File::delete)
                    File(directory, "flattened.png").delete()
                    File(directory, "flattened.tmp").delete()
                    ProjectVersionStore.maybeSnapshot(context, projectId, directory)

                    post {
                        layerSnapshots.forEach { snapshot ->
                            val currentLayer = layers.firstOrNull { it.id == snapshot.reference.id }
                                ?: return@forEach
                            currentLayer.surface.acknowledgeSave(snapshot.tileSnapshot)
                            val maskSnapshot = snapshot.maskSnapshot
                            if (maskSnapshot != null) currentLayer.maskSurface?.acknowledgeSave(maskSnapshot)
                        }
                        updateEngineStatus()
                        onProjectSaved?.invoke(true)
                    }
                } catch (error: Throwable) {
                    post {
                        onProjectSaved?.invoke(false)
                        onEngineMessage?.invoke(
                            error.message?.let { "No se pudo guardar el proyecto: $it" }
                                ?: "No se pudo guardar el proyecto.",
                        )
                    }
                } finally {
                    previewBitmap?.recycle()
                }
            }
        }.apply {
            name = "canvas-project-save"
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun loadProject(projectId: String): Boolean {
        val directory = ProjectRepository.projectDirectory(context, projectId)
        val metadata = ProjectRepository.metadataFile(context, projectId)
        if (!metadata.isFile) return false
        return runCatching {
            val properties = Properties().apply {
                FileInputStream(metadata).use { input -> load(input) }
            }
            val storedWidth = properties.getProperty("width", documentWidth.toString()).toInt()
            val storedHeight = properties.getProperty("height", documentHeight.toString()).toInt()
            createEmptyDocument(storedWidth, storedHeight)

            layers.forEach { layer ->
                layer.surface.recycle()
                layer.maskSurface?.recycle()
                layer.baseTileDirectory.parentFile?.deleteRecursively()
                layer.maskBaseTileDirectory?.parentFile?.deleteRecursively()
            }
            layers.clear()
            layerGroups.clear()
            selectedLayerIds.clear()

            val groupCount = properties.getProperty("groupCount", "0").toIntOrNull() ?: 0
            repeat(groupCount) { index ->
                layerGroups += LayerGroupData(
                    id = properties.getProperty("group.$index.id", UUID.randomUUID().toString()),
                    name = properties.getProperty("group.$index.name", "Grupo ${index + 1}"),
                    visible = properties.getProperty("group.$index.visible", "true").toBoolean(),
                    opacity = properties.getProperty("group.$index.opacity", "1.0")
                        .toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                    collapsed = properties.getProperty("group.$index.collapsed", "false").toBoolean(),
                    parentGroupId = properties.getProperty("group.$index.parentGroupId", "").ifBlank { null },
                )
            }
            guideMode = runCatching {
                GuideMode.valueOf(properties.getProperty("guideMode", GuideMode.NONE.name))
            }.getOrDefault(GuideMode.NONE)
            rulersVisible = properties.getProperty("rulersVisible", "false").toBoolean()
            rulersUseCentimeters = properties.getProperty("rulersUseCentimeters", "false").toBoolean()
            verticalRulerGuides.clear()
            verticalRulerGuides += properties.getProperty("rulerGuides.vertical", "")
                .split(',')
                .mapNotNull { it.toFloatOrNull()?.coerceIn(0f, documentWidth.toFloat()) }
            horizontalRulerGuides.clear()
            horizontalRulerGuides += properties.getProperty("rulerGuides.horizontal", "")
                .split(',')
                .mapNotNull { it.toFloatOrNull()?.coerceIn(0f, documentHeight.toFloat()) }
            angleSnappingEnabled = properties.getProperty("angleSnappingEnabled", "false").toBoolean()
            perspectiveSnappingEnabled =
                properties.getProperty("perspectiveSnappingEnabled", "false").toBoolean()
            perspectiveEditing = properties.getProperty("perspectiveEditing", "false").toBoolean() &&
                guideMode != GuideMode.NONE
            perspectivePoint1X = properties.getProperty("perspective.point1.x", (documentWidth * 0.5f).toString())
                .toFloatOrNull()?.coerceIn(0f, documentWidth.toFloat()) ?: documentWidth * 0.5f
            perspectivePoint1Y = properties.getProperty("perspective.point1.y", (documentHeight * 0.42f).toString())
                .toFloatOrNull()?.coerceIn(0f, documentHeight.toFloat()) ?: documentHeight * 0.42f
            perspectivePoint2X = properties.getProperty("perspective.point2.x", (documentWidth * 0.88f).toString())
                .toFloatOrNull()?.coerceIn(0f, documentWidth.toFloat()) ?: documentWidth * 0.88f
            perspectivePoint2Y = properties.getProperty("perspective.point2.y", (documentHeight * 0.42f).toString())
                .toFloatOrNull()?.coerceIn(0f, documentHeight.toFloat()) ?: documentHeight * 0.42f

            val layerCount = properties.getProperty("layerCount", "0").toIntOrNull() ?: 0
            val storageMode = properties.getProperty("storageMode", "layered-raster")
            val storedVersion = properties.getProperty("version", "2").toIntOrNull() ?: 2
            val isTiledProject = storageMode == "tiled-raster" ||
                storageMode == "sparse-tiled-raster" ||
                storedVersion >= 3

            if (layerCount > 0 && isTiledProject) {
                repeat(layerCount) { index ->
                    val layerId = properties.getProperty("layer.$index.id", UUID.randomUUID().toString())
                    val relativeTilePath = properties.getProperty(
                        "layer.$index.tilePath",
                        "layers/${layerId}/tiles",
                    )
                    val sourceTileDirectory = File(directory, relativeTilePath)
                    val layer = createLayer(
                        name = properties.getProperty("layer.$index.name", "Capa ${index + 1}"),
                        id = layerId,
                        sourceDirectory = sourceTileDirectory,
                    ).copy(
                        visible = properties.getProperty("layer.$index.visible", "true").toBoolean(),
                        opacity = properties.getProperty("layer.$index.opacity", "1.0")
                            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                        blendMode = runCatching {
                            LayerBlendMode.valueOf(
                                properties.getProperty("layer.$index.blendMode", LayerBlendMode.NORMAL.name),
                            )
                        }.getOrDefault(LayerBlendMode.NORMAL),
                        alphaLocked = properties.getProperty("layer.$index.alphaLocked", "false").toBoolean(),
                        clipping = properties.getProperty("layer.$index.clipping", "false").toBoolean(),
                        groupId = properties.getProperty("layer.$index.groupId", "").ifBlank { null },
                        maskEnabled = properties.getProperty("layer.$index.maskEnabled", "true").toBoolean(),
                    )
                    if (properties.getProperty("layer.$index.hasMask", "false").toBoolean()) {
                        val maskRelativePath = properties.getProperty(
                            "layer.$index.maskTilePath",
                            "layers/${layerId}/mask",
                        )
                        val (mask, base) = createMaskSurface(layerId, File(directory, maskRelativePath))
                        layer.maskSurface = mask
                        layer.maskBaseTileDirectory = base
                    }
                    layers += layer
                }
            } else if (layerCount > 0) {
                repeat(layerCount) { index ->
                    val file = File(directory, "layer-${index.toString().padStart(3, '0')}.png")
                    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@repeat
                    val bitmap = if (decoded.width == documentWidth && decoded.height == documentHeight) {
                        decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
                    } else {
                        Bitmap.createScaledBitmap(decoded, documentWidth, documentHeight, true)
                            .also { decoded.recycle() }
                    }
                    val layer = createLayer(
                        name = properties.getProperty("layer.$index.name", "Capa ${index + 1}"),
                        id = properties.getProperty("layer.$index.id", UUID.randomUUID().toString()),
                    ).copy(
                        visible = properties.getProperty("layer.$index.visible", "true").toBoolean(),
                        opacity = properties.getProperty("layer.$index.opacity", "1.0")
                            .toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                        blendMode = runCatching {
                            LayerBlendMode.valueOf(
                                properties.getProperty("layer.$index.blendMode", LayerBlendMode.NORMAL.name),
                            )
                        }.getOrDefault(LayerBlendMode.NORMAL),
                        alphaLocked = properties.getProperty("layer.$index.alphaLocked", "false").toBoolean(),
                        clipping = properties.getProperty("layer.$index.clipping", "false").toBoolean(),
                        groupId = properties.getProperty("layer.$index.groupId", "").ifBlank { null },
                        maskEnabled = properties.getProperty("layer.$index.maskEnabled", "true").toBoolean(),
                    )
                    layer.surface.replaceFromBitmap(bitmap, markProjectDirty = true)
                    check(layer.surface.copyCurrentTo(layer.baseTileDirectory))
                    bitmap.recycle()
                    layers += layer
                }
            }

            if (layers.isEmpty()) {
                val flattened = File(directory, "flattened.png")
                val source = BitmapFactory.decodeFile(flattened.absolutePath) ?: return@runCatching false
                val bitmap = if (source.width == documentWidth && source.height == documentHeight) {
                    source.copy(Bitmap.Config.ARGB_8888, true).also { source.recycle() }
                } else {
                    Bitmap.createScaledBitmap(source, documentWidth, documentHeight, true)
                        .also { source.recycle() }
                }
                val layer = createLayer("Arte guardado")
                layer.surface.replaceFromBitmap(bitmap, markProjectDirty = true)
                check(layer.surface.copyCurrentTo(layer.baseTileDirectory))
                bitmap.recycle()
                layers += layer
            }

            pruneEmptyGroups()
            selectOnly(layers.last().id)
            undoStack.clear()
            clearRedoHistory()
            updateCacheBudgets()
            notifyLayers()
            updateEngineStatus()
            fittedOnce = false
            if (width > 0 && height > 0) {
                resetView()
                fittedOnce = true
            }
            invalidate()
            true
        }.getOrDefault(false)
    }

    fun saveAutosave() {
        saveProject("autosave", "Guardado automático", 300, includePreview = false)
    }

    private fun configureLayerPaint(layer: LayerData, effectiveOpacity: Float = layer.opacity) {
        layerPaint.alpha = (effectiveOpacity * 255f).toInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            layerPaint.xfermode = null
            layerPaint.blendMode = when (layer.blendMode) {
                LayerBlendMode.NORMAL -> null
                LayerBlendMode.MULTIPLY -> BlendMode.MULTIPLY
                LayerBlendMode.SCREEN -> BlendMode.SCREEN
                LayerBlendMode.OVERLAY -> BlendMode.OVERLAY
                LayerBlendMode.ADD -> BlendMode.PLUS
                LayerBlendMode.DARKEN -> BlendMode.DARKEN
                LayerBlendMode.LIGHTEN -> BlendMode.LIGHTEN
                LayerBlendMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
                LayerBlendMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
                LayerBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
                LayerBlendMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
                LayerBlendMode.COLOR_BURN -> BlendMode.COLOR_BURN
            }
        } else {
            layerPaint.xfermode = when (layer.blendMode) {
                LayerBlendMode.NORMAL -> null
                LayerBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                LayerBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                LayerBlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                LayerBlendMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                LayerBlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                LayerBlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                LayerBlendMode.SOFT_LIGHT,
                LayerBlendMode.HARD_LIGHT,
                LayerBlendMode.DIFFERENCE,
                LayerBlendMode.COLOR_DODGE,
                LayerBlendMode.COLOR_BURN,
                -> null
            }
        }
    }

    private fun rebuildAllLayers() {
        layers.forEach { layer ->
            rebuildLayer(layer, HistoryTarget.CONTENT)
            if (layer.maskSurface != null) rebuildLayer(layer, HistoryTarget.MASK)
        }
        invalidate()
    }

    private fun rebuildLayerRegion(
        layer: LayerData,
        bounds: RectF,
        target: HistoryTarget = activeHistoryTarget(layer),
    ) {
        if (bounds.isEmpty) return
        val safeBounds = RectF(bounds).apply { intersect(documentBounds) }
        if (safeBounds.isEmpty) return
        val tileBounds = TileStorage.tileAlignedBounds(safeBounds, documentWidth, documentHeight)
        if (tileBounds.isEmpty) return
        val surface = surfaceFor(layer, target)
        surface.resetRegionFrom(baseDirectoryFor(layer, target), tileBounds)
        commandsFor(layer, target).forEach { command ->
            if (RectF.intersects(commandBounds(command), tileBounds)) {
                drawCommand(surface, command, replayClipBounds = tileBounds)
            }
        }
        scheduleEngineStatusUpdate()
        invalidate()
    }

    private fun rebuildLayer(layer: LayerData, target: HistoryTarget = HistoryTarget.CONTENT) {
        val surface = surfaceFor(layer, target)
        surface.resetWorkingFrom(baseDirectoryFor(layer, target))
        commandsFor(layer, target).forEach { command -> drawCommand(surface, command) }
        updateEngineStatus()
        invalidate()
    }

    private fun activeLayer(): LayerData? = layers.firstOrNull { it.id == activeLayerId }

    private fun selectOnly(id: String) {
        activeLayerId = id
        selectedLayerIds.clear()
        selectedLayerIds += id
    }

    private fun pruneEmptyGroups() {
        var changed: Boolean
        do {
            val occupied = layers.mapNotNullTo(mutableSetOf()) { it.groupId }
            occupied += layerGroups.mapNotNull { it.parentGroupId }
            changed = layerGroups.removeAll { it.id !in occupied }
        } while (changed)
        val valid = layerGroups.mapTo(mutableSetOf()) { it.id }
        layerGroups.forEach { group ->
            if (group.parentGroupId !in valid) group.parentGroupId = null
        }
        layers.forEach { layer ->
            if (layer.groupId !in valid) layer.groupId = null
        }
    }

    private fun notifyLayers() {
        onLayersChanged?.invoke(
            layers.asReversed().map { layer ->
                LayerUiModel(
                    id = layer.id,
                    name = layer.name,
                    visible = layer.visible,
                    opacity = layer.opacity,
                    blendMode = layer.blendMode,
                    alphaLocked = layer.alphaLocked,
                    clipping = layer.clipping,
                    isActive = layer.id == activeLayerId,
                    isSelected = layer.id in selectedLayerIds,
                    groupId = layer.groupId,
                    hasMask = layer.maskSurface != null,
                    maskEnabled = layer.maskEnabled,
                    editingMask = layer.editingMask,
                )
            },
        )
        onLayerGroupsChanged?.invoke(
            layerGroups.map { group ->
                val depth = groupAncestors(group.parentGroupId).count().coerceAtMost(8)
                LayerGroupUiModel(
                    id = group.id,
                    name = group.name,
                    visible = group.visible,
                    opacity = group.opacity,
                    collapsed = group.collapsed,
                    layerCount = layers.count { it.groupId == group.id },
                    parentGroupId = group.parentGroupId,
                    depth = depth,
                )
            },
        )
        scheduleEngineStatusUpdate()
        invalidate()
    }

    private fun commitDocumentChange() {
        notifyLayers()
        onDocumentChanged?.invoke()
        invalidate()
    }

    private fun clearRedoHistory() {
        redoStack.flatMap { it.commands }.distinctBy { it.id }.forEach(::recycleCommand)
        redoStack.clear()
    }

    private fun recycleCommand(command: DrawCommand) {
        when (command) {
            is PixelPatchCommand -> if (!command.bitmap.isRecycled) command.bitmap.recycle()
            is TransformSelectionCommand -> if (!command.bitmap.isRecycled) command.bitmap.recycle()
            else -> Unit
        }
    }

    private companion object {
        const val MAX_FLOOD_FILL_PIXELS = 12_000_000L
        const val MAX_ORA_LAYER_PIXELS = 40_000_000L
        const val ENGINE_STATUS_INTERVAL_MS = 650L
        const val MAX_STAMPS_PER_SEGMENT = 48
        const val MAX_STROKE_SEGMENTS_PER_FRAME = 24
    }
}
