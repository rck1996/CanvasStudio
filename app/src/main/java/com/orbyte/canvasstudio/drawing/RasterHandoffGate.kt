package com.orbyte.canvasstudio.drawing

/**
 * Keeps low-latency preview items alive until the backing raster has been presented for a
 * configurable number of frames. New completions restart the countdown so a burst of strokes
 * cannot expose a gap between the front buffer and the tiled document.
 */
internal class RasterHandoffGate<T>(
    private val presentationFrames: Int,
) {
    private val pending = linkedSetOf<T>()
    private var framesRemaining = 0

    init {
        require(presentationFrames > 0)
    }

    fun enqueue(items: Collection<T>) {
        if (items.isEmpty()) return
        pending += items
        framesRemaining = presentationFrames
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun onRasterFramePresented(): Set<T>? {
        if (pending.isEmpty()) return null
        framesRemaining -= 1
        if (framesRemaining > 0) return null
        return pending.toSet().also {
            pending.clear()
            framesRemaining = 0
        }
    }
}
