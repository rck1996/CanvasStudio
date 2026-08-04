package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.os.Trace
import com.orbyte.canvasstudio.BuildConfig
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import com.orbyte.canvasstudio.drawing.TileStorage
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/**
 * Explicit seam between brush evaluation and the production Canvas/Bitmap tile renderer.
 * A future GPU backend must preserve these local-coordinate and alpha-lock semantics.
 */
internal interface TileRasterBackend {
    fun rasterize(
        surface: SparseTileSurface,
        bounds: RectF,
        preserveAlpha: Boolean,
        draw: (Canvas) -> Unit,
    ): TileRasterResult

    fun rasterizeDabs(request: RasterDabRequest): TileRasterResult? = null
}

internal data class TileRasterResult(
    val changed: Boolean,
    val touchedTiles: Int,
)

internal enum class RendererMode(val label: String) {
    AUTO("Auto"),
    CANVAS_BITMAP("Canvas/Bitmap"),
    VULKAN_EXPERIMENTAL("Vulkan experimental"),
}

internal enum class VulkanBrushMaterial { TECHNICAL_INK, TILTED_GRAPHITE }

internal data class RasterDabRequest(
    val surface: SparseTileSurface,
    val bounds: RectF,
    val dabs: List<BrushDab>,
    val material: VulkanBrushMaterial,
    val erase: Boolean,
    val preserveAlpha: Boolean,
    val selection: FloatArray = FloatArray(0),
    val selectionInverted: Boolean = false,
    val grainDepth: Float = 0f,
)

internal data class VulkanRasterStats(
    val initialized: Boolean,
    val deviceName: String,
    val batches: Long,
    val failures: Long,
    val uploadNanos: Long,
    val rasterCpuNanos: Long,
    val submitNanos: Long,
    val waitNanos: Long,
    val readbackNanos: Long,
    val gpuNanos: Long,
    val allocatedBytes: Long,
)

/** Wraps the proven sparse Canvas backend without changing image output. */
internal class BitmapCanvasTileRasterBackend : TileRasterBackend {
    override fun rasterize(
        surface: SparseTileSurface,
        bounds: RectF,
        preserveAlpha: Boolean,
        draw: (Canvas) -> Unit,
    ): TileRasterResult {
        val changed = if (preserveAlpha) {
            surface.drawPreservingAlpha(bounds, draw)
        } else {
            surface.draw(bounds, draw)
        }
        return TileRasterResult(
            changed = changed,
            touchedTiles = if (changed) TileStorage.keysForBounds(bounds, surface.width, surface.height).size else 0,
        )
    }

    override fun rasterizeDabs(request: RasterDabRequest): TileRasterResult {
        val selectionPath = request.selection.takeIf { it.size >= 6 }?.let { points ->
            Path().apply {
                moveTo(points[0], points[1])
                var index = 2
                while (index + 1 < points.size) {
                    lineTo(points[index], points[index + 1])
                    index += 2
                }
                close()
            }
        }
        val operation: (Canvas) -> Unit = { canvas ->
            val save = if (selectionPath != null) {
                canvas.save().also {
                    if (request.selectionInverted) canvas.clipOutPath(selectionPath) else canvas.clipPath(selectionPath)
                }
            } else null
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                if (request.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            request.dabs.forEach { dab ->
                paint.color = dab.color
                paint.alpha = (dab.opacity * dab.flow * 255f).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.rotate(Math.toDegrees(dab.rotationRadians.toDouble()).toFloat(), dab.x, dab.y)
                canvas.drawOval(
                    RectF(dab.x - dab.radiusX, dab.y - dab.radiusY, dab.x + dab.radiusX, dab.y + dab.radiusY),
                    paint,
                )
                canvas.restore()
            }
            paint.xfermode = null
            if (save != null) canvas.restoreToCount(save)
        }
        return rasterize(request.surface, request.bounds, request.preserveAlpha, operation)
    }
}

/**
 * Debug-only Vulkan 1.1 compute rasterizer. Canvas remains authoritative whenever initialization,
 * brush support or a batch fails; no renderer choice is serialized into the document.
 */
internal class VulkanTileRasterBackend : TileRasterBackend, Closeable {
    private val handle: Long = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 26) {
        runCatching { VulkanNativeBridge.create() }.getOrDefault(0L)
    } else {
        0L
    }
    private val batches = AtomicLong()
    private val failures = AtomicLong()
    private val uploadNanos = AtomicLong()
    private val rasterCpuNanos = AtomicLong()
    private val submitNanos = AtomicLong()
    private val waitNanos = AtomicLong()
    private val readbackNanos = AtomicLong()
    private val gpuNanos = AtomicLong()

    val isAvailable: Boolean get() = handle != 0L

    override fun rasterize(
        surface: SparseTileSurface,
        bounds: RectF,
        preserveAlpha: Boolean,
        draw: (Canvas) -> Unit,
    ): TileRasterResult = BitmapCanvasTileRasterBackend().rasterize(surface, bounds, preserveAlpha, draw)

    override fun rasterizeDabs(request: RasterDabRequest): TileRasterResult? {
        if (!isAvailable || request.dabs.isEmpty()) return null
        val snapshots = TileStorage.keysForBounds(
            request.bounds,
            request.surface.width,
            request.surface.height,
        ).associateWith(request.surface::snapshotTile)
        val packedDabs = FloatArray(request.dabs.size * FLOATS_PER_DAB)
        request.dabs.forEachIndexed { index, dab ->
            val offset = index * FLOATS_PER_DAB
            packedDabs[offset] = dab.x
            packedDabs[offset + 1] = dab.y
            packedDabs[offset + 2] = dab.radiusX
            packedDabs[offset + 3] = dab.radiusY
            packedDabs[offset + 4] = dab.rotationRadians
            packedDabs[offset + 5] = dab.opacity
            packedDabs[offset + 6] = dab.flow
            packedDabs[offset + 7] = dab.randomSeed.toFloat()
            packedDabs[offset + 8] = ((dab.color ushr 16) and 0xff) / 255f
            packedDabs[offset + 9] = ((dab.color ushr 8) and 0xff) / 255f
            packedDabs[offset + 10] = (dab.color and 0xff) / 255f
            packedDabs[offset + 11] = ((dab.color ushr 24) and 0xff) / 255f
        }
        var flags = 0
        if (request.erase) flags = flags or FLAG_ERASER
        if (request.preserveAlpha) flags = flags or FLAG_PRESERVE_ALPHA
        if (request.material == VulkanBrushMaterial.TILTED_GRAPHITE) flags = flags or FLAG_GRAPHITE
        if (request.selectionInverted) flags = flags or FLAG_SELECTION_INVERTED
        var failed = false
        val changed = request.surface.mutateTilePixels(
            request.bounds,
            createIfMissing = !request.preserveAlpha || request.erase,
        ) { _, rect, pixels ->
            val timings = LongArray(6)
            val success = try {
                Trace.beginSection("CanvasStudio.Vulkan.TileBatch")
                VulkanNativeBridge.render(
                    handle = handle,
                    pixels = pixels,
                    width = rect.width(),
                    height = rect.height(),
                    tileLeft = rect.left,
                    tileTop = rect.top,
                    dabs = packedDabs,
                    selection = request.selection,
                    flags = flags,
                    grainDepth = request.grainDepth,
                    timings = timings,
                )
            } catch (_: Throwable) {
                false
            } finally {
                Trace.endSection()
            }
            if (success) {
                batches.incrementAndGet()
                uploadNanos.addAndGet(timings[0])
                rasterCpuNanos.addAndGet(timings[1])
                submitNanos.addAndGet(timings[2])
                waitNanos.addAndGet(timings[3])
                readbackNanos.addAndGet(timings[4])
                gpuNanos.addAndGet(timings[5])
            } else {
                failures.incrementAndGet()
                failed = true
            }
            success
        }
        if (failed) {
            snapshots.forEach(request.surface::restoreTile)
            snapshots.values.filterNotNull().forEach { if (!it.isRecycled) it.recycle() }
            return null
        }
        snapshots.values.filterNotNull().forEach { if (!it.isRecycled) it.recycle() }
        return TileRasterResult(changed > 0, changed)
    }

    fun stats(): VulkanRasterStats = VulkanRasterStats(
        initialized = isAvailable,
        deviceName = if (isAvailable) VulkanNativeBridge.deviceName(handle) else "Unavailable",
        batches = batches.get(),
        failures = failures.get(),
        uploadNanos = uploadNanos.get(),
        rasterCpuNanos = rasterCpuNanos.get(),
        submitNanos = submitNanos.get(),
        waitNanos = waitNanos.get(),
        readbackNanos = readbackNanos.get(),
        gpuNanos = gpuNanos.get(),
        allocatedBytes = if (isAvailable) VulkanNativeBridge.allocatedBytes(handle) else 0L,
    )

    override fun close() {
        if (handle != 0L) VulkanNativeBridge.destroy(handle)
    }

    private companion object {
        const val FLOATS_PER_DAB = 12
        const val FLAG_ERASER = 1
        const val FLAG_PRESERVE_ALPHA = 2
        const val FLAG_GRAPHITE = 4
        const val FLAG_SELECTION_INVERTED = 8
    }
}

internal object VulkanNativeBridge {
    private val loaded = runCatching { System.loadLibrary("canvasstudio_vulkan") }.isSuccess

    fun create(): Long = if (loaded) nativeCreate() else 0L
    fun destroy(handle: Long) { if (loaded) nativeDestroy(handle) }
    fun render(
        handle: Long,
        pixels: IntArray,
        width: Int,
        height: Int,
        tileLeft: Int,
        tileTop: Int,
        dabs: FloatArray,
        selection: FloatArray,
        flags: Int,
        grainDepth: Float,
        timings: LongArray,
    ): Boolean = loaded && nativeRender(
        handle, pixels, width, height, tileLeft, tileTop, dabs, selection, flags, grainDepth, timings,
    )
    fun deviceName(handle: Long): String = if (loaded) nativeDeviceName(handle) else "Unavailable"
    fun allocatedBytes(handle: Long): Long = if (loaded) nativeAllocatedBytes(handle) else 0L

    @JvmStatic private external fun nativeCreate(): Long
    @JvmStatic private external fun nativeDestroy(handle: Long)
    @JvmStatic private external fun nativeRender(
        handle: Long,
        pixels: IntArray,
        width: Int,
        height: Int,
        tileLeft: Int,
        tileTop: Int,
        dabs: FloatArray,
        selection: FloatArray,
        flags: Int,
        grainDepth: Float,
        timings: LongArray,
    ): Boolean
    @JvmStatic private external fun nativeDeviceName(handle: Long): String
    @JvmStatic private external fun nativeAllocatedBytes(handle: Long): Long
}

internal fun BrushKind.vulkanMaterialOrNull(): VulkanBrushMaterial? = when (this) {
    BrushKind.INK -> VulkanBrushMaterial.TECHNICAL_INK
    BrushKind.PENCIL -> VulkanBrushMaterial.TILTED_GRAPHITE
    else -> null
}
