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
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.os.SystemClock
import android.util.AtomicFile
import android.view.View
import android.view.ViewConfiguration
import com.orbyte.canvasstudio.BuildConfig
import com.orbyte.canvasstudio.drawing.brush.BrushDabBatchBuilder
import com.orbyte.canvasstudio.drawing.brush.BrushEvaluator
import com.orbyte.canvasstudio.drawing.input.StrokeSampler
import com.orbyte.canvasstudio.drawing.input.StrokeInputBatch
import com.orbyte.canvasstudio.drawing.pipeline.StrokeFrameBudget
import com.orbyte.canvasstudio.drawing.pipeline.StrokePreviewRenderer
import com.orbyte.canvasstudio.drawing.pipeline.CoalescingSerialWorker
import com.orbyte.canvasstudio.drawing.pipeline.OrderedRasterCommandQueue
import com.orbyte.canvasstudio.drawing.pipeline.StrokeCommitLane
import com.orbyte.canvasstudio.drawing.raster.DetachedStrokeRasterizer
import com.orbyte.canvasstudio.drawing.raster.PreviewCompositor
import com.orbyte.canvasstudio.drawing.input.QuickShapeRecognizer
import com.orbyte.canvasstudio.drawing.input.StylusInputController
import com.orbyte.canvasstudio.drawing.history.HistoryRasterTarget
import com.orbyte.canvasstudio.drawing.history.HistorySurfaceKey
import com.orbyte.canvasstudio.drawing.history.TileCommandIndex
import com.orbyte.canvasstudio.drawing.history.TileCheckpointKey
import com.orbyte.canvasstudio.drawing.history.TileCheckpointPolicy
import com.orbyte.canvasstudio.drawing.history.TileCheckpointStore
import com.orbyte.canvasstudio.drawing.performance.DrawingPerformanceMetrics
import com.orbyte.canvasstudio.drawing.raster.BitmapCanvasTileRasterBackend
import com.orbyte.canvasstudio.drawing.raster.RasterDabRequest
import com.orbyte.canvasstudio.drawing.raster.RendererMode
import com.orbyte.canvasstudio.drawing.raster.TileRasterBackend
import com.orbyte.canvasstudio.drawing.raster.VulkanBrushMaterial
import com.orbyte.canvasstudio.drawing.raster.VulkanRasterStats
import com.orbyte.canvasstudio.drawing.raster.VulkanTileRasterBackend
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
import com.orbyte.canvasstudio.model.ProjectSaveTransaction
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

internal fun radialQuickMenuSlot(dx: Float, dy: Float, threshold: Float): Int? {
    if (hypot(dx, dy) < threshold) return null
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    return (((degrees + 90.0 + 30.0 + 360.0) % 360.0) / 60.0).toInt().coerceIn(0, 5)
}

class DrawingView(context: Context) : View(context) {
    private val mainHandler = Handler(Looper.getMainLooper())
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
    // Repeating 96 px bitmap replaces potentially thousands of rectangle draws per frame.
    private val checkerboardPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(
            CheckerboardTexture.create(checkerLight.color, checkerDark.color),
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT,
        )
    }
    // Tiles share exact edges. Bilinear sampling blends the transparent outer pixel of a tile
    // with its neighbour at fractional zoom and produces a visible 512 px checker/grid.
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clippingMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val rasterMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
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
    @Volatile private var committedRevision = 0L
    private val layers = mutableListOf<LayerData>()
    private val layerGroups = mutableListOf<LayerGroupData>()
    private var activeLayerId: String = ""
    private val selectedLayerIds = linkedSetOf<String>()
    private val undoStack = mutableListOf<HistoryEntry>()
    private val redoStack = mutableListOf<HistoryEntry>()
    private val tileCommandIndex = TileCommandIndex()
    private val commandRegistry = mutableMapOf<String, DrawCommand>()
    private val checkpointBudgetBytes = (Runtime.getRuntime().maxMemory() * .035).toLong()
        .coerceIn(4L * 1024L * 1024L, 32L * 1024L * 1024L)
    private val tileCheckpointStore = TileCheckpointStore(checkpointBudgetBytes)
    internal var tileCheckpointPolicy = TileCheckpointPolicy()

    private val transform = Matrix()
    private val inverse = Matrix()
    private val viewportInverseCoords = FloatArray(8)
    // Reused only on the UI thread during active ink rasterization.
    private val strokeTransformBatch = StrokeBatchTransform()
    private val strokeInputBatch = StrokeInputBatch()
    private val strokeFrameBudget = StrokeFrameBudget()
    private val viewportVisibleBounds = RectF()
    private var currentScale = 1f
    private var currentRotationDegrees = 0f
    private var fittedOnce = false
    private var navigationActive = false
    private var navigationInitialized = false
    private var lastGestureCentroidX = 0f
    private var lastGestureCentroidY = 0f
    private var navigationDistancePx = 0f
    private var navigationScaleFactor = 1f
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
    private var cachedSymmetryCount = Int.MIN_VALUE
    private var cachedVerticalSymmetry = false
    private var cachedSymmetryWidth = -1
    private var cachedSymmetryHeight = -1
    private var cachedSymmetryMatrices: List<Matrix> = emptyList()
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
    // Background prefetch is prepared serially, then published on the main
    // thread; stale generations cannot update the editor.
    private val rasterWorkQueue = OrderedRasterCommandQueue(prefetchExecutor) { callback ->
        mainHandler.post(callback)
    }
    @Volatile private var prefetchInFlight = false
    private var lastPrefetchBounds = RectF()
    private val prefetchedBounds = RectF()
    private val saveLock = Any()
    // One running project save + at most one queued latest save. Older debounced
    // autosaves never spawn overlapping encode threads or exhaust the queue.
    private val saveWorker = CoalescingSerialWorker.background("canvas-project-save") { error ->
        android.util.Log.e("CanvasStudio", "Project save worker failed", error)
        mainHandler.post { onProjectSaved?.invoke(false) }
    }
    @Volatile private var saveGeneration = 0
    // Gallery demo rasterization happens on another thread. Autosave must not
    // snapshot files until demo tile generation has fully completed.
    @Volatile private var demoSeedThread: Thread? = null
    private var lastEngineStatus = ""
    private var engineStatusUpdatePosted = false
    private var lastEngineStatusUpdateUptime = 0L
    private val performanceMetrics = DrawingPerformanceMetrics(BuildConfig.DEBUG)
    // Isolated mutable paints, shaders and brush textures; UI-thread owner.
    private val brushRasterSession = BrushRasterSession(performanceMetrics)
    private val strokeCommandPainter = StrokeCommandPainter(brushRasterSession)
    private var activeStrokeInputStartedNanos = 0L
    private val bitmapRasterBackend: TileRasterBackend = BitmapCanvasTileRasterBackend()
    private var vulkanRasterBackend: VulkanTileRasterBackend? = null
    private var requestedRendererMode: RendererMode = RendererMode.CANVAS_BITMAP
    private var rendererFallbackCount = 0L
    private var simulateVulkanFailure = false
    private val globalTileCacheBudgetBytes: Long =
        (Runtime.getRuntime().maxMemory() * 0.12)
            .toLong()
            .coerceIn(20L * 1024L * 1024L, 80L * 1024L * 1024L)

    private val strokePreviewRenderer = StrokePreviewRenderer()
    // Strictly experimental. The proven synchronous renderer remains the
    // default until end-to-end stylus latency and pixel parity are certified.
    internal var debugAsyncStrokeRasterEnabled: Boolean = false
    private val strokeCommitWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "canvas-stroke-commit").apply { isDaemon = true }
    }
    private val strokeCommitLane = StrokeCommitLane<DetachedStrokeRasterizer.PreparedBatch>(
        strokeCommitWorker,
        { callback -> mainHandler.post(callback) },
    ) { error ->
        android.util.Log.e("CanvasStudio", "Stroke commit failed", error)
        onEngineMessage?.invoke("Error al procesar un trazo; se usará el motor CPU.")
    }
    private data class QueuedStrokePreview(
        val command: StrokeCommand,
    )
    private val queuedStrokePreviews = mutableListOf<QueuedStrokePreview>()
    internal fun debugPendingStrokeCommits(): Int = strokeCommitLane.pendingCount
    internal fun debugFlushPendingStrokeCommits() = flushStrokeCommits()
    private fun flushStrokeCommits() {
        if (strokeCommitLane.hasPending) strokeCommitLane.flushSynchronously()
    }
    private var activeStrokePoints: MutableList<StrokePoint>? = null
    private var activeInputTool: DrawingTool? = null
    private var activeStrokeSettings: BrushSettings? = null
    private var activeStrokeDefersRaster: Boolean = false
    private var activeStrokeLastMotionTimeMillis: Long = 0L
    private var activeStrokeLastRawX: Float = 0f
    private var activeStrokeLastRawY: Float = 0f
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
    private var tutorialTransformDistancePx = 0f
    private var tutorialTransformScaleDelta = 0f
    private var tutorialTransformRotationDegrees = 0f
    private var quickMenuHoldRunnable: Runnable? = null
    private var quickMenuGestureActive = false
    private var quickMenuDownX = 0f
    private var quickMenuDownY = 0f
    private var quickMenuCenterX = 0f
    private var quickMenuCenterY = 0f
    private val quickMenuTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val quickMenuFlickThreshold = 54f * resources.displayMetrics.density
    private val quickMenuSelectionDeadZone = 42f * resources.displayMetrics.density
    private val quickMenuHalfSize = 165f * resources.displayMetrics.density

    var tool: DrawingTool = DrawingTool.BRUSH
    /** Opt-in; disabled by default so regular stylus strokes retain the fastest raster path. */
    var quickShapeEnabled: Boolean = false
    var brushSettings: BrushSettings = BrushSettings()
        set(value) {
            field = value.sanitized()
        }
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
    var onInteraction: ((DrawingInteractionEvent) -> Unit)? = null
    var onRasterFramePresented: (() -> Unit)? = null
    var onQuickMenuRequested: ((Float, Float) -> Unit)? = null
    var onQuickMenuFlickSelected: ((Int) -> Unit)? = null
    var quickMenuTouchHoldEnabled: Boolean = true
    var usePlatformLowLatencyPreview: Boolean = false

    /** Local debug instrumentation for profiling on real tablets. Never persisted or uploaded. */
    var debugPerformanceMetricsEnabled: Boolean
        get() = performanceMetrics.enabled
        set(value) {
            performanceMetrics.enabled = value
        }

    fun debugPerformanceMetrics(): DrawingPerformanceMetrics.Snapshot = performanceMetrics.snapshot()

    fun resetDebugPerformanceMetrics() = performanceMetrics.reset()

    internal fun setRendererMode(mode: RendererMode): Boolean {
        requestedRendererMode = if (BuildConfig.DEBUG) mode else RendererMode.CANVAS_BITMAP
        val available = requestedRendererMode != RendererMode.VULKAN_EXPERIMENTAL ||
            obtainVulkanBackend()?.isAvailable == true
        updateEngineStatus()
        return available
    }

    internal fun rendererMode(): RendererMode = requestedRendererMode

    internal fun availableRendererModes(): List<RendererMode> = if (BuildConfig.DEBUG) {
        RendererMode.entries
    } else {
        listOf(RendererMode.CANVAS_BITMAP)
    }

    internal fun debugVulkanStats(): VulkanRasterStats? = vulkanRasterBackend?.stats()
    internal fun debugRendererFallbackCount(): Long = rendererFallbackCount
    internal fun debugSimulateVulkanFailure(enabled: Boolean) { simulateVulkanFailure = enabled }
    private fun obtainVulkanBackend(): VulkanTileRasterBackend? {
        if (!BuildConfig.DEBUG) return null
        vulkanRasterBackend?.let { return it }
        return VulkanTileRasterBackend().also { vulkanRasterBackend = it }
    }
    internal fun debugDrawStrokeForTest(points: List<StrokePoint>, drawingTool: DrawingTool = DrawingTool.BRUSH) {
        val layer = activeLayer() ?: return
        val command = StrokeCommand(points = points, tool = drawingTool, settings = brushSettings)
        drawCommand(surfaceFor(layer, activeHistoryTarget(layer)), command)
        recordCommands(layer, listOf(command))
    }
    internal fun debugPixelForTest(x: Float, y: Float): Int =
        activeLayer()?.let { surfaceFor(it, activeHistoryTarget(it)).samplePixel(x, y) } ?: 0

    fun supportsPlatformLowLatencyPreview(): Boolean =
        tool == DrawingTool.BRUSH &&
            (shouldDeferActiveRaster(brushSettings) || quickShapeEnabled) &&
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
        val seedWorker = Thread {
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
        }
        demoSeedThread = seedWorker
        seedWorker.start()
    }

    private fun createEmptyDocument(width: Int, height: Int) {
        flushStrokeCommits()
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
        rasterWorkQueue.invalidate()
        prefetchInFlight = false
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
        tileCommandIndex.clear()
        commandRegistry.clear()
        tileCheckpointStore.clear()
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
        registerHistoryEntry(undoStack.last(), undoStack.lastIndex)
    }

    private fun registerHistoryEntry(entry: HistoryEntry, position: Int) {
        val surface = HistorySurfaceKey(entry.layerId, entry.target.toIndexTarget())
        entry.commands.forEach { command ->
            commandRegistry[command.id] = command
            val tiles = TileStorage.keysForBounds(commandBounds(command), documentWidth, documentHeight)
            tileCommandIndex.register(command.id, position, mapOf(surface to tiles), estimatedReplayCost(command))
        }
    }

    /** Used only after destructive lifecycle edits; regional replay never rebuilds/scans this index. */
    private fun rebuildHistoryIndex() {
        tileCommandIndex.clear()
        commandRegistry.clear()
        val ordered = undoStack + redoStack.asReversed()
        ordered.forEachIndexed { position, entry -> registerHistoryEntry(entry, position) }
    }

    private fun HistoryTarget.toIndexTarget(): HistoryRasterTarget = when (this) {
        HistoryTarget.CONTENT -> HistoryRasterTarget.CONTENT
        HistoryTarget.MASK -> HistoryRasterTarget.MASK
    }

    private fun estimatedReplayCost(command: DrawCommand): Int = when (command) {
        is StrokeCommand -> command.points.size
        else -> 1
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

    private fun strokeSegmentBounds(from: StrokePoint, to: StrokePoint, settings: BrushSettings): RectF =
        BrushStrokeGeometry.segmentBounds(from, to, settings)

    private fun maximumBrushExtent(settings: BrushSettings): Float =
        BrushStrokeGeometry.maximumBrushExtent(settings)

    private fun isStampBrush(kind: BrushKind): Boolean = brushRasterSession.isStampBrush(kind)

    private fun shouldDeferActiveRaster(settings: BrushSettings): Boolean =
        requestedRendererMode == RendererMode.VULKAN_EXPERIMENTAL ||
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
        performanceMetrics.updateTileStats(stats)
        val residentTiles = stats.sumOf { it.residentTiles }
        val storedTiles = stats.sumOf { it.storedTiles }
        val dirtyTiles = stats.sumOf { it.dirtyTiles }
        val cacheMegabytes = stats.sumOf { it.cacheBytes } / (1024L * 1024L)
        val detail = when {
            dirtyTiles > 0 -> "$residentTiles residentes · $dirtyTiles pendientes · ${cacheMegabytes} MB"
            storedTiles > 0 -> "$residentTiles residentes / $storedTiles en disco · ${cacheMegabytes} MB"
            else -> "superficie dispersa preparada"
        }
        val renderer = when (requestedRendererMode) {
            RendererMode.VULKAN_EXPERIMENTAL -> if (vulkanRasterBackend?.isAvailable == true) {
                "Vulkan exp."
            } else {
                "Canvas fallback"
            }
            RendererMode.AUTO -> "Auto/Canvas"
            RendererMode.CANVAS_BITMAP -> "Canvas"
        }
        val status = "Tiles ${TileStorage.TILE_SIZE} · $renderer · $detail"
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
            resetView(notifyInteraction = false)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            deleteSelectionContents()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        strokeCommitLane.close()
        strokeCommitWorker.shutdown()
        prefetchGeneration.incrementAndGet()
        rasterWorkQueue.close()
        prefetchExecutor.shutdownNow()
        // Complete the active durable save rather than interrupting an atomic swap.
        saveWorker.close()
        tileCheckpointStore.clear()
        brushRasterSession.close()
        vulkanRasterBackend?.close()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        deferredStrokeOverlay.setBounds(0, 0, w, h)
        if (w > 0 && h > 0 && !fittedOnce) {
            resetView(notifyInteraction = false)
            fittedOnce = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        val frameStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), workspacePaint)
        canvas.save()
        canvas.concat(transform)
        canvas.save()
        canvas.clipRect(documentBounds)
        val visibleBounds = visibleDocumentRect()
        drawCheckerboard(canvas, visibleBounds)
        prefetchedBounds.set(
            (visibleBounds.left - TileStorage.TILE_SIZE).coerceAtLeast(0f),
            (visibleBounds.top - TileStorage.TILE_SIZE).coerceAtLeast(0f),
            (visibleBounds.right + TileStorage.TILE_SIZE).coerceAtMost(documentWidth.toFloat()),
            (visibleBounds.bottom + TileStorage.TILE_SIZE).coerceAtMost(documentHeight.toFloat()),
        )
        layers.forEachIndexed { index, layer ->
            drawVisibleLayer(canvas, visibleBounds, index, layer)
        }
        drawGridOverlay(canvas, visibleBounds)
        drawPerspectiveGuides(canvas)
        drawRulers(canvas)
        drawShapePreview(canvas)
        drawSelectionOverlay(canvas)
        canvas.restore()
        canvas.drawRect(documentBounds, borderPaint)
        canvas.restore()
        drawBrushCursor(canvas)
        val hasMissingTiles = hasMissingVisibleTiles(prefetchedBounds)
        if (hasMissingTiles) performanceMetrics.recordVisibleMiss()
        scheduleTilePrefetch(prefetchedBounds, force = hasMissingTiles)
        scheduleEngineStatusUpdate()
        // AndroidX Ink must retain its finished front-buffer stroke while
        // the detached tile batch is still being prepared or published.
        if (!strokeCommitLane.hasPending) onRasterFramePresented?.invoke()
        if (frameStartedNanos != 0L) {
            performanceMetrics.addViewportFrame(System.nanoTime() - frameStartedNanos)
        }
    }

    private fun hasMissingVisibleTiles(bounds: RectF): Boolean {
        for (layer in layers) {
            if (!isLayerEffectivelyVisible(layer)) continue
            if (layer.surface.hasMissingVisibleTiles(bounds) ||
                (layer.maskEnabled && layer.maskSurface?.hasMissingVisibleTiles(bounds) == true)
            ) return true
        }
        return false
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

        // No need for a document-sized offscreen texture when only the viewport is visible.
        val checkpoint = canvas.saveLayer(visibleBounds, null)
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
        val accepted = rasterWorkQueue.enqueue(
            prepare = {
                val started = if (performanceMetrics.isActive) System.nanoTime() else 0L
                var loadedTiles = 0
                for (surface in snapshot) {
                    if (generation != prefetchGeneration.get()) break
                    loadedTiles += surface.prefetch(requestedBounds)
                }
                val duration = if (started != 0L) System.nanoTime() - started else 0L
                loadedTiles to duration
            },
            commit = { (loadedTiles, elapsedNanos) ->
                prefetchInFlight = false
                if (generation == prefetchGeneration.get()) {
                    performanceMetrics.recordPrefetch(loadedTiles)
                    if (elapsedNanos != 0L) performanceMetrics.addTilePrefetch(elapsedNanos)
                    updateEngineStatus()
                    postInvalidateOnAnimation()
                }
            },
            onError = { error ->
                prefetchInFlight = false
                android.util.Log.w("CanvasStudio", "Tile prefetch failed", error)
            },
        )
        if (!accepted) prefetchInFlight = false
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
        if (width <= 0 || height <= 0) {
            viewportVisibleBounds.set(documentBounds)
            return viewportVisibleBounds
        }
        transform.invert(inverse)
        val points = viewportInverseCoords
        points[0] = 0f
        points[1] = 0f
        points[2] = width.toFloat()
        points[3] = 0f
        points[4] = width.toFloat()
        points[5] = height.toFloat()
        points[6] = 0f
        points[7] = height.toFloat()
        inverse.mapPoints(points)
        viewportVisibleBounds.set(
            min(min(points[0], points[2]), min(points[4], points[6])).coerceIn(0f, documentWidth.toFloat()),
            min(min(points[1], points[3]), min(points[5], points[7])).coerceIn(0f, documentHeight.toFloat()),
            max(max(points[0], points[2]), max(points[4], points[6])).coerceIn(0f, documentWidth.toFloat()),
            max(max(points[1], points[3]), max(points[5], points[7])).coerceIn(0f, documentHeight.toFloat()),
        )
        return viewportVisibleBounds
    }

    private fun drawCheckerboard(canvas: Canvas, visible: RectF) {
        canvas.drawRect(documentBounds, pagePaint)
        if (!visible.isEmpty) canvas.drawRect(visible, checkerboardPaint)
    }

    private fun drawGridOverlay(canvas: Canvas, visible: RectF) {
        if (gridVisible && !visible.isEmpty) {
            val spacing = when {
                currentScale >= 1.4f -> 64f
                currentScale >= 0.65f -> 128f
                else -> 256f
            }
            // Grid lines outside the visible bounds never need to reach the renderer.
            var x = kotlin.math.floor(visible.left / spacing) * spacing
            while (x <= visible.right) {
                canvas.drawLine(x, visible.top, x, visible.bottom, gridPaint)
                x += spacing
            }
            var y = kotlin.math.floor(visible.top / spacing) * spacing
            while (y <= visible.bottom) {
                canvas.drawLine(visible.left, y, visible.right, y, gridPaint)
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
        brushRasterSession.configurePaint(previewPaint, tool, brushSettings, 1f, 0f, isPreview = true)
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
        if (cachedSymmetryCount == radialSymmetryCount &&
            cachedVerticalSymmetry == verticalSymmetry &&
            cachedSymmetryWidth == documentWidth &&
            cachedSymmetryHeight == documentHeight
        ) return cachedSymmetryMatrices
        val centerX = documentWidth / 2f
        val centerY = documentHeight / 2f
        cachedSymmetryMatrices = when {
            radialSymmetryCount > 1 -> List(radialSymmetryCount) { index ->
                Matrix().apply { setRotate(360f * index / radialSymmetryCount, centerX, centerY) }
            }
            verticalSymmetry -> listOf(
                Matrix(),
                Matrix().apply { setScale(-1f, 1f, centerX, centerY) },
            )
            else -> listOf(Matrix())
        }
        cachedSymmetryCount = radialSymmetryCount
        cachedVerticalSymmetry = verticalSymmetry
        cachedSymmetryWidth = documentWidth
        cachedSymmetryHeight = documentHeight
        return cachedSymmetryMatrices
    }

    internal fun debugSymmetryMatricesForTest(): List<Matrix> = symmetryMatrices()

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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            flushStrokeCommits()
        }
        performanceMetrics.recordMotionEvent(event.historySize)
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
        if (handleQuickMenuTouchGesture(event)) return true
        if (handlePerspectiveEdit(event)) return true
        if (handleRulerGuide(event)) return true
        val active = activeLayer()
        if (active?.editingMask == true && tool !in setOf(DrawingTool.BRUSH, DrawingTool.ERASER, DrawingTool.HAND)) {
            finishMaskEditing()
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onEngineMessage?.invoke("Ocultación cerrada. La capa vuelve a recibir las herramientas normales.")
            }
        }
        if (tool == DrawingTool.TRANSFORM && selectionPath != null) {
            hoverVisible = false
            handleSelectionTransformGesture(event)
            return true
        }
        val stylusIndex = StylusInputController.stylusPointerIndex(event)
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
        val currentSample = StylusInputController.currentSample(event, activePointerIndex)
        val current = mapToDocument(currentSample.x, currentSample.y)
        val pressure = currentSample.pressure
        val tilt = currentSample.tilt
        val orientation = currentSample.orientation
        val eventTool = if (StylusInputController.isEraser(event, activePointerIndex)) {
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
                        deferUntilCommit = !stylusPresent || (quickShapeEnabled && eventTool == DrawingTool.BRUSH),
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
                        strokeInputBatch.forEachHistorical(
                            event,
                            activePointerIndex,
                            inverse,
                        ) { x, y, historicalPressure, historicalTilt, historicalOrientation, historicalTime ->
                            appendStrokePoint(
                                points,
                                x,
                                y,
                                historicalPressure,
                                historicalTilt,
                                historicalOrientation,
                                historicalTime,
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
                    DrawingTool.BRUSH, DrawingTool.ERASER -> {
                        if (quickShapeEnabled && activeInputTool == DrawingTool.BRUSH) {
                            trackQuickShapeMotion(current[0], current[1], event.eventTime)
                        }
                        finishStroke(event.eventTime, current[0], current[1])
                    }
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
        activeStrokeLastMotionTimeMillis = 0L
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
        // Preserve history order if the user draws again before the previous
        // experimental background batch has published.
        flushStrokeCommits()
        activeStrokeInputStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
        val settings = brushSettings
        val points = mutableListOf(
            StrokePoint(x, y, pressure, tilt, time, orientation),
        )
        activeStrokePoints = points
        activeInputTool = drawingTool
        activeStrokeSettings = settings
        activeStrokeDefersRaster = deferUntilCommit
        activeStrokeLastRawX = x
        activeStrokeLastRawY = y
        activeStrokeLastMotionTimeMillis = time
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
                brushRasterSession.drawBrushStamp(
                    canvas = canvas,
                    x = transformed.x,
                    y = transformed.y,
                    pressure = pressure * brushRasterSession.taperFactor(settings, 0f),
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

    private fun trackQuickShapeMotion(x: Float, y: Float, timeMillis: Long) {
        // Track the unsmoothed contact, not the rendered StrokeSampler position.
        // Small S Pen jitter is ignored without allocating any gesture objects.
        val deadZone = (3f / currentScale.coerceAtLeast(.1f)).coerceIn(2f, 24f)
        if (hypot(x - activeStrokeLastRawX, y - activeStrokeLastRawY) > deadZone) {
            activeStrokeLastRawX = x
            activeStrokeLastRawY = y
            activeStrokeLastMotionTimeMillis = timeMillis
        }
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
        if (quickShapeEnabled && activeInputTool == DrawingTool.BRUSH) {
            trackQuickShapeMotion(rawX, rawY, time)
        }
        val previous = points.lastOrNull() ?: return
        val settings = activeStrokeSettings ?: brushSettings
        val sampled = StrokeSampler.sample(
            previous = previous,
            rawX = rawX,
            rawY = rawY,
            pressure = pressure,
            tilt = tilt,
            orientation = orientation,
            time = time,
            settings = settings,
            stampBased = isStampBrush(settings.kind),
        )
        if (sampled == null) {
            performanceMetrics.recordSample(accepted = false)
            return
        }
        performanceMetrics.recordSample(accepted = true)
        points += sampled
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
        // A finished asynchronous stroke must stay visible until tile
        // publication; otherwise pen-up would briefly erase its preview.
        queuedStrokePreviews.forEach { preview ->
            strokePreviewRenderer.draw(
                canvas,
                preview.command.points,
                preview.command.settings,
                preview.command.tool,
                listOf(Matrix()),
            )
        }
        val points = activeStrokePoints ?: return
        val settings = activeStrokeSettings ?: return
        if ((!activeStrokeDefersRaster && !shouldDeferActiveRaster(settings)) || points.isEmpty()) return
        strokePreviewRenderer.draw(
            canvas = canvas,
            points = points,
            settings = settings,
            tool = activeInputTool ?: tool,
            symmetryMatrices = symmetryMatrices(),
        )
    }

    private fun scheduleActiveStrokeRender() {
        if (strokeFramePosted) return
        strokeFramePosted = true
        postOnAnimation {
            strokeFramePosted = false
            val before = activeRenderedPointCount
            val pending = ((activeStrokePoints?.size ?: 0) - before).coerceAtLeast(0)
            if (pending > 0) {
                val startedNanos = System.nanoTime()
                renderActiveStrokePending(strokeFrameBudget.nextSegments(pending))
                strokeFrameBudget.record(
                    (activeRenderedPointCount - before).coerceAtLeast(0),
                    System.nanoTime() - startedNanos,
                )
            }
            val points = activeStrokePoints
            if (points != null && activeRenderedPointCount < points.size) {
                scheduleActiveStrokeRender()
            }
        }
    }

    private fun renderActiveStrokePending(maxSegments: Int) {
        val rasterStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
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
        // A single brush extent is sufficient for the whole batch; consecutive
        // segment rectangles union to the same min/max point bounds.
        val extent = maximumBrushExtent(settings)
        val pickupSurface = if (settings.renderProfile.colorPickup > .001f) {
            surfaceFor(layer, activeHistoryTarget(layer))
        } else null

        matrices.forEach { matrix ->
            // Matrix.mapPoints transforms a reusable float buffer in one JNI call.
            // Copies of immutable StrokePoint values remain local to this batch;
            // no float arrays, lists, or RectF are created per segment.
            val transformed = strokeTransformBatch.map(
                points,
                firstEndPointIndex - 1,
                endExclusive,
                matrix,
            )
            if (transformed.size < 2) return@forEach
            val bounds = strokeTransformBatch.bounds(extent)
            val pickupSurfaceForMatrix = pickupSurface
            val pickupColors = if (pickupSurfaceForMatrix != null) {
                IntArray(transformed.size - 1) { localIndex ->
                    pickupSurfaceForMatrix.samplePixel(
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
                    brushRasterSession.drawStrokeSegment(
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
        performanceMetrics.recordDabs((endExclusive - firstEndPointIndex) * matrices.size)
        if (activeStrokeInputStartedNanos != 0L) {
            performanceMetrics.addInputToPreview(System.nanoTime() - activeStrokeInputStartedNanos)
        }
        if (rasterStartedNanos != 0L) {
            performanceMetrics.addTileRaster(System.nanoTime() - rasterStartedNanos)
        }
        invalidate()
    }

    /**
     * Experimental debug-only asynchronous publication. Only a single
     * unmasked, unmirrored brush command without wet color pickup is eligible.
     *
     * The immutable stroke is rendered on detached worker-owned bitmaps.
     * History and document revision change only from the owner-thread
     * transaction which publishes the complete tile batch. All other editor
     * mutations first flush the lane and replay its fallback synchronously.
     */
    private fun enqueueDetachedStrokeIfEligible(
        layer: LayerData,
        commands: List<DrawCommand>,
        completedPoints: List<StrokePoint>,
        completedTool: DrawingTool,
        deferredRaster: Boolean,
    ): Boolean {
        if (!BuildConfig.DEBUG || !debugAsyncStrokeRasterEnabled ||
            !deferredRaster || completedTool != DrawingTool.BRUSH ||
            layer.editingMask || commands.size != 1) return false
        val stroke = commands.single() as? StrokeCommand ?: return false
        if (stroke.clipPoints.isNotEmpty() || stroke.clipFeatherPx > .01f ||
            stroke.settings.renderProfile.colorPickup > .001f ||
            stroke.settings.tipAssetPath != null) return false
        val bounds = commandBounds(stroke)
        if (TileStorage.keysForBounds(bounds, documentWidth, documentHeight).size > 24) return false
        val surface = layer.surface
        val preserveAlpha = layer.alphaLocked
        val generation = documentGeneration
        val preview = QueuedStrokePreview(stroke)
        val length = completedPoints.zipWithNext().sumOf { (first, second) ->
            hypot(second.x - first.x, second.y - first.y).toDouble()
        }.toFloat()
        val event = DrawingInteractionEvent.StrokeCommitted(
            layerId = layer.id,
            lengthPx = length,
            minimumPressure = completedPoints.minOf(StrokePoint::pressure),
            maximumPressure = completedPoints.maxOf(StrokePoint::pressure),
            maximumTiltRadians = completedPoints.maxOf { abs(it.tilt) },
            eraser = false,
            editingMask = false,
            symmetryCopies = 1,
        )
        val inputStartedNanos = activeStrokeInputStartedNanos

        queuedStrokePreviews += preview
        val accepted = strokeCommitLane.enqueue(
            prepare = {
                DetachedStrokeRasterizer().use { renderer ->
                    renderer.prepare(surface, stroke, preserveAlpha)
                }
            },
            finish = { prepared ->
                queuedStrokePreviews.remove(preview)
                deferredStrokeOverlay.invalidateSelf()
                val layerStillActive = documentGeneration == generation &&
                    layers.any { it === layer }
                if (layerStillActive) {
                    if (prepared?.publish() != true) {
                        // A concurrent document edit invalidates detached tiles,
                        // so replay exactly once using the known-good renderer.
                        drawCommand(surface, stroke)
                    }
                    recordCommands(layer, commands)
                    onInteraction?.invoke(event)
                    commitDocumentChange()
                    if (inputStartedNanos != 0L) {
                        performanceMetrics.addInputToCommit(
                            System.nanoTime() - inputStartedNanos,
                        )
                    }
                }
                postInvalidateOnAnimation()
            },
        )
        if (!accepted) {
            queuedStrokePreviews.remove(preview)
            deferredStrokeOverlay.invalidateSelf()
        } else {
            deferredStrokeOverlay.invalidateSelf()
            postInvalidateOnAnimation()
        }
        return accepted
    }

    private fun finishStroke(releaseTimeMillis: Long, releaseX: Float, releaseY: Float) {
        val points = activeStrokePoints ?: return
        val completedTool = activeInputTool ?: tool
        val settings = activeStrokeSettings ?: brushSettings
        val deferredRaster = activeStrokeDefersRaster || shouldDeferActiveRaster(settings)
        if (!deferredRaster) renderActiveStrokePending(Int.MAX_VALUE)
        val completedPoints = points.toList()
        val quickLine = if (quickShapeEnabled && completedTool == DrawingTool.BRUSH && deferredRaster) {
            QuickShapeRecognizer.detectLine(
                points = completedPoints,
                releaseX = releaseX,
                releaseY = releaseY,
                heldDurationMillis = (releaseTimeMillis - activeStrokeLastMotionTimeMillis).coerceAtLeast(0L),
            )
        } else null
        clearActiveStrokeState()
        if (completedPoints.isEmpty()) return
        if (quickLine != null) {
            val shape = ShapeCommand(
                tool = DrawingTool.LINE,
                startX = quickLine.startX,
                startY = quickLine.startY,
                endX = quickLine.endX,
                endY = quickLine.endY,
                settings = settings,
                clipPoints = persistentSelectionPoints(),
                clipInverted = selectionInverted,
                clipFeatherPx = selectionFeatherPx,
            )
            activeLayer()?.let { layer ->
                val commands = symmetryCommands(shape)
                val surface = surfaceFor(layer, activeHistoryTarget(layer))
                commands.forEach { drawCommand(surface, it) }
                recordCommands(layer, commands)
            }
            onInteraction?.invoke(
                DrawingInteractionEvent.ShapeCommitted(
                    DrawingTool.LINE,
                    abs(quickLine.endX - quickLine.startX),
                    abs(quickLine.endY - quickLine.startY),
                ),
            )
            commitDocumentChange()
            if (activeStrokeInputStartedNanos != 0L) {
                performanceMetrics.addInputToCommit(System.nanoTime() - activeStrokeInputStartedNanos)
            }
            return
        }
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
            if (enqueueDetachedStrokeIfEligible(
                    layer, commands, completedPoints, completedTool, deferredRaster,
                )
            ) return
            recordCommands(layer, commands)
            if (deferredRaster) {
                val surface = surfaceFor(layer, activeHistoryTarget(layer))
                commands.forEach { drawCommand(surface, it) }
            } else if (requiresFinalStrokeRebuild(command.settings)) {
                rebuildLayerRegion(layer, combinedBounds(commands))
            }
            val length = completedPoints.zipWithNext().sumOf { (first, second) ->
                hypot(second.x - first.x, second.y - first.y).toDouble()
            }.toFloat()
            onInteraction?.invoke(
                DrawingInteractionEvent.StrokeCommitted(
                    layerId = layer.id,
                    lengthPx = length,
                    minimumPressure = completedPoints.minOf(StrokePoint::pressure),
                    maximumPressure = completedPoints.maxOf(StrokePoint::pressure),
                    maximumTiltRadians = completedPoints.maxOf { abs(it.tilt) },
                    eraser = completedTool == DrawingTool.ERASER,
                    editingMask = layer.editingMask,
                    symmetryCopies = commands.size,
                ),
            )
        }
        commitDocumentChange()
        if (activeStrokeInputStartedNanos != 0L) {
            performanceMetrics.addInputToCommit(System.nanoTime() - activeStrokeInputStartedNanos)
        }
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
        onInteraction?.invoke(
            DrawingInteractionEvent.ShapeCommitted(tool, abs(end.x - start.x), abs(end.y - start.y)),
        )
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
        onInteraction?.invoke(DrawingInteractionEvent.GradientCommitted(hypot(end.x - start.x, end.y - start.y)))
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
        onInteraction?.invoke(DrawingInteractionEvent.SelectionCreated(bounds.width() * bounds.height()))
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
        onInteraction?.invoke(DrawingInteractionEvent.SelectionCreated(bounds.width() * bounds.height()))
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
        val bounds = RectF().also { selectionPath?.computeBounds(it, true) }
        onInteraction?.invoke(DrawingInteractionEvent.SelectionCreated(bounds.width() * bounds.height()))
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    tutorialTransformDistancePx = 0f
                    tutorialTransformScaleDelta = 0f
                    tutorialTransformRotationDegrees = 0f
                }
                resetBaseline()
            }
            MotionEvent.ACTION_MOVE -> {
                val values = gestureValues()
                if (!transformGestureInitialized) {
                    resetBaseline()
                    return
                }
                val cx = values[0]
                val cy = values[1]
                val translated = hypot(cx - transformLastCentroidX, cy - transformLastCentroidY)
                var scaleDelta = 0f
                var rotationDelta = 0f
                selectionTransform.postTranslate(cx - transformLastCentroidX, cy - transformLastCentroidY)
                if (event.pointerCount >= 2 && transformLastSpan > 0.5f && values[2] > 0.5f) {
                    val factor = (values[2] / transformLastSpan).coerceIn(0.65f, 1.55f)
                    scaleDelta = factor - 1f
                    selectionTransform.postScale(factor, factor, cx, cy)
                    val rotation = normalizeDegrees(values[3] - transformLastAngle).coerceIn(-28f, 28f)
                    rotationDelta = rotation
                    selectionTransform.postRotate(rotation, cx, cy)
                }
                tutorialTransformDistancePx += translated
                tutorialTransformScaleDelta += abs(scaleDelta)
                tutorialTransformRotationDegrees += abs(rotationDelta)
                onInteraction?.invoke(
                    DrawingInteractionEvent.TransformPreviewChanged(
                        tutorialTransformDistancePx,
                        tutorialTransformScaleDelta,
                        tutorialTransformRotationDegrees,
                    ),
                )
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
        onInteraction?.invoke(DrawingInteractionEvent.TransformCommitted)
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
                    onInteraction?.invoke(DrawingInteractionEvent.FillCommitted(filled.cardinality()))
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
        if (command is StrokeCommand && tryRasterizeVulkan(surface, layer, target, command, bounds)) {
            return
        }
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

    private fun tryRasterizeVulkan(
        surface: SparseTileSurface,
        layer: LayerData?,
        target: HistoryTarget,
        command: StrokeCommand,
        bounds: RectF,
    ): Boolean {
        if (requestedRendererMode != RendererMode.VULKAN_EXPERIMENTAL) return false
        // The experimental backend does not yet implement mask grayscale semantics or
        // feathered selection coverage. Keep these operations pixel-compatible by
        // routing the complete command through the production Canvas backend.
        if (target == HistoryTarget.MASK || command.clipFeatherPx > 0.01f) {
            rendererFallbackCount += 1L
            performanceMetrics.recordRendererFallback()
            return false
        }
        val material = when (command.settings.presetId) {
            "technical-ink" -> VulkanBrushMaterial.TECHNICAL_INK
            "graphite-shader" -> VulkanBrushMaterial.TILTED_GRAPHITE
            else -> {
                rendererFallbackCount += 1L
                performanceMetrics.recordRendererFallback()
                return false
            }
        }
        val backend = obtainVulkanBackend()
        if (backend == null || !backend.isAvailable || simulateVulkanFailure) {
            rendererFallbackCount += 1L
            performanceMetrics.recordRendererFallback()
            return false
        }
        val dabs = BrushDabBatchBuilder.build(command.points, command.settings, command.tool)
        performanceMetrics.recordDabs(dabs.size)
        val selection = FloatArray(command.clipPoints.size * 2)
        command.clipPoints.forEachIndexed { index, point ->
            selection[index * 2] = point.x
            selection[index * 2 + 1] = point.y
        }
        val started = if (performanceMetrics.isActive) System.nanoTime() else 0L
        val result = backend.rasterizeDabs(
            RasterDabRequest(
                surface = surface,
                bounds = bounds,
                dabs = dabs,
                material = material,
                erase = command.tool == DrawingTool.ERASER,
                preserveAlpha = target == HistoryTarget.CONTENT && layer?.alphaLocked == true,
                selection = selection,
                selectionInverted = command.clipInverted,
                grainDepth = command.settings.grainProfile.depth,
            ),
        )
        if (result == null) {
            rendererFallbackCount += 1L
            performanceMetrics.recordRendererFallback()
            return false
        }
        if (started != 0L) performanceMetrics.addTileRaster(System.nanoTime() - started)
        performanceMetrics.recordTilesTouched(result.touchedTiles)
        return true
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
        val result = bitmapRasterBackend.rasterize(
            surface = surface,
            bounds = bounds,
            preserveAlpha = target == HistoryTarget.CONTENT && layer.alphaLocked,
            draw = clippedBlock,
        )
        performanceMetrics.recordTilesTouched(result.touchedTiles)
    }

    private fun drawCommand(
        canvas: Canvas,
        command: DrawCommand,
        pickupColors: IntArray? = null,
        renderClipBounds: RectF? = null,
    ) {
        when (command) {
            is StrokeCommand -> strokeCommandPainter.draw(
                canvas = canvas,
                command = command,
                documentBounds = documentBounds,
                pickupColors = pickupColors,
                renderClipBounds = renderClipBounds,
            )
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
                brushRasterSession.configurePaint(brushRasterSession.strokePaint, command.tool, command.settings, 1f, 0f)
                val start = StrokePoint(command.startX, command.startY, 1f, 0f, 0L)
                val end = StrokePoint(command.endX, command.endY, 1f, 0f, 0L)
                drawShapeWithPaint(canvas, command.tool, start, end, brushRasterSession.strokePaint)
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
                val startsNavigation = !navigationActive
                navigationActive = true
                if (startsNavigation) {
                    navigationDistancePx = 0f
                    navigationScaleFactor = 1f
                }
                resetBaseline()
            }

            MotionEvent.ACTION_MOVE -> {
                navigationActive = true
                if (!navigationInitialized) {
                    resetBaseline()
                    return
                }

                val panDelta = hypot(
                    centroidX - lastGestureCentroidX,
                    centroidY - lastGestureCentroidY,
                )
                navigationDistancePx += panDelta
                transform.postTranslate(centroidX - lastGestureCentroidX, centroidY - lastGestureCentroidY)

                if (hasPair && lastGestureSpan > 0.001f && span > 0.001f) {
                    val requestedFactor = span / lastGestureSpan
                    navigationScaleFactor *= requestedFactor
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

                onInteraction?.invoke(
                    DrawingInteractionEvent.ViewChanged(
                        scale = navigationScaleFactor,
                        panDistancePx = navigationDistancePx,
                        rotationDegrees = currentRotationDegrees,
                    ),
                )

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
        flushStrokeCommits()
        val entry = undoStack.removeLastOrNull() ?: return
        val layer = layers.firstOrNull { it.id == entry.layerId } ?: return
        val commandIds = entry.commands.mapTo(mutableSetOf()) { it.id }
        commandsFor(layer, entry.target).removeAll { it.id in commandIds }
        redoStack += entry
        val affectedBounds = combinedBounds(entry.commands)
        rebuildLayerRegion(layer, affectedBounds, entry.target)
        markLayerDirty(layer, affectedBounds, entry.target)
        commitDocumentChange()
        onInteraction?.invoke(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.UNDO))
    }

    fun redo() {
        flushStrokeCommits()
        val entry = redoStack.removeLastOrNull() ?: return
        val layer = layers.firstOrNull { it.id == entry.layerId } ?: return
        commandsFor(layer, entry.target) += entry.commands
        undoStack += entry
        val affectedBounds = combinedBounds(entry.commands)
        rebuildLayerRegion(layer, affectedBounds, entry.target)
        markLayerDirty(layer, affectedBounds, entry.target)
        commitDocumentChange()
        onInteraction?.invoke(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.REDO))
    }

    fun refreshLayerState() = notifyLayers()

    fun addLayer() {
        flushStrokeCommits()
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
        flushStrokeCommits()
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
        flushStrokeCommits()
        val selected = selectedLayerIds.ifEmpty { setOf(activeLayerId) }
        val removable = layers.filter { it.id in selected }.take((layers.size - 1).coerceAtLeast(0))
        if (removable.isEmpty()) return
        val firstIndex = removable.minOf { layers.indexOf(it) }
        val removedIds = removable.mapTo(mutableSetOf()) { it.id }
        layers.removeAll { layer -> layer.id in removedIds }
        removable.forEach { removed ->
            (removed.commands + removed.maskCommands).forEach { commandRegistry.remove(it.id) }
            (removed.commands + removed.maskCommands).distinctBy { it.id }.forEach(::recycleCommand)
            removed.surface.recycle()
            removed.maskSurface?.recycle()
            removed.baseTileDirectory.parentFile?.deleteRecursively()
            removed.maskBaseTileDirectory?.parentFile?.deleteRecursively()
        }
        undoStack.removeAll { it.layerId in removedIds }
        redoStack.removeAll { it.layerId in removedIds }
        removedIds.forEach { id ->
            tileCommandIndex.removeSurface(HistorySurfaceKey(id, HistoryRasterTarget.CONTENT))
            tileCommandIndex.removeSurface(HistorySurfaceKey(id, HistoryRasterTarget.MASK))
            tileCheckpointStore.removeSurface(HistorySurfaceKey(id, HistoryRasterTarget.CONTENT))
            tileCheckpointStore.removeSurface(HistorySurfaceKey(id, HistoryRasterTarget.MASK))
        }
        rebuildHistoryIndex()
        tileCheckpointStore.clear()
        pruneEmptyGroups()
        selectOnly(layers[min(firstIndex, layers.lastIndex)].id)
        updateCacheBudgets()
        notifyLayers()
        commitDocumentChange()
    }

    fun setActiveLayer(id: String) {
        flushStrokeCommits()
        if (layers.any { it.id == id }) {
            layers.filter { it.id != id }.forEach { it.editingMask = false }
            selectOnly(id)
            notifyLayers()
        }
    }

    fun toggleLayerSelection(id: String) {
        flushStrokeCommits()
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
        flushStrokeCommits()
        layers.firstOrNull { it.id == id }?.let {
            it.visible = !it.visible
            notifyLayers()
            commitDocumentChange()
        }
    }

    fun setLayerOpacity(id: String, opacity: Float) {
        flushStrokeCommits()
        layers.firstOrNull { it.id == id }?.let {
            it.opacity = opacity.coerceIn(0f, 1f)
            notifyLayers()
            invalidate()
            onDocumentChanged?.invoke()
        }
    }

    fun setLayerBlendMode(id: String, blendMode: LayerBlendMode) {
        flushStrokeCommits()
        layers.firstOrNull { it.id == id }?.let {
            it.blendMode = blendMode
            notifyLayers()
            commitDocumentChange()
        }
    }

    fun setLayerAlphaLocked(id: String, locked: Boolean) {
        flushStrokeCommits()
        layers.firstOrNull { it.id == id }?.let { layer ->
            layer.alphaLocked = locked
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun setLayerClipping(id: String, clipping: Boolean) {
        flushStrokeCommits()
        layers.firstOrNull { it.id == id }?.let { layer ->
            layer.clipping = clipping
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun createGroupFromActiveLayer() {
        flushStrokeCommits()
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
        flushStrokeCommits()
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
        flushStrokeCommits()
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.visible = !group.visible
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun toggleGroupCollapsed(id: String) {
        flushStrokeCommits()
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.collapsed = !group.collapsed
            notifyLayers()
        }
    }

    fun setGroupOpacity(id: String, opacity: Float) {
        flushStrokeCommits()
        layerGroups.firstOrNull { it.id == id }?.let { group ->
            group.opacity = opacity.coerceIn(0f, 1f)
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun addMaskToActiveLayer() {
        flushStrokeCommits()
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
        onEngineMessage?.invoke("Ocultación lista: Pincel oculta y Borrador recupera; la capa original no cambia.")
        onDocumentChanged?.invoke()
    }

    fun setEditingLayerMask(id: String, editing: Boolean) {
        flushStrokeCommits()
        layers.forEach { it.editingMask = false }
        layers.firstOrNull { it.id == id && it.maskSurface != null }?.editingMask = editing
        notifyLayers()
    }

    fun finishMaskEditing(): Boolean {
        flushStrokeCommits()
        val changed = layers.any { it.editingMask }
        if (!changed) return false
        layers.forEach { it.editingMask = false }
        notifyLayers()
        return true
    }

    /**
     * A still finger opens the radial menu at the contact point. Stylus input and
     * multi-touch navigation never enter this path. Moving before the hold timeout
     * simply continues the normal deferred finger stroke.
     */
    private fun handleQuickMenuTouchGesture(event: MotionEvent): Boolean {
        val callback = onQuickMenuRequested
        val fingerOnly = event.pointerCount == 1 &&
            event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        val eligibleTool = tool in setOf(DrawingTool.BRUSH, DrawingTool.ERASER, DrawingTool.HAND)
        if (!quickMenuTouchHoldEnabled || callback == null || !fingerOnly || !eligibleTool) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                event.actionMasked == MotionEvent.ACTION_UP
            ) cancelQuickMenuTouchGesture()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelQuickMenuTouchGesture()
                quickMenuDownX = event.x
                quickMenuDownY = event.y
                val hold = Runnable {
                    quickMenuHoldRunnable = null
                    quickMenuGestureActive = true
                    quickMenuCenterX = if (width >= quickMenuHalfSize * 2f) {
                        quickMenuDownX.coerceIn(quickMenuHalfSize, width - quickMenuHalfSize)
                    } else width / 2f
                    quickMenuCenterY = if (height >= quickMenuHalfSize * 2f) {
                        quickMenuDownY.coerceIn(quickMenuHalfSize, height - quickMenuHalfSize)
                    } else height / 2f
                    clearActiveStrokeState()
                    shapeStart = null
                    shapeEnd = null
                    parent?.requestDisallowInterceptTouchEvent(true)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    callback(quickMenuCenterX, quickMenuCenterY)
                }
                quickMenuHoldRunnable = hold
                postDelayed(hold, QUICK_MENU_HOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.x - quickMenuDownX, event.y - quickMenuDownY)
                if (!quickMenuGestureActive && distance > quickMenuTouchSlop) {
                    quickMenuHoldRunnable?.let(::removeCallbacks)
                    quickMenuHoldRunnable = null
                }
                if (quickMenuGestureActive) return true
            }
            MotionEvent.ACTION_UP -> {
                quickMenuHoldRunnable?.let(::removeCallbacks)
                quickMenuHoldRunnable = null
                if (quickMenuGestureActive) {
                    val movedFromContact = hypot(event.x - quickMenuDownX, event.y - quickMenuDownY)
                    if (movedFromContact >= quickMenuFlickThreshold) {
                        radialQuickMenuSlot(
                            event.x - quickMenuCenterX,
                            event.y - quickMenuCenterY,
                            threshold = quickMenuSelectionDeadZone,
                        )?.let { onQuickMenuFlickSelected?.invoke(it) }
                    }
                    quickMenuGestureActive = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelQuickMenuTouchGesture()
        }
        return false
    }

    private fun cancelQuickMenuTouchGesture() {
        quickMenuHoldRunnable?.let(::removeCallbacks)
        quickMenuHoldRunnable = null
        quickMenuGestureActive = false
    }

    fun toggleLayerMaskEnabled(id: String) {
        flushStrokeCommits()
        layers.firstOrNull { it.id == id && it.maskSurface != null }?.let { layer ->
            layer.maskEnabled = !layer.maskEnabled
            notifyLayers()
            onDocumentChanged?.invoke()
        }
    }

    fun deleteActiveLayerMask() {
        flushStrokeCommits()
        val layer = activeLayer() ?: return
        val mask = layer.maskSurface ?: return
        layer.maskCommands.distinctBy { it.id }.forEach { command ->
            commandRegistry.remove(command.id)
            recycleCommand(command)
        }
        layer.maskCommands.clear()
        mask.recycle()
        layer.maskSurface = null
        layer.maskBaseTileDirectory?.parentFile?.deleteRecursively()
        layer.maskBaseTileDirectory = null
        layer.editingMask = false
        layer.maskEnabled = true
        undoStack.removeAll { it.layerId == layer.id && it.target == HistoryTarget.MASK }
        redoStack.removeAll { it.layerId == layer.id && it.target == HistoryTarget.MASK }
        tileCommandIndex.removeSurface(HistorySurfaceKey(layer.id, HistoryRasterTarget.MASK))
        tileCheckpointStore.removeSurface(HistorySurfaceKey(layer.id, HistoryRasterTarget.MASK))
        rebuildHistoryIndex()
        tileCheckpointStore.clear()
        updateCacheBudgets()
        notifyLayers()
        onDocumentChanged?.invoke()
    }

    fun moveActiveLayer(up: Boolean) {
        flushStrokeCommits()
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
        flushStrokeCommits()
        val layer = activeLayer() ?: return
        val target = activeHistoryTarget(layer)
        commandsFor(layer, target).distinctBy { it.id }.forEach { command ->
            commandRegistry.remove(command.id)
            recycleCommand(command)
        }
        commandsFor(layer, target).clear()
        val base = baseDirectoryFor(layer, target)
        base.deleteRecursively()
        base.mkdirs()
        surfaceFor(layer, target).clearAll()
        undoStack.removeAll { it.layerId == layer.id && it.target == target }
        redoStack.removeAll { it.layerId == layer.id && it.target == target }
        val surfaceKey = HistorySurfaceKey(layer.id, target.toIndexTarget())
        tileCommandIndex.removeSurface(surfaceKey)
        tileCheckpointStore.removeSurface(surfaceKey)
        rebuildHistoryIndex()
        tileCheckpointStore.clear()
        updateEngineStatus()
        commitDocumentChange()
    }

    fun renameActiveLayer(name: String) {
        flushStrokeCommits()
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

    fun resetView(notifyInteraction: Boolean = true) {
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
        if (notifyInteraction) onInteraction?.invoke(DrawingInteractionEvent.ViewReset)
        invalidate()
    }

    fun rotateBy(degrees: Float) {
        if (width <= 0 || height <= 0) return
        val focusX = width / 2f
        val focusY = height / 2f
        transform.postRotate(degrees, focusX, focusY)
        currentRotationDegrees = normalizeDegrees(currentRotationDegrees + degrees)
        onRotationChanged?.invoke(rotationDegrees())
        onInteraction?.invoke(DrawingInteractionEvent.ViewChanged(currentScale, 0f, currentRotationDegrees))
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
        onInteraction?.invoke(DrawingInteractionEvent.ViewChanged(currentScale, 0f, currentRotationDegrees))
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
        flushStrokeCommits()
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
        flushStrokeCommits()
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
        if (destination.parentFile?.let { !it.isDirectory && !it.mkdirs() } == true) return false
        val atomic = AtomicFile(destination)
        var output: FileOutputStream? = null
        return try {
            val stream = atomic.startWrite()
            output = stream
            FileInputStream(source).use { input -> input.copyTo(stream) }
            atomic.finishWrite(stream)
            File(destination.parentFile, "${destination.name}.tmp").delete()
            true
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
            false
        }
    }

    fun saveProject(
        projectId: String,
        title: String,
        dpi: Int,
        includePreview: Boolean = true,
    ) {
        flushStrokeCommits()
        val saveStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
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
            val effectiveVisible: Boolean,
            val effectiveOpacity: Float,
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
                effectiveVisible = isLayerEffectivelyVisible(layer),
                effectiveOpacity = effectiveLayerOpacity(layer),
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
        val savedWidth = documentWidth
        val savedHeight = documentHeight
        val documentAtSave = documentGeneration
        val revisionAtSave = committedRevision
        val seedToAwait = demoSeedThread
        val generation = ++saveGeneration

        val enqueued = saveWorker.submitLatest {
            synchronized(saveLock) {
                // Never block UI input: join happens only on this save worker.
                // Otherwise a demo can rebuild its working tile folder after
                // saveSnapshot but before the transactional copy begins.
                if (seedToAwait != null && seedToAwait !== Thread.currentThread()) {
                    try {
                        seedToAwait.join()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@synchronized
                    }
                }
                if (generation != saveGeneration || documentAtSave != documentGeneration ||
                    revisionAtSave != committedRevision) {
                    return@synchronized
                }

                // Compose the thumbnail from immutable layer metadata on the save
                // worker, never on the stylus/UI thread. Only the sparse surfaces
                // are referenced and each protects its own tile cache.
                val previewBitmap = if (includePreview) {
                    runCatching {
                        PreviewCompositor().render(
                            savedWidth,
                            savedHeight,
                            layerReferences.map { layer ->
                                PreviewCompositor.Layer(
                                    surface = layer.surface,
                                    maskSurface = layer.maskSurface,
                                    visible = layer.effectiveVisible,
                                    opacity = layer.effectiveOpacity,
                                    blendMode = layer.blendMode,
                                    clipping = layer.clipping,
                                    maskEnabled = layer.maskEnabled,
                                )
                            },
                        )
                    }.getOrElse { error ->
                        mainHandler.post {
                            onEngineMessage?.invoke("No se pudo generar la miniatura del proyecto.")
                            onProjectSaved?.invoke(false)
                        }
                        return@synchronized
                    }
                } else null

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
                    mainHandler.post { onProjectSaved?.invoke(false) }
                    return@synchronized
                }

                if (generation != saveGeneration || documentAtSave != documentGeneration ||
                    revisionAtSave != committedRevision) {
                    previewBitmap?.recycle()
                    return@synchronized
                }

                var transaction: ProjectSaveTransaction? = null
                try {
                    transaction = ProjectSaveTransaction.begin(context, projectId)
                    val directory = transaction.workingDirectory
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
                        val previewAtomic = AtomicFile(File(directory, "preview.png"))
                        var output: FileOutputStream? = null
                        try {
                            output = previewAtomic.startWrite()
                            check(previewBitmap.compress(Bitmap.CompressFormat.PNG, 92, output))
                            previewAtomic.finishWrite(output)
                            File(directory, "preview.png.tmp").delete()
                        } catch (error: Throwable) {
                            output?.let(previewAtomic::failWrite)
                            throw error
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
                    val metadataAtomic = AtomicFile(File(directory, "project.properties"))
                    var metadataOutput: FileOutputStream? = null
                    try {
                        metadataOutput = metadataAtomic.startWrite()
                        properties.store(metadataOutput, "Canvas Studio sparse tiled project")
                        metadataAtomic.finishWrite(metadataOutput)
                        File(directory, "project.properties.tmp").delete()
                    } catch (error: Throwable) {
                        metadataOutput?.let(metadataAtomic::failWrite)
                        throw error
                    }

                    directory.listFiles { file ->
                        file.name.startsWith("layer-") && (file.extension == "png" || file.extension == "tmp")
                    }.orEmpty().forEach(File::delete)
                    File(directory, "flattened.png").delete()
                    File(directory, "flattened.tmp").delete()
                    // Do not publish a mixed-generation revision when the user draws,
                    // undoes, or changes project during thumbnail/PNG generation.
                    // Finally will abort the staged directory without touching the
                    // previous valid project revision.
                    if (generation != saveGeneration || documentAtSave != documentGeneration ||
                        revisionAtSave != committedRevision) return@synchronized
                    transaction.commit()
                    ProjectVersionStore.maybeSnapshot(
                        context,
                        projectId,
                        ProjectRepository.projectDirectory(context, projectId),
                    )

                    mainHandler.post {
                        layerSnapshots.forEach { snapshot ->
                            val currentLayer = layers.firstOrNull { it.id == snapshot.reference.id }
                                ?: return@forEach
                            currentLayer.surface.acknowledgeSave(snapshot.tileSnapshot)
                            val maskSnapshot = snapshot.maskSnapshot
                            if (maskSnapshot != null) currentLayer.maskSurface?.acknowledgeSave(maskSnapshot)
                        }
                        updateEngineStatus()
                        if (saveStartedNanos != 0L) {
                            performanceMetrics.addSave(System.nanoTime() - saveStartedNanos)
                        }
                        onProjectSaved?.invoke(true)
                    }
                } catch (error: Throwable) {
                    mainHandler.post {
                        if (saveStartedNanos != 0L) {
                            performanceMetrics.addSave(System.nanoTime() - saveStartedNanos)
                        }
                        onProjectSaved?.invoke(false)
                        onEngineMessage?.invoke(
                            error.message?.let { "No se pudo guardar el proyecto: $it" }
                                ?: "No se pudo guardar el proyecto.",
                        )
                    }
                } finally {
                    transaction?.abort()
                    previewBitmap?.recycle()
                }
            }
        }
        if (!enqueued) {
            onProjectSaved?.invoke(false)
        }
    }

    fun loadProject(projectId: String): Boolean {
        flushStrokeCommits()
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
            tileCommandIndex.clear()
            commandRegistry.clear()
            tileCheckpointStore.clear()
            updateCacheBudgets()
            notifyLayers()
            updateEngineStatus()
            fittedOnce = false
            if (width > 0 && height > 0) {
                resetView(notifyInteraction = false)
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
        val replayStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
        if (bounds.isEmpty) return
        val safeBounds = RectF(bounds).apply { intersect(documentBounds) }
        if (safeBounds.isEmpty) return
        val tileBounds = TileStorage.tileAlignedBounds(safeBounds, documentWidth, documentHeight)
        if (tileBounds.isEmpty) return
        val surface = surfaceFor(layer, target)
        var replayedCommands = 0
        val keys = TileStorage.keysForBounds(tileBounds, documentWidth, documentHeight)
        val surfaceKey = HistorySurfaceKey(layer.id, target.toIndexTarget())
        val targetPosition = undoStack.lastIndex
        keys.forEach { key ->
            val tileReplayStarted = if (performanceMetrics.isActive) System.nanoTime() else 0L
            val keyBounds = RectF(TileStorage.tileRect(key, documentWidth, documentHeight))
            val restoreStarted = if (performanceMetrics.isActive) System.nanoTime() else 0L
            val checkpoint = tileCheckpointStore.findNearest(surfaceKey, key, targetPosition)
            if (checkpoint != null) {
                surface.restoreTile(key, checkpoint.bitmap)
                checkpoint.bitmap?.recycle()
            } else {
                surface.resetRegionFrom(baseDirectoryFor(layer, target), keyBounds)
            }
            val fromPosition = checkpoint?.key?.historyPosition ?: -1
            val indexedIds = tileCommandIndex.commandIdsFor(
                surfaceKey, setOf(key), fromExclusive = fromPosition, toInclusive = targetPosition,
            )
            performanceMetrics.recordIndexQuery(indexedIds.size, tileCommandIndex.entryCount())
            performanceMetrics.recordCheckpoint(
                checkpoint != null,
                indexedIds.size,
                if (restoreStarted == 0L) 0L else System.nanoTime() - restoreStarted,
            )
            indexedIds.forEach { id ->
                val command = commandRegistry[id] ?: run {
                    performanceMetrics.recordIndexFallback()
                    commandsFor(layer, target).firstOrNull { it.id == id }
                        ?.also { recovered -> commandRegistry[id] = recovered }
                }
                if (command == null) {
                    return@forEach
                }
                replayedCommands++
                drawCommand(surface, command, replayClipBounds = keyBounds)
            }
            val localReplayNanos = if (tileReplayStarted == 0L) 0L else System.nanoTime() - tileReplayStarted
            if (tileCheckpointPolicy.shouldCreate(
                    indexedIds.size,
                    tileCommandIndex.replayCostFor(indexedIds),
                    localReplayNanos,
                ) && targetPosition >= 0
            ) {
                val createStarted = if (performanceMetrics.isActive) System.nanoTime() else 0L
                val snapshot = surface.snapshotTile(key)
                tileCheckpointStore.put(TileCheckpointKey(surfaceKey, key, targetPosition), snapshot)
                snapshot?.recycle()
                performanceMetrics.recordCheckpointCreate(
                    if (createStarted == 0L) 0L else System.nanoTime() - createStarted,
                )
            }
        }
        tileCheckpointStore.stats().also {
            performanceMetrics.updateCheckpointStats(it.bytes, it.budgetBytes, it.evictions)
        }
        performanceMetrics.recordReplay(
            commandCount = replayedCommands,
            tileCount = keys.size,
        )
        if (replayStartedNanos != 0L) {
            performanceMetrics.addHistoryReplay(System.nanoTime() - replayStartedNanos)
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
        committedRevision++
        notifyLayers()
        onDocumentChanged?.invoke()
        invalidate()
    }

    private fun clearRedoHistory() {
        val staleCommands = redoStack.flatMap { it.commands }.distinctBy { it.id }
        tileCommandIndex.removeCommands(staleCommands.mapTo(mutableSetOf()) { it.id })
        staleCommands.forEach { commandRegistry.remove(it.id) }
        tileCheckpointStore.invalidateAfter(undoStack.lastIndex)
        staleCommands.forEach(::recycleCommand)
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
        const val QUICK_MENU_HOLD_MS = 420L
    }
}
