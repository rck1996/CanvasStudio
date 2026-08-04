package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.brush.BrushDabBatchBuilder
import com.orbyte.canvasstudio.drawing.brush.BrushFixture
import com.orbyte.canvasstudio.drawing.raster.BitmapCanvasTileRasterBackend
import com.orbyte.canvasstudio.drawing.raster.RasterDabRequest
import com.orbyte.canvasstudio.drawing.raster.VulkanBrushMaterial
import com.orbyte.canvasstudio.drawing.raster.VulkanTileRasterBackend
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class VulkanTileRasterBackendTest {
    private lateinit var root: File

    @Before fun setUp() {
        root = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "vulkan-test-${System.nanoTime()}")
        root.mkdirs()
    }

    @After fun tearDown() { root.deleteRecursively() }

    @Test fun initializesRealVulkanDeviceAndReusesBoundedBuffers() {
        VulkanTileRasterBackend().use { backend ->
            assertTrue("La Tab S8 debe exponer Vulkan", backend.isAvailable)
            val request = request(surface("init"), "technical-ink", VulkanBrushMaterial.TECHNICAL_INK)
            assertTrue(backend.rasterizeDabs(request)?.changed == true)
            val first = backend.stats()
            assertTrue(first.deviceName.isNotBlank() && first.deviceName != "Unavailable")
            assertTrue(first.allocatedBytes in 1..16L * 1024L * 1024L)
            repeat(4) { backend.rasterizeDabs(request) }
            assertEquals(first.allocatedBytes, backend.stats().allocatedBytes)
        }
    }

    @Test fun technicalInkCanvasAndVulkanUseIdenticalDabsAcrossFourTiles() {
        val canvasSurface = surface("canvas-ink")
        val vulkanSurface = surface("vulkan-ink")
        val canvasRequest = request(canvasSurface, "technical-ink", VulkanBrushMaterial.TECHNICAL_INK)
        val vulkanRequest = canvasRequest.copy(surface = vulkanSurface)
        val canvasStarted = System.nanoTime()
        BitmapCanvasTileRasterBackend().rasterizeDabs(canvasRequest)
        val canvasNanos = System.nanoTime() - canvasStarted
        VulkanTileRasterBackend().use { backend ->
            val vulkanStarted = System.nanoTime()
            assertTrue(backend.rasterizeDabs(vulkanRequest)?.changed == true)
            val wall = System.nanoTime() - vulkanStarted
            val canvasPixels = render(canvasSurface)
            val vulkanPixels = render(vulkanSurface)
            val canvasCount = nonTransparent(canvasPixels)
            val vulkanCount = nonTransparent(vulkanPixels)
            val ratio = vulkanCount.toDouble() / canvasCount.coerceAtLeast(1)
            assertTrue("Cobertura A/B fuera de tolerancia: $ratio", ratio in .72..1.28)
            Log.i(
                "CanvasStudioRendererAB",
                "INK dabs=${canvasRequest.dabs.size} tiles=4 canvasNs=$canvasNanos " +
                    "vulkanWallNs=$wall canvasDabsPerSecond=${canvasRequest.dabs.size * 1e9 / canvasNanos} " +
                    "vulkanDabsPerSecond=${canvasRequest.dabs.size * 1e9 / wall} pixelsCanvas=$canvasCount " +
                    "pixelsVulkan=$vulkanCount coverageRatio=$ratio stats=${backend.stats()}",
            )
            canvasPixels.recycle(); vulkanPixels.recycle()
        }
    }

    @Test fun graphiteIsDeterministicAndDocumentAnchored() {
        val settings = premiumBrushes.single { it.id == "graphite-shader" }.toSettings()
        val points = BrushFixture.points(BrushFixture.Scenario.TILT_SHADING)
        val dabs = BrushDabBatchBuilder.build(points, settings, DrawingTool.BRUSH)
        val bounds = RectF(0f, 0f, 1024f, 1024f)
        val first = surface("graphite-a")
        val second = surface("graphite-b")
        val base = RasterDabRequest(first, bounds, dabs, VulkanBrushMaterial.TILTED_GRAPHITE, false, false, grainDepth = settings.grainProfile.depth)
        VulkanTileRasterBackend().use { backend ->
            assertTrue(backend.rasterizeDabs(base)?.changed == true)
            assertTrue(backend.rasterizeDabs(base.copy(surface = second))?.changed == true)
        }
        val a = render(first); val b = render(second)
        assertEquals(pixelHash(a), pixelHash(b))
        a.recycle(); b.recycle()
    }

    @Test fun tiltedGraphiteCanvasAndVulkanStayVisuallyComparable() {
        val settings = premiumBrushes.single { it.id == "graphite-shader" }.toSettings()
        val dabs = BrushDabBatchBuilder.build(
            BrushFixture.points(BrushFixture.Scenario.TILT_SHADING),
            settings,
            DrawingTool.BRUSH,
        )
        val bounds = RectF(0f, 0f, 1024f, 1024f)
        val canvasSurface = surface("canvas-graphite")
        val vulkanSurface = surface("vulkan-graphite")
        val base = RasterDabRequest(
            canvasSurface, bounds, dabs, VulkanBrushMaterial.TILTED_GRAPHITE,
            erase = false, preserveAlpha = false, grainDepth = settings.grainProfile.depth,
        )
        val canvasStarted = System.nanoTime()
        BitmapCanvasTileRasterBackend().rasterizeDabs(base)
        val canvasNanos = System.nanoTime() - canvasStarted
        VulkanTileRasterBackend().use { backend ->
            val started = System.nanoTime()
            assertTrue(backend.rasterizeDabs(base.copy(surface = vulkanSurface))?.changed == true)
            val vulkanWall = System.nanoTime() - started
            val canvasBitmap = render(canvasSurface)
            val vulkanBitmap = render(vulkanSurface)
            val ratio = nonTransparent(vulkanBitmap).toDouble() / nonTransparent(canvasBitmap).coerceAtLeast(1)
            assertTrue("Cobertura de grafito A/B fuera de tolerancia: $ratio", ratio in .55..1.2)
            Log.i(
                "CanvasStudioRendererAB",
                "GRAPHITE dabs=${dabs.size} tiles=4 canvasNs=$canvasNanos vulkanWallNs=$vulkanWall " +
                    "canvasDabsPerSecond=${dabs.size * 1e9 / canvasNanos} " +
                    "vulkanDabsPerSecond=${dabs.size * 1e9 / vulkanWall} coverageRatio=$ratio stats=${backend.stats()}",
            )
            canvasBitmap.recycle(); vulkanBitmap.recycle()
        }
    }

    @Test fun eraserSelectionAndAlphaLockAreAppliedByVulkan() {
        val surface = surface("semantics")
        surface.draw(RectF(0f, 0f, 1024f, 1024f)) { canvas ->
            canvas.drawRect(0f, 0f, 1024f, 1024f, Paint().apply { color = Color.argb(128, 40, 80, 120) })
        }
        val request = request(surface, "technical-ink", VulkanBrushMaterial.TECHNICAL_INK).copy(
            preserveAlpha = true,
            selection = floatArrayOf(256f, 256f, 768f, 256f, 768f, 768f, 256f, 768f),
        )
        VulkanTileRasterBackend().use { backend -> assertTrue(backend.rasterizeDabs(request)?.changed == true) }
        assertEquals(128, Color.alpha(surface.samplePixel(604f, 604f)!!))
        val outsideBefore = surface.samplePixel(100f, 100f)
        assertEquals(outsideBefore, surface.samplePixel(100f, 100f))
        VulkanTileRasterBackend().use { backend ->
            assertTrue(backend.rasterizeDabs(request.copy(erase = true, preserveAlpha = false))?.changed == true)
        }
        assertTrue(Color.alpha(surface.samplePixel(604f, 604f)!!) < 128)
    }

    @Test fun failedOrUnsupportedBackendLeavesCanvasFallbackAvailable() {
        val result = onMain {
            val view = DrawingView(ApplicationProvider.getApplicationContext())
            view.debugSimulateVulkanFailure(true)
            assertTrue(view.setRendererMode(com.orbyte.canvasstudio.drawing.raster.RendererMode.VULKAN_EXPERIMENTAL))
            view.brushSettings = premiumBrushes.single { it.id == "technical-ink" }.toSettings()
            view.debugDrawStrokeForTest(
                listOf(StrokePoint(100f, 100f, 1f, 0f, 0L), StrokePoint(700f, 700f, 1f, 0f, 20L)),
            )
            view.debugSimulateVulkanFailure(false)
            view.brushSettings = premiumBrushes.single { it.id == "granulated-watercolor" }.toSettings().copy(sizePx = 44f)
            view.debugDrawStrokeForTest(
                listOf(StrokePoint(100f, 760f, .5f, .2f, 30L), StrokePoint(700f, 760f, .7f, .3f, 50L)),
            )
            view.debugRendererFallbackCount() to view.debugPixelForTest(400f, 400f)
        }
        assertTrue(result.first >= 2L)
        assertNotEquals(0, result.second)
    }

    private fun surface(name: String) = SparseTileSurface(1024, 1024, File(root, name), 16L * 1024L * 1024L)

    private fun request(surface: SparseTileSurface, id: String, material: VulkanBrushMaterial): RasterDabRequest {
        val settings = premiumBrushes.single { it.id == id }.toSettings()
        val points = BrushFixture.points(BrushFixture.Scenario.FOUR_TILES)
        return RasterDabRequest(
            surface = surface,
            bounds = RectF(300f, 300f, 730f, 730f),
            dabs = BrushDabBatchBuilder.build(points, settings, DrawingTool.BRUSH),
            material = material,
            erase = false,
            preserveAlpha = false,
            grainDepth = settings.grainProfile.depth,
        )
    }

    private fun render(surface: SparseTileSurface): Bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
        surface.drawAll(Canvas(it), null)
    }

    private fun nonTransparent(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it ushr 24 != 0 }
    }

    private fun pixelHash(bitmap: Bitmap): Long {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.fold(1125899906842597L) { hash, pixel -> hash * 31L + pixel }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
