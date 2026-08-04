package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.drawing.history.HistoryRasterTarget
import com.orbyte.canvasstudio.drawing.history.HistorySurfaceKey
import com.orbyte.canvasstudio.drawing.history.TileCommandIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileCommandIndexTest {
    private val content = HistorySurfaceKey("layer", HistoryRasterTarget.CONTENT)
    private val mask = HistorySurfaceKey("layer", HistoryRasterTarget.MASK)
    private val a = TileStorage.Key(0, 0)
    private val b = TileStorage.Key(1, 0)

    @Test fun registersAndQueriesOneTile() {
        val index = TileCommandIndex(); index.register("a", 0, mapOf(content to setOf(a)), 1)
        assertEquals(listOf("a"), index.commandIdsFor(content, setOf(a)))
    }

    @Test fun commandCanCrossFourTilesWithoutDuplicates() {
        val index = TileCommandIndex(); val tiles = setOf(a, b, TileStorage.Key(0, 1), TileStorage.Key(1, 1))
        index.register("cross", 1, mapOf(content to tiles), 4)
        assertEquals(listOf("cross"), index.commandIdsFor(content, tiles))
        assertEquals(4, index.entryCount())
    }

    @Test fun contentAndMaskAreIsolatedAndOrderedByHistoryPosition() {
        val index = TileCommandIndex()
        index.register("late", 4, mapOf(content to setOf(a)), 1)
        index.register("mask", 1, mapOf(mask to setOf(a)), 1)
        index.register("early", 2, mapOf(content to setOf(a)), 1)
        assertEquals(listOf("early", "late"), index.commandIdsFor(content, setOf(a)))
        assertEquals(listOf("mask"), index.commandIdsFor(mask, setOf(a)))
    }

    @Test fun rangeAndBranchTruncationRemoveFutureCommands() {
        val index = TileCommandIndex()
        (0..2).forEach { index.register("c$it", it, mapOf(content to setOf(a)), 1) }
        assertEquals(listOf("c1"), index.commandIdsFor(content, setOf(a), 0, 1))
        index.truncateAfter(0)
        assertEquals(listOf("c0"), index.commandIdsFor(content, setOf(a)))
    }

    @Test fun surfaceDeletionAndEmptyQueryReleaseEntries() {
        val index = TileCommandIndex(); index.register("a", 0, mapOf(content to setOf(a), mask to setOf(b)), 1)
        assertTrue(index.commandIdsFor(content, setOf(b)).isEmpty())
        index.removeSurface(mask)
        assertTrue(index.commandIdsFor(mask, setOf(b)).isEmpty())
        assertEquals(listOf("a"), index.commandIdsFor(content, setOf(a)))
    }

    @Test fun removingMaskKeepsContentForACommandThatAffectedBothSurfaces() {
        val index = TileCommandIndex()
        index.register("shared", 3, mapOf(content to setOf(a), mask to setOf(a)), 2)
        index.removeSurface(mask)
        assertEquals(listOf("shared"), index.commandIdsFor(content, setOf(a)))
        assertTrue(index.commandIdsFor(mask, setOf(a)).isEmpty())
        assertEquals(mapOf(content to setOf(a)), index.affectedTilesFor("shared"))
        assertEquals(1, index.entryCount())
    }

    @Test(expected = IllegalStateException::class) fun rejectsDuplicateRegistration() {
        val index = TileCommandIndex(); index.register("a", 0, mapOf(content to setOf(a)), 1)
        index.register("a", 1, mapOf(content to setOf(b)), 1)
    }
}
