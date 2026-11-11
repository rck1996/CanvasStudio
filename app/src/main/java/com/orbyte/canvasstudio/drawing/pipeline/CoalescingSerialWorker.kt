package com.orbyte.canvasstudio.drawing.pipeline

import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Serial, bounded queue for expensive project snapshot / preview work.
 *
 * At most one task is executing and one latest request is waiting. This
 * prevents autosave bursts from spawning threads or accumulating an unbounded
 * list of obsolete save attempts. Callers must snapshot UI-only metadata
 * before enqueueing; a task must not inspect live View state.
 *
 * close() disallows new submissions but lets the active/last save complete.
 */
internal class CoalescingSerialWorker(
    private val executor: Executor,
    private val onFailure: (Throwable) -> Unit,
) : Closeable {
    private val lock = Any()
    private var scheduled = false
    private var closed = false
    private var pending: (() -> Unit)? = null

    /** @return false when shut down. */
    fun submitLatest(job: () -> Unit): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        pending = job
        if (!scheduled) {
            scheduled = true
            try {
                executor.execute(::drain)
            } catch (error: RuntimeException) {
                scheduled = false
                pending = null
                onFailure(error)
                return@synchronized false
            }
        }
        true
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val job = pending
                pending = null
                if (job == null) scheduled = false
                job
            } ?: return
            try {
                next()
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
    }

    override fun close() {
        synchronized(lock) { closed = true }
    }

    companion object {
        fun background(name: String, onFailure: (Throwable) -> Unit): Managed {
            val executor = Executors.newSingleThreadExecutor { job ->
                Thread(job, name).apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }
            return Managed(executor, onFailure)
        }
    }

    class Managed internal constructor(
        private val ownedExecutor: java.util.concurrent.ExecutorService,
        onFailure: (Throwable) -> Unit,
    ) : Closeable {
        private val delegate = CoalescingSerialWorker(ownedExecutor, onFailure)

        fun submitLatest(job: () -> Unit): Boolean = delegate.submitLatest(job)

        override fun close() {
            delegate.close()
            ownedExecutor.shutdown()
        }
    }
}
