package com.orbyte.canvasstudio.drawing.pipeline

import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * A strict, single-flight stroke transaction lane.
 *
 * Unlike a generic serial executor, the next prepare NEVER starts until the
 * preceding result has been applied to the document on its owner thread.
 * This ensures that the second stroke snapshots the first stroke's pixels.
 *
 * The caller supplies a detached result (AutoCloseable), a completion callback
 * which must synchronously publish or fall back to the authoritative CPU path,
 * and the owner-thread dispatcher. Only the caller may modify history/undo/save.
 *
 * flushSynchronously() is called on the document owner thread before an
 * operation which cannot wait for pending strokes (undo, save, layer deletion,
 * next pointer down). It commits every pending stroke in FIFO order through
 * its synchronous fallback. Any worker already running becomes stale; its
 * eventual bitmap result will only be closed, never published twice.
 *
 * All enqueue/flush/close methods must be called from the same owner thread.
 */
internal class StrokeCommitLane<R : Closeable>(
    private val worker: Executor,
    private val publishOnOwner: (Runnable) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : Closeable {
    private class Task<R : Closeable>(
        val prepare: () -> R?,
        val finish: (R?) -> Unit,
    )

    private val queue = ArrayDeque<Task<R>>()
    private var active: Task<R>? = null
    private var generation = 0L
    private var closed = false

    val hasPending: Boolean get() = active != null || queue.isNotEmpty()
    val pendingCount: Int get() = queue.size + if (active == null) 0 else 1

    fun enqueue(prepare: () -> R?, finish: (R?) -> Unit): Boolean {
        if (closed) return false
        queue.addLast(Task(prepare, finish))
        startNext()
        return true
    }

    /**
     * Clears all pending transactions in order on the owner thread. The
     * caller's finish(null) must invoke its safe synchronous raster fallback.
     */
    fun flushSynchronously() {
        if (!hasPending) return
        generation++
        val unfinished = ArrayList<Task<R>>(pendingCount)
        active?.let(unfinished::add)
        active = null
        while (queue.isNotEmpty()) unfinished += queue.removeFirst()
        unfinished.forEach { task ->
            try {
                task.finish(null)
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
    }

    private fun startNext() {
        if (closed || active != null || queue.isEmpty()) return
        val task = queue.removeFirst()
        active = task
        val ticket = generation
        try {
            worker.execute {
                val result = runCatching { task.prepare() }
                publishOnOwner(Runnable { complete(task, ticket, result) })
            }
        } catch (error: Throwable) {
            publishOnOwner(Runnable { complete(task, ticket, Result.failure(error)) })
        }
    }

    private fun complete(task: Task<R>, ticket: Long, outcome: Result<R?>) {
        try {
            if (!closed && generation == ticket && active === task) {
                try {
                    task.finish(outcome.getOrNull())
                    outcome.exceptionOrNull()?.let(onFailure)
                } catch (error: Throwable) {
                    onFailure(error)
                }
            }
        } finally {
            try {
                outcome.getOrNull()?.close()
            } catch (error: Throwable) {
                onFailure(error)
            }
            if (active === task) {
                active = null
                startNext()
            }
        }
    }

    override fun close() {
        flushSynchronously()
        closed = true
        generation++
    }
}
