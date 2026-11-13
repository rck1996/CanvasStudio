package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SparseTileSurfaceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hardwareViewportKeepsTranslucentTilesUniformAtZoomAndRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val directory = freshDirectory("hardware-translucent-seam")
        val bounds = RectF(0f, 0f, 1536f, 1024f)
        val surface = SparseTileSurface(1536, 1024, directory, 12L * 1024L * 1024L)
        val pigment = Paint().apply { color = Color.argb(96, 0, 0, 0) }
        val sampling = Paint().apply { isFilterBitmap = false }
        val drawn = CountDownLatch(1)
        var hardware = false
        var angle = 0f
        var zoom = .789f
        var exported: Bitmap? = null
        var useExport = false
        val view = object : View(activity) {
            override fun onDraw(canvas: Canvas) {
                hardware = canvas.isHardwareAccelerated
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(width / 2f + .37f, height / 2f + .65f)
                canvas.rotate(angle)
                canvas.scale(zoom, zoom)
                canvas.translate(-768f, -512f)
                if (useExport) canvas.drawBitmap(requireNotNull(exported), 0f, 0f, sampling)
                else surface.drawVisible(canvas, bounds, sampling)
                canvas.restore()
                drawn.countDown()
            }
        }
        try {
            assertTrue(surface.draw(bounds) { it.drawRect(bounds, pigment) })
            instrumentation.runOnMainSync {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                activity.addContentView(view, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }
            assertTrue(drawn.await(6, TimeUnit.SECONDS))
            assertTrue("Must exercise the GPU viewport", hardware)
            fun capture(degrees: Float, scale: Float, reference: Boolean): IntArray {
                val copied = CountDownLatch(1)
                val result = IntArray(1) { -1 }
                val bitmap = Bitmap.createBitmap(600, 360, Bitmap.Config.ARGB_8888)
                try {
                    instrumentation.runOnMainSync {
                        angle = degrees
                        zoom = scale
                        useExport = reference
                        val location = IntArray(2).also(view::getLocationInWindow)
                        val x = location[0] + view.width / 2 - bitmap.width / 2
                        val y = location[1] + view.height / 2 - bitmap.height / 2
                        val crop = Rect(x, y, x + bitmap.width, y + bitmap.height)
                        // Queue PixelCopy after the invalidated hardware layer is presented.
                        view.postInvalidateOnAnimation()
                        view.postDelayed({
                            PixelCopy.request(activity.window, crop, bitmap, {
                                result[0] = it
                                copied.countDown()
                            }, Handler(Looper.getMainLooper()))
                        }, 150L)
                    }
                    assertTrue("PixelCopy did not return", copied.await(6, TimeUnit.SECONDS))
                    assertEquals(PixelCopy.SUCCESS, result[0])
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    return pixels
                } finally {
                    bitmap.recycle()
                }
            }
            val scenarios = listOf(0f to .789f, 17f to .673f, -24f to 1.23f)
            for ((degrees, scale) in scenarios) {
                val pixels = capture(degrees, scale, reference = false)
                assertTrue("Tile lines at zoom $scale, rotation $degrees: ${pixels.minOf { Color.red(it) }}..${pixels.maxOf { Color.red(it) }}",
                    pixels.all { Color.red(it) in 158..160 })
            }
            instrumentation.runOnMainSync {
                BrushRasterSession().use { session ->
                    val painter = StrokeCommandPainter(session)
                    val materials = listOf("marker", "flat-marker", "bristle", "thick-oil", "impasto-bristle", "granulated-watercolor")
                    for ((index, id) in materials.withIndex()) {
                        val command = StrokeCommand(
                            points = (0..36).map { step ->
                                StrokePoint(104f + step * 36f, 388f + index * 47f + kotlin.math.sin(step * .32f) * 23f,
                                    .3f + step / 52f, .18f, step * 13L)
                            },
                            tool = DrawingTool.BRUSH,
                            settings = requireNotNull(resolveBuiltInBrush(id)).toSettings(Color.rgb(35, 24, 16)).copy(sizePx = 94f),
                        )
                        val plan = session.planDistanceDabs(command)
                        surface.drawPerTile(bounds) { canvas, tile ->
                            painter.draw(canvas, command, bounds, renderClipBounds = tile, distancePlan = plan)
                        }
                    }
                }
                exported = surface.renderBitmap(1536L * 1024L)
            }
            for ((degrees, scale) in scenarios) {
                val tiled = capture(degrees, scale, reference = false)
                val flat = capture(degrees, scale, reference = true)
                assertTrue("The reference should contain visible texture", flat.any { Color.red(it) < 110 })
                // A single draw and adjacent draws may round sample coordinates
                // differently on fractional zoom, particularly in SwiftShader.
                // Accept the flat reference's local 1px intensity range rather
                // than hiding line artifacts with a wider global error budget.
                // The uniform fill above independently rejects even one line.
                val different = flat.indices.count { exceedsLocalIntensityRange(flat, tiled, it, 10) }
                assertTrue("Viewport/flattened mismatch ($scale, $degrees): $different pixels", different < flat.size / 120)

                // A seam is too narrow to be caught reliably by a whole-image tolerance.
                val viewport = Matrix().apply {
                    setTranslate(-768f, -512f)
                    postScale(scale, scale)
                    postRotate(degrees)
                    postTranslate(300.37f + (view.width % 2) * .5f, 180.65f + (view.height % 2) * .5f)
                }
                val inverse = Matrix()
                assertTrue(viewport.invert(inverse))
                val seamSamples = IntArray(3)
                val seamDifferences = IntArray(3)
                val point = FloatArray(2)
                for (index in flat.indices) {
                    point[0] = (index % 600) + .5f
                    point[1] = (index / 600) + .5f
                    inverse.mapPoints(point)
                    fun checkSeam(seam: Int, distance: Float) {
                        if (kotlin.math.abs(distance) * scale > 1.25f) return
                        seamSamples[seam]++
                        if (exceedsLocalIntensityRange(flat, tiled, index, 18)) {
                            seamDifferences[seam]++
                        }
                    }
                    checkSeam(0, point[0] - 512f)
                    checkSeam(1, point[0] - 1024f)
                    checkSeam(2, point[1] - 512f)
                }
                for (seam in seamSamples.indices) {
                    assertTrue("Unpainted or double-blended seam $seam ($scale, $degrees): " +
                        "${seamDifferences[seam]}/${seamSamples[seam]} pixels",
                        seamSamples[seam] > 100 && seamDifferences[seam] <= seamSamples[seam] / 40)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                (view.parent as? ViewGroup)?.removeView(view)
                activity.finish()
            }
            surface.recycle()
            exported?.recycle()
            directory.deleteRecursively()
        }
    }

    private fun exceedsLocalIntensityRange(reference: IntArray, actual: IntArray, index: Int, tolerance: Int): Boolean {
        val value = Color.red(actual[index])
        val x = index % 600
        val y = index / 600
        var low = Color.red(reference[index])
        var high = low
        if (value in low - tolerance..high + tolerance) return false
        for (dy in -1..1) for (dx in -1..1) {
            val px = x + dx
            val py = y + dy
            if (px !in 0..599 || py !in 0..359) continue
            val sample = Color.red(reference[py * 600 + px])
            low = minOf(low, sample)
            high = maxOf(high, sample)
        }
        return value !in low - tolerance..high + tolerance
    }

    @Test
    fun translucentTilesDoNotDoubleBlendAtFractionalZoom() {
        val directory = freshDirectory("visible-translucent-seam")
        val bounds = RectF(0f, 0f, 1536f, 1024f)
        val surface = SparseTileSurface(1536, 1024, directory, 12L * 1024L * 1024L)
        val bitmap = Bitmap.createBitmap(1230, 820, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { color = Color.argb(96, 15, 70, 125) }
        try {
            assertTrue(surface.draw(bounds) { it.drawRect(bounds, paint) })
            val canvas = Canvas(bitmap)
            canvas.translate(.37f, .65f)
            canvas.scale(.789f, .789f)
            surface.drawVisible(canvas, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
            for (y in 390..421) for (x in 390..825) {
                assertEquals("Visible tile seam at $x,$y", 96, Color.alpha(bitmap.getPixel(x, y)))
            }
        } finally {
            surface.recycle()
            bitmap.recycle()
            directory.deleteRecursively()
        }
    }

    @Test
    fun twoHundredThickStrokesSurviveFlushEvictionAndReload() {
        val directory = freshDirectory("massive-strokes")
        val points = uniqueStrokePoints(count = 200)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val surface = SparseTileSurface(
            width = DOCUMENT_WIDTH,
            height = DOCUMENT_HEIGHT,
            workingDirectory = directory,
            cacheBudgetBytes = 1L * 1024L * 1024L,
        )
        points.forEachIndexed { index, point ->
            val radius = 18f + (index % 4) * 7f
            assertTrue(
                surface.draw(RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) {
                    it.drawCircle(point.x, point.y, radius, paint)
                },
            )
        }
        assertTrue(surface.flushPending())
        assertEquals(200, points.size)
        assertTrue(surface.stats().storedTiles > 1)
        surface.recycle()

        val reopened = SparseTileSurface(
            width = DOCUMENT_WIDTH,
            height = DOCUMENT_HEIGHT,
            workingDirectory = directory,
            cacheBudgetBytes = 1L * 1024L * 1024L,
        )
        points.forEachIndexed { index, point ->
            assertTrue("Stroke ${index + 1} disappeared after reload", reopened.isOpaqueAt(point.x, point.y))
        }
        assertTrue("Reload should exercise disk-backed tiles", reopened.stats().diskLoads > 1)
        reopened.recycle()
        directory.deleteRecursively()
    }

    @Test
    fun visibleStoredTileIsReportedMissingUntilPrefetched() {
        val directory = freshDirectory("visible-prefetch")
        val bounds = RectF(700f, 700f, 760f, 760f)
        val surface = SparseTileSurface(2048, 2048, directory, 1L * 1024L * 1024L)
        assertTrue(surface.draw(bounds) { canvas ->
            canvas.drawRect(bounds, Paint().apply { color = Color.MAGENTA })
        })
        assertTrue(surface.flushPending())
        surface.recycle()

        val reopened = SparseTileSurface(2048, 2048, directory, 1L * 1024L * 1024L)
        assertTrue(reopened.hasMissingVisibleTiles(bounds))
        assertEquals(1, reopened.prefetch(bounds))
        assertFalse(reopened.hasMissingVisibleTiles(bounds))
        assertTrue(reopened.isOpaqueAt(730f, 730f))
        reopened.recycle()
        directory.deleteRecursively()
    }

    @Test
    fun detachedBackgroundCompositeMatchesResidentRasterWithoutGrowingTileCache() {
        val directory = freshDirectory("detached-preview")
        val area = RectF(470f, 470f, 570f, 570f)
        val surface = SparseTileSurface(1024, 1024, directory, 2L * 1024L * 1024L)
        val reference = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val detached = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(surface.draw(area) { canvas ->
                canvas.drawRect(area, Paint().apply { color = Color.CYAN })
            })
            surface.drawAll(Canvas(reference), null)
            assertTrue(surface.flushPending())
            surface.recycle()

            val reopened = SparseTileSurface(1024, 1024, directory, 2L * 1024L * 1024L)
            try {
                reopened.drawAllDetached(Canvas(detached), null)
                assertEquals(0, reopened.stats().residentTiles)
                for (y in listOf(0, 469, 470, 511, 512, 569, 570, 900)) {
                    for (x in listOf(0, 469, 470, 511, 512, 569, 570, 900)) {
                        assertEquals("Mismatch at $x,$y",
                            reference.getPixel(x, y), detached.getPixel(x, y))
                    }
                }
            } finally {
                reopened.recycle()
            }
        } finally {
            reference.recycle()
            detached.recycle()
            directory.deleteRecursively()
        }
    }

    private fun SparseTileSurface.isOpaqueAt(x: Float, y: Float): Boolean {
        val sample = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return try {
            drawAtPoint(Canvas(sample), x, y, null)
            Color.alpha(sample.getPixel(0, 0)) > 0
        } finally {
            sample.recycle()
        }
    }

    private fun uniqueStrokePoints(count: Int): List<Point> = buildList(count) {
        repeat(10) { row ->
            repeat(20) { column ->
                add(Point(120f + column * 195f, 120f + row * 270f))
            }
        }
    }.take(count)

    private fun freshDirectory(name: String): File =
        File(context.cacheDir, "sparse-tile-tests/$name").apply {
            deleteRecursively()
            mkdirs()
        }

    private data class Point(val x: Float, val y: Float)

    private companion object {
        const val DOCUMENT_WIDTH = 4096
        const val DOCUMENT_HEIGHT = 2732
    }
}
