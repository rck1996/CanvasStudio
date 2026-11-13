package com.orbyte.canvasstudio.drawing.pipeline

import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeCommitLaneTest {
    private class ManualExecutor : Executor {
        private val jobs = ArrayDeque<Runnable>()
        val pending get() = jobs.size
        override fun execute(command: Runnable) { jobs.addLast(command) }
        fun next() { jobs.removeFirst().run() }
        fun drain() { while (jobs.isNotEmpty()) next() }
    }
    private class ResultTile(private val id: String, private val log: MutableList<String>) : Closeable {
        override fun close() { log += "release-$id" }
    }

    @Test
    fun secondPrepareStartsOnlyAfterFirstPublication() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val log = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { log += "error" }
        lane.enqueue(
            prepare = { log += "prepare-1"; ResultTile("1", log) },
            finish = { log += "commit-1" },
        )
        lane.enqueue(
            prepare = { log += "prepare-2"; ResultTile("2", log) },
            finish = { log += "commit-2" },
        )
        assertEquals(2, lane.pendingCount)
        assertEquals(1, worker.pending)
        worker.next()
        // Next worker task must not begin or even be scheduled before UI commit.
        assertEquals(listOf("prepare-1"), log)
        assertEquals(0, worker.pending)
        ui.next()
        assertEquals(listOf("prepare-1", "commit-1", "release-1"), log)
        assertEquals(1, worker.pending)
        worker.next()
        ui.next()
        assertEquals(
            listOf("prepare-1", "commit-1", "release-1",
                "prepare-2", "commit-2", "release-2"),
            log,
        )
        assertFalse(lane.hasPending)
    }

    @Test
    fun synchronousBarrierReplaysAllPendingAndDropsInflightResult() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val log = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { log += "error" }
        lane.enqueue(
            prepare = { ResultTile("stale", log) },
            finish = { tile -> log += if (tile == null) "fallback-1" else "commit-1" },
        )
        lane.enqueue(
            prepare = { ResultTile("next", log) },
            finish = { tile -> log += if (tile == null) "fallback-2" else "commit-2" },
        )
        worker.next() // Worker prepared old tiles but UI publication is not run.
        lane.flushSynchronously() // Undo/save barrier, called from owner thread.
        assertEquals(listOf("fallback-1", "fallback-2"), log)
        assertFalse(lane.hasPending)
        ui.next() // Discard stale worker result, do not duplicate history.
        assertEquals(listOf("fallback-1", "fallback-2", "release-stale"), log)
        assertEquals(0, worker.pending)
    }

    @Test
    fun nextGestureCanQueueAfterInvalidatingOldWork() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val log = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { log += "error" }
        lane.enqueue(
            prepare = { log += "prepare-stale"; ResultTile("old", log) },
            finish = { log += "finish-old" },
        )
        lane.flushSynchronously()
        lane.enqueue(
            prepare = { log += "prepare-new"; ResultTile("new", log) },
            finish = { log += "finish-new" },
        )
        worker.drain()
        ui.drain()
        assertEquals(
            listOf("finish-old", "prepare-stale", "prepare-new",
                "release-old", "finish-new", "release-new"),
            log,
        )
        assertFalse(lane.hasPending)
    }

    @Test
    fun workerFailureFallsBackAndDoesNotStrandQueue() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val log = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { log += "error:${it.message}" }
        lane.enqueue(
            prepare = { error("decode failed") },
            finish = { tile -> log += if (tile == null) "fallback" else "commit" },
        )
        lane.enqueue(
            prepare = { ResultTile("two", log) },
            finish = { log += "second" },
        )
        worker.drain()
        ui.drain()
        worker.drain()
        ui.drain()
        assertEquals(listOf("fallback", "error:decode failed", "second", "release-two"), log)
    }

    @Test
    fun nestedSaveBarrierCannotReplayStrokeAlreadyBeingPublished() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val events = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { events += "error" }
        lane.enqueue(
            prepare = { ResultTile("one", events) },
            finish = {
                events += "publish-one"
                // Simulates synchronous save called by onDocumentChanged() while
                // the same transaction's owner-thread callback is still running.
                lane.flushSynchronously()
                events += "saved"
            },
        )
        worker.drain()
        ui.drain()
        assertEquals(listOf("publish-one", "saved", "release-one"), events)
        assertFalse(lane.hasPending)
    }

    @Test
    fun reentrantBarrierFlushesOnlyLaterStrokesInOrder() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val events = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { events += "error" }
        lane.enqueue(
            prepare = { ResultTile("one", events) },
            finish = {
                events += "publish-one"
                lane.flushSynchronously()
            },
        )
        lane.enqueue(
            prepare = { ResultTile("two", events) },
            finish = { result ->
                events += if (result == null) "fallback-two" else "publish-two"
            },
        )
        worker.drain()
        ui.drain()
        assertEquals(
            listOf("publish-one", "fallback-two", "release-one"),
            events,
        )
        assertFalse(lane.hasPending)
    }

    @Test
    fun autosaveWaitsForEveryStrokeAndCoalescesToLatestRequest() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val events = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, main::execute) { events += "error" }
        lane.enqueue(
            prepare = { ResultTile("one", events) },
            finish = { events += "commit-one" },
        )
        lane.enqueue(
            prepare = { ResultTile("two", events) },
            finish = { events += "commit-two" },
        )
        lane.whenIdleForAutosave { events += "stale-autosave" }
        lane.whenIdleForAutosave { events += "latest-autosave" }
        assertTrue(events.isEmpty())
        worker.next()
        main.next()
        assertEquals(listOf("commit-one", "release-one"), events)
        worker.next()
        main.next()
        assertEquals(
            listOf("commit-one", "release-one", "commit-two",
                "release-two", "latest-autosave"),
            events,
        )
        assertFalse(lane.hasPending)
    }

    @Test
    fun explicitSaveCanCancelDeferredAutosaveBeforeItRuns() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val events = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, main::execute) { events += "error" }
        lane.enqueue(
            prepare = { ResultTile("one", events) },
            finish = { events += "commit-one" },
        )
        lane.whenIdleForAutosave { events += "autosave-unexpected" }
        lane.cancelDeferredAutosave()
        lane.flushSynchronously()
        worker.drain()
        main.drain()
        assertEquals(listOf("commit-one", "release-one"), events)
    }

    @Test
    fun closeFlushesBeforeRejectingNewStrokes() {
        val worker = ManualExecutor()
        val ui = ManualExecutor()
        val log = mutableListOf<String>()
        val lane = StrokeCommitLane<ResultTile>(worker, ui::execute) { log += "error" }
        assertTrue(lane.enqueue({ ResultTile("old", log) }) { log += "fallback" })
        lane.close()
        assertFalse(lane.enqueue({ null }) { log += "invalid" })
        assertEquals(listOf("fallback"), log)
        worker.drain()
        ui.drain()
        assertEquals(listOf("fallback", "release-old"), log)
    }
}
