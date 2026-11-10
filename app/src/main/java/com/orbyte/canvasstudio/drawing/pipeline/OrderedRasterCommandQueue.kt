package com.orbyte.canvasstudio.drawing.pipeline

import java.io.Closeable
import java.util.concurrent.Executor

/**
 * Ordered prepare/publish pipeline for raster-adjacent work.
 *
 * Worker jobs may read immutable commands or thread-safe sparse surfaces, but
 * must not touch DrawingView/Compose state. Publication runs on the UI executor,
 * in submission order. A barrier runs only after all earlier publications.
 *
 * This class does NOT make arbitrary View-bound brush rendering thread-safe.
 * A future authoritative backend must produce detached tile results and apply
 * them inside publish, with explicit failure and cancellation policies.
 */
internal class OrderedRasterCommandQueue(
    private val worker: Executor,
    private val publishOnMain: (Runnable) -> Unit,
) : Closeable {
    private val lock = Any()
    private var generation = 0L
    private var submitted = 0L
    private var published = 0L
    private var closed = false
    private val barriers = ArrayDeque<Barrier>()

    private data class Barrier(val generation: Long, val afterSequence: Long, val action: () -> Unit)

    val hasPending: Boolean get() = synchronized(lock) { submitted > published }

    /**
     * Main-thread callback uses detached result data, never mutable scratch.
     * discard is called for a stale/closed result to free resources.
     */
    fun <T> enqueue(
        prepare: () -> T,
        commit: (T) -> Unit,
        discard: (T) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ): Boolean {
        val ticket = synchronized(lock) {
            if (closed) return false
            ++submitted to generation
        }
        try {
            worker.execute {
                val result = runCatching(prepare)
                publishOnMain(Runnable {
                    val active = synchronized(lock) { !closed && generation == ticket.second }
                    try {
                        if (active) {
                            result.fold(commit, onError)
                        } else {
                            result.getOrNull()?.let(discard)
                        }
                    } finally {
                        publishThrough(ticket.first)
                    }
                })
            }
        } catch (error: RuntimeException) {
            publishOnMain(Runnable {
                val active = synchronized(lock) { !closed && generation == ticket.second }
                try {
                    if (active) onError(error)
                } finally {
                    publishThrough(ticket.first)
                }
            })
        }
        return true
    }

    /** A nonblocking barrier after previously submitted publications. */
    fun afterPrior(action: () -> Unit) {
        val immediate = synchronized(lock) {
            if (closed) return
            if (published == submitted) {
                true
            } else {
                barriers.addLast(Barrier(generation, submitted, action))
                false
            }
        }
        if (immediate) action()
    }

    /** Cancels stale publications and their barriers, not a currently running worker. */
    fun invalidate() = synchronized(lock) {
        generation++
        barriers.clear()
        // Older callbacks are discarded after returning to main.
        published = submitted
    }

    override fun close() = synchronized(lock) {
        closed = true
        generation++
        barriers.clear()
        published = submitted
    }

    private fun publishThrough(sequence: Long) {
        val ready = mutableListOf<Barrier>()
        synchronized(lock) {
            if (sequence > published) published = sequence
            while (barriers.isNotEmpty() && barriers.first().afterSequence <= published) {
                ready += barriers.removeFirst()
            }
        }
        ready.forEach { barrier ->
            val active = synchronized(lock) { !closed && barrier.generation == generation }
            if (active) barrier.action()
        }
    }
}
