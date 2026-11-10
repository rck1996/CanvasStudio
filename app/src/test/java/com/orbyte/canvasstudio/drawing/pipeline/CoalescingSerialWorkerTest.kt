package com.orbyte.canvasstudio.drawing.pipeline

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoalescingSerialWorkerTest {
    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }
        fun drain() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    @Test
    fun latestWinsWhileKeepingOneWorkerTask() {
        val executor = ManualExecutor()
        val outcomes = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val worker = CoalescingSerialWorker(executor, failures::add)
        assertTrue(worker.submitLatest { outcomes += "stale-1" })
        assertTrue(worker.submitLatest { outcomes += "stale-2" })
        assertTrue(worker.submitLatest { outcomes += "latest" })
        assertEquals(1, executor.tasks.size)
        executor.drain()
        assertEquals(listOf("latest"), outcomes)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun runningJobCanRequestAnotherJobWithoutConcurrentExecution() {
        val executor = ManualExecutor()
        val events = mutableListOf<Int>()
        val worker = CoalescingSerialWorker(executor) { throw AssertionError(it) }
        assertTrue(worker.submitLatest {
            events += 1
            worker.submitLatest { events += 3 }
            worker.submitLatest { events += 4 }
        })
        executor.drain()
        assertEquals(listOf(1, 4), events)
    }

    @Test
    fun exceptionDoesNotBlockLastPendingJobAndCloseRejectsNewWork() {
        val executor = ManualExecutor()
        val failures = mutableListOf<Throwable>()
        val events = mutableListOf<Int>()
        val worker = CoalescingSerialWorker(executor, failures::add)
        worker.submitLatest {
            events += 1
            worker.submitLatest { events += 2 }
            error("intentional")
        }
        executor.drain()
        assertEquals(listOf(1, 2), events)
        assertEquals(1, failures.size)
        worker.close()
        assertFalse(worker.submitLatest { events += 3 })
    }
}
