package com.orbyte.canvasstudio.drawing.history

import android.graphics.Bitmap
import com.orbyte.canvasstudio.drawing.TileStorage

internal data class TileCheckpointKey(
    val surface: HistorySurfaceKey,
    val tile: TileStorage.Key,
    val historyPosition: Int,
)

internal data class TileCheckpointRestore(
    val key: TileCheckpointKey,
    /** Caller owns this immutable-store copy and must recycle it. Null means transparent tile. */
    val bitmap: Bitmap?,
)

internal data class TileCheckpointStats(
    val bytes: Long,
    val budgetBytes: Long,
    val entries: Int,
    val evictions: Long,
)

internal data class TileCheckpointPolicy(
    val commandThreshold: Int = 24,
    val replayCostThreshold: Int = 600,
    val replayTimeThresholdNanos: Long = 8_000_000L,
) {
    fun shouldCreate(commands: Int, replayCost: Int, replayNanos: Long): Boolean =
        commands >= commandThreshold || replayCost >= replayCostThreshold || replayNanos >= replayTimeThresholdNanos
}

/** Strict-budget, access-ordered in-memory store. Checkpoints never enter project persistence. */
internal class TileCheckpointStore(
    private val budgetBytes: Long,
    private val maxEntries: Int = (budgetBytes / 4096L).toInt().coerceIn(16, 512),
) {
    private data class Stored(val bitmap: Bitmap?, val bytes: Long)
    private val entries = LinkedHashMap<TileCheckpointKey, Stored>(16, .75f, true)
    private var bytes = 0L
    private var evictions = 0L

    fun findNearest(surface: HistorySurfaceKey, tile: TileStorage.Key, atOrBeforePosition: Int): TileCheckpointRestore? {
        val key = entries.keys
            .filter { it.surface == surface && it.tile == tile && it.historyPosition <= atOrBeforePosition }
            .maxByOrNull { it.historyPosition } ?: return null
        val stored = entries[key] ?: return null // access updates LRU
        return TileCheckpointRestore(key, stored.bitmap?.copy(Bitmap.Config.ARGB_8888, false))
    }

    fun put(key: TileCheckpointKey, snapshot: Bitmap?) {
        val immutable = snapshot?.copy(Bitmap.Config.ARGB_8888, false)
        val size = immutable?.allocationByteCount?.toLong() ?: 0L
        if (size > budgetBytes) { immutable?.recycle(); return }
        entries.remove(key)?.let(::release)
        entries[key] = Stored(immutable, size)
        bytes += size
        trim()
    }

    fun invalidateAfter(historyPosition: Int) = removeWhere { it.historyPosition > historyPosition }
    fun removeSurface(surface: HistorySurfaceKey) = removeWhere { it.surface == surface }
    fun clear() = removeWhere { true }
    fun stats() = TileCheckpointStats(bytes, budgetBytes, entries.size, evictions)

    private fun trim() {
        while ((bytes > budgetBytes || entries.size > maxEntries) && entries.isNotEmpty()) {
            val eldest = entries.entries.iterator().next()
            entries.remove(eldest.key)
            release(eldest.value)
            evictions++
        }
    }

    private fun removeWhere(predicate: (TileCheckpointKey) -> Boolean) {
        entries.keys.filter(predicate).toList().forEach { key -> entries.remove(key)?.let(::release) }
    }

    private fun release(stored: Stored) {
        bytes = (bytes - stored.bytes).coerceAtLeast(0L)
        stored.bitmap?.recycle()
    }
}
