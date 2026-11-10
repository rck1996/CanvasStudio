package com.orbyte.canvasstudio.drawing.pipeline

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedRasterCommandQueueTest {
    private class ManualExecutor : Executor {
        val pending = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { pending.addLast(command) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    @Test
    fun publishesInOrderAndRunsBarrierOnlyAfterPriorCommits() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val queue = OrderedRasterCommandQueue(worker) { main.execute(it) }
        val events = mutableListOf<String>()
        queue.enqueue(
            prepare = { events += "prepare-1"; "one" },
            commit = { events += "commit-$it" },
        )
        queue.enqueue(
            prepare = { events += "prepare-2"; "two" },
            commit = { events += "commit-$it" },
        )
        queue.afterPrior { events += "barrier" }
        assertTrue(queue.hasPending)
        worker.drain()
        assertEquals(listOf("prepare-1", "prepare-2"), events)
        main.drain()
        assertEquals(
            listOf("prepare-1", "prepare-2", "commit-one", "commit-two", "barrier"),
            events,
        )
        assertFalse(queue.hasPending)
    }

    @Test
    fun invalidateDiscardsStaleRasterAndNeverRunsOldBarrier() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val queue = OrderedRasterCommandQueue(worker) { main.execute(it) }
        val events = mutableListOf<String>()
        queue.enqueue(
            prepare = { "old" },
            commit = { events += "stale-commit" },
            discard = { events += "discard-$it" },
        )
        queue.afterPrior { events += "old-barrier" }
        worker.drain()
        queue.invalidate()
        queue.enqueue(
            prepare = { "new" },
            commit = { events += "commit-$it" },
        )
        queue.afterPrior { events += "new-barrier" }
        worker.drain()
        main.drain()
        assertEquals(listOf("discard-old", "commit-new", "new-barrier"), events)
    }

    @Test
    fun errorPublicationDoesNotSkipBarrierOrSubsequentWork() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val queue = OrderedRasterCommandQueue(worker) { main.execute(it) }
        val events = mutableListOf<String>()
        queue.enqueue(
            prepare = { throw IllegalArgumentException("broken") },
            commit = { _: Int -> events += "not-published" },
            onError = { events += "error-${it.message}" },
        )
        queue.enqueue(
            prepare = { 3 },
            commit = { events += "commit-$it" },
        )
        queue.afterPrior { events += "barrier" }
        worker.drain()
        main.drain()
        assertEquals(listOf("error-broken", "commit-3", "barrier"), events)
    }

    @Test
    fun commitExceptionIsReportedAndDoesNotTrapPendingBarrier() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val events = mutableListOf<String>()
        val queue = OrderedRasterCommandQueue(worker) { main.execute(it) }
        queue.enqueue(
            prepare = { 4 },
            commit = { _: Int -> error("commit failed") },
            onError = { events += it.message.orEmpty() },
        )
        queue.afterPrior { events += "barrier" }
        worker.drain()
        main.drain()
        assertEquals(listOf("commit failed", "barrier"), events)
        assertFalse(queue.hasPending)
    }

    @Test
    fun closeDropsPendingPublicationAndRejectsNewWork() {
        val worker = ManualExecutor()
        val main = ManualExecutor()
        val queue = OrderedRasterCommandQueue(worker) { main.execute(it) }
        val events = mutableListOf<String>()
        assertTrue(queue.enqueue(prepare = { 1 }, commit = { events += "$it" }))
        worker.drain()
        queue.close()
        assertFalse(queue.enqueue(prepare = { 2 }, commit = { events += "$it" }))
        main.drain()
        assertTrue(events.isEmpty())
    }
}
