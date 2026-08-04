package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.history.HistoryRasterTarget
import com.orbyte.canvasstudio.drawing.history.HistorySurfaceKey
import com.orbyte.canvasstudio.drawing.history.TileCheckpointKey
import com.orbyte.canvasstudio.drawing.history.TileCheckpointPolicy
import com.orbyte.canvasstudio.drawing.history.TileCheckpointStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

@RunWith(AndroidJUnit4::class)
class TileCheckpointStoreTest {
    private val content = HistorySurfaceKey("layer", HistoryRasterTarget.CONTENT)
    private val mask = HistorySurfaceKey("layer", HistoryRasterTarget.MASK)
    private val tile = TileStorage.Key(0, 0)

    @Test fun nearestCheckpointIsAtOrBeforeCursorAndSurfacesStaySeparate() {
        val store = TileCheckpointStore(1024)
        store.put(TileCheckpointKey(content, tile, 2), pixel(Color.RED))
        store.put(TileCheckpointKey(content, tile, 7), pixel(Color.BLUE))
        store.put(TileCheckpointKey(mask, tile, 5), pixel(Color.WHITE))
        assertEquals(2, store.findNearest(content, tile, 6)?.key?.historyPosition)
        assertEquals(7, store.findNearest(content, tile, 9)?.key?.historyPosition)
        assertEquals(5, store.findNearest(mask, tile, 9)?.key?.historyPosition)
        store.clear()
    }

    @Test fun missingAndTransparentCheckpointsAreRepresented() {
        val store = TileCheckpointStore(1024)
        assertNull(store.findNearest(content, tile, 0))
        store.put(TileCheckpointKey(content, tile, 0), null)
        val restored = store.findNearest(content, tile, 0)
        assertTrue(restored != null && restored.bitmap == null)
        store.clear()
    }

    @Test fun snapshotsAreDetachedFromSourceAndRestoreCopiesAreDetached() {
        val source = pixel(Color.RED)
        val store = TileCheckpointStore(1024)
        store.put(TileCheckpointKey(content, tile, 0), source)
        source.setPixel(0, 0, Color.BLUE)
        val first = requireNotNull(store.findNearest(content, tile, 0)?.bitmap)
        assertEquals(Color.RED, first.getPixel(0, 0))
        first.recycle()
        val second = requireNotNull(store.findNearest(content, tile, 0)?.bitmap)
        assertEquals(Color.RED, second.getPixel(0, 0))
        second.recycle(); source.recycle(); store.clear()
    }

    @Test fun budgetEvictsLeastRecentlyUsedAndNeverExceedsLimit() {
        val store = TileCheckpointStore(8)
        store.put(TileCheckpointKey(content, tile, 0), pixel(Color.RED))
        store.put(TileCheckpointKey(content, TileStorage.Key(1, 0), 1), pixel(Color.BLUE))
        store.findNearest(content, tile, 1)?.bitmap?.recycle()
        store.put(TileCheckpointKey(content, TileStorage.Key(2, 0), 2), pixel(Color.GREEN))
        assertNull(store.findNearest(content, TileStorage.Key(1, 0), 2))
        assertTrue(store.stats().bytes <= store.stats().budgetBytes)
        assertEquals(1, store.stats().evictions)
        store.clear()
    }

    @Test fun branchAndSurfaceInvalidationAreTargeted() {
        val store = TileCheckpointStore(1024)
        store.put(TileCheckpointKey(content, tile, 1), null)
        store.put(TileCheckpointKey(content, tile, 3), null)
        store.put(TileCheckpointKey(mask, tile, 2), null)
        store.invalidateAfter(1)
        assertEquals(1, store.findNearest(content, tile, 9)?.key?.historyPosition)
        assertNull(store.findNearest(mask, tile, 9))
        store.removeSurface(content)
        assertNull(store.findNearest(content, tile, 9))
    }

    @Test fun adaptivePolicyUsesLocalCostSignals() {
        val policy = TileCheckpointPolicy(3, 10, 100)
        assertTrue(policy.shouldCreate(3, 0, 0))
        assertTrue(policy.shouldCreate(0, 10, 0))
        assertTrue(policy.shouldCreate(0, 0, 100))
    }

    @Test fun restoredCheckpointSurvivesTileFlushEvictionAndReload() {
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "phase2/checkpoint-eviction",
        ).apply { deleteRecursively(); mkdirs() }
        val bounds = RectF(0f, 0f, 64f, 64f)
        val surface = SparseTileSurface(1024, 1024, directory, 1024L * 1024L)
        surface.draw(bounds) { it.drawRect(bounds, Paint().apply { color = Color.RED }) }
        val store = TileCheckpointStore(2L * 1024L * 1024L)
        val snapshot = surface.snapshotTile(tile)
        store.put(TileCheckpointKey(content, tile, 0), snapshot)
        snapshot?.recycle()
        surface.draw(bounds) { it.drawRect(bounds, Paint().apply { color = Color.BLUE }) }
        val restore = requireNotNull(store.findNearest(content, tile, 0))
        surface.restoreTile(tile, restore.bitmap)
        restore.bitmap?.recycle()
        assertTrue(surface.flushPending())
        surface.recycle()

        val reopened = SparseTileSurface(1024, 1024, directory, 1024L * 1024L)
        assertEquals(Color.RED, reopened.samplePixel(10f, 10f))
        reopened.recycle(); store.clear(); directory.deleteRecursively()
    }

    private fun pixel(color: Int) = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, color)
    }
}
