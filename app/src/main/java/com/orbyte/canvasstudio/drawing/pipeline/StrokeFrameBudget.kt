package com.orbyte.canvasstudio.drawing.pipeline

/**
 * Adaptive, bounded amount of raster work per UI animation frame.
 *
 * This is a scheduling hint, not a frame-time guarantee: callers must still
 * measure main-thread stalls and keep the document's authoritative updates
 * ordered. Only the UI thread owns an instance.
 */
internal class StrokeFrameBudget(
    private val targetNanos: Long = 3_000_000L,
    private val initialSegments: Int = 12,
    private val maxSegments: Int = 32,
) {
    private var estimatedNanosPerSegment = 0L

    init {
        require(targetNanos > 0L)
        require(initialSegments in 1..maxSegments)
    }

    fun nextSegments(pending: Int): Int {
        if (pending <= 0) return 0
        val suggestion = if (estimatedNanosPerSegment == 0L) initialSegments else {
            (targetNanos / estimatedNanosPerSegment).coerceIn(1L, maxSegments.toLong()).toInt()
        }
        return suggestion.coerceIn(1, maxSegments).coerceAtMost(pending)
    }

    fun record(segments: Int, elapsedNanos: Long) {
        if (segments <= 0 || elapsedNanos <= 0L) return
        val observation = (elapsedNanos / segments).coerceAtLeast(1L)
        estimatedNanosPerSegment = if (estimatedNanosPerSegment == 0L) {
            observation
        } else {
            // Stable EWMA. Avoid multiplication overflow if a debugger pauses a frame.
            estimatedNanosPerSegment +
                (observation - estimatedNanosPerSegment) / 4L
        }
    }
}
