package com.orbyte.canvasstudio.drawing.performance

import android.os.Trace
import com.orbyte.canvasstudio.drawing.SparseTileSurface

/**
 * Local, debug-only renderer counters. This intentionally has no persistence, networking or
 * allocation-heavy frame recording: it is a small baseline tool for real-device profiling.
 */
class DrawingPerformanceMetrics(
    private val debugBuild: Boolean,
) {
    data class Snapshot(
        val motionEvents: Long,
        val historicalSamples: Long,
        val acceptedSamples: Long,
        val discardedSamples: Long,
        val generatedDabs: Long,
        val tilesTouched: Long,
        val cacheHits: Long,
        val cacheMisses: Long,
        val residentTiles: Int,
        val residentBytes: Long,
        val dirtyTiles: Int,
        val evictions: Long,
        val diskLoads: Long,
        val commandsReplayed: Long,
        val tilesRebuilt: Long,
        val visibleTileMisses: Long,
        val prefetchedTiles: Long,
        val inputToPreviewNanos: Long,
        val inputToCommitNanos: Long,
        val brushEvaluationNanos: Long,
        val tileRasterNanos: Long,
        val historyReplayNanos: Long,
        val viewportFrameNanos: Long,
        val tilePrefetchNanos: Long,
        val saveNanos: Long,
        val indexQueries: Long, val indexFallbacks: Long, val indexEntryCount: Long, val commandsExamined: Long,
        val checkpointHits: Long, val checkpointMisses: Long, val checkpointCreates: Long,
        val checkpointEvictions: Long, val checkpointRestoreNanos: Long, val checkpointCreateNanos: Long,
        val checkpointBytes: Long, val checkpointBudgetBytes: Long, val commandsAfterCheckpoint: Long,
        val frameTimeP50Nanos: Long,
        val frameTimeP95Nanos: Long,
        val frameTimeP99Nanos: Long,
        val dabsPerSecond: Double,
        val rendererFallbacks: Long,
    )

    var enabled: Boolean = false

    val isActive: Boolean
        get() = debugBuild && enabled

    private var motionEvents = 0L
    private var historicalSamples = 0L
    private var acceptedSamples = 0L
    private var discardedSamples = 0L
    private var generatedDabs = 0L
    private var tilesTouched = 0L
    private var cacheHits = 0L
    private var cacheMisses = 0L
    private var residentTiles = 0
    private var residentBytes = 0L
    private var dirtyTiles = 0
    private var evictions = 0L
    private var diskLoads = 0L
    private var commandsReplayed = 0L
    private var tilesRebuilt = 0L
    private var visibleTileMisses = 0L
    private var prefetchedTiles = 0L
    private var inputToPreviewNanos = 0L
    private var inputToCommitNanos = 0L
    private var brushEvaluationNanos = 0L
    private var tileRasterNanos = 0L
    private var historyReplayNanos = 0L
    private var viewportFrameNanos = 0L
    private var tilePrefetchNanos = 0L
    private var saveNanos = 0L
    private var indexQueries = 0L; private var indexFallbacks = 0L; private var indexEntryCount = 0L; private var commandsExamined = 0L
    private var checkpointHits = 0L; private var checkpointMisses = 0L; private var checkpointCreates = 0L
    private var checkpointEvictions = 0L; private var checkpointRestoreNanos = 0L; private var checkpointCreateNanos = 0L
    private var checkpointBytes = 0L; private var checkpointBudgetBytes = 0L; private var commandsAfterCheckpoint = 0L
    private val frameTimes = LongArray(240)
    private var frameTimeCount = 0
    private var frameTimeCursor = 0
    private var rendererFallbacks = 0L

    fun reset() = guarded {
        motionEvents = 0L
        historicalSamples = 0L
        acceptedSamples = 0L
        discardedSamples = 0L
        generatedDabs = 0L
        tilesTouched = 0L
        cacheHits = 0L
        cacheMisses = 0L
        residentTiles = 0
        residentBytes = 0L
        dirtyTiles = 0
        evictions = 0L
        diskLoads = 0L
        commandsReplayed = 0L
        tilesRebuilt = 0L
        visibleTileMisses = 0L
        prefetchedTiles = 0L
        inputToPreviewNanos = 0L
        inputToCommitNanos = 0L
        brushEvaluationNanos = 0L
        tileRasterNanos = 0L
        historyReplayNanos = 0L
        viewportFrameNanos = 0L
        tilePrefetchNanos = 0L
        saveNanos = 0L
        indexQueries = 0L; indexFallbacks = 0L; indexEntryCount = 0L; commandsExamined = 0L
        checkpointHits = 0L; checkpointMisses = 0L; checkpointCreates = 0L; checkpointEvictions = 0L
        checkpointRestoreNanos = 0L; checkpointCreateNanos = 0L; checkpointBytes = 0L
        checkpointBudgetBytes = 0L; commandsAfterCheckpoint = 0L
        frameTimeCount = 0; frameTimeCursor = 0; rendererFallbacks = 0L
    }

    fun recordMotionEvent(historySize: Int) = guarded {
        motionEvents += 1
        historicalSamples += historySize.coerceAtLeast(0)
    }

    fun recordSample(accepted: Boolean) = guarded {
        if (accepted) acceptedSamples += 1 else discardedSamples += 1
    }

    fun recordDabs(count: Int) = guarded { generatedDabs += count.coerceAtLeast(0) }
    fun recordTilesTouched(count: Int) = guarded { tilesTouched += count.coerceAtLeast(0) }
    fun recordVisibleMiss() = guarded { visibleTileMisses += 1 }
    fun recordPrefetch(count: Int) = guarded { prefetchedTiles += count.coerceAtLeast(0) }
    fun recordReplay(commandCount: Int, tileCount: Int) = guarded {
        commandsReplayed += commandCount.coerceAtLeast(0)
        tilesRebuilt += tileCount.coerceAtLeast(0)
    }

    internal fun updateTileStats(stats: Collection<SparseTileSurface.Stats>) = guarded {
        residentTiles = stats.sumOf { it.residentTiles }
        residentBytes = stats.sumOf { it.cacheBytes }
        dirtyTiles = stats.sumOf { it.dirtyTiles }
        evictions = stats.sumOf { it.evictions }
        diskLoads = stats.sumOf { it.diskLoads }
        // A tile absent from memory is a cache miss; loaded tiles are the only reliable count
        // currently exposed by SparseTileSurface. Keep the distinction explicit for Phase 1.
        cacheMisses = diskLoads
        cacheHits = (tilesTouched - cacheMisses).coerceAtLeast(0L)
    }

    fun <T> trace(section: String, block: () -> T): T {
        if (!isEnabled()) return block()
        Trace.beginSection("CanvasStudio/$section")
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun addInputToPreview(nanos: Long) = guarded { inputToPreviewNanos += nanos.coerceAtLeast(0) }
    fun addInputToCommit(nanos: Long) = guarded { inputToCommitNanos += nanos.coerceAtLeast(0) }
    fun addBrushEvaluation(nanos: Long) = guarded { brushEvaluationNanos += nanos.coerceAtLeast(0) }
    fun addTileRaster(nanos: Long) = guarded { tileRasterNanos += nanos.coerceAtLeast(0) }
    fun addHistoryReplay(nanos: Long) = guarded { historyReplayNanos += nanos.coerceAtLeast(0) }
    fun addViewportFrame(nanos: Long) = guarded {
        val safe = nanos.coerceAtLeast(0)
        viewportFrameNanos += safe
        frameTimes[frameTimeCursor] = safe
        frameTimeCursor = (frameTimeCursor + 1) % frameTimes.size
        frameTimeCount = (frameTimeCount + 1).coerceAtMost(frameTimes.size)
    }
    fun addTilePrefetch(nanos: Long) = guarded { tilePrefetchNanos += nanos.coerceAtLeast(0) }
    fun addSave(nanos: Long) = guarded { saveNanos += nanos.coerceAtLeast(0) }
    fun recordIndexQuery(examined: Int, entries: Int) = guarded { indexQueries++; commandsExamined += examined; indexEntryCount = entries.toLong() }
    fun recordIndexFallback() = guarded { indexFallbacks++ }
    fun recordRendererFallback() = guarded { rendererFallbacks++ }
    fun recordCheckpoint(hit: Boolean, commands: Int, restoreNanos: Long) = guarded {
        if (hit) checkpointHits++ else checkpointMisses++
        commandsAfterCheckpoint += commands.coerceAtLeast(0)
        checkpointRestoreNanos += restoreNanos.coerceAtLeast(0)
    }
    fun recordCheckpointCreate(nanos: Long) = guarded { checkpointCreates++; checkpointCreateNanos += nanos.coerceAtLeast(0) }
    fun updateCheckpointStats(bytes: Long, budget: Long, evictions: Long) = guarded {
        checkpointBytes = bytes; checkpointBudgetBytes = budget; checkpointEvictions = evictions
    }

    fun snapshot(): Snapshot {
        val sortedFrames = frameTimes.copyOf(frameTimeCount).apply { sort() }
        fun percentile(value: Float): Long = if (sortedFrames.isEmpty()) 0L else {
            sortedFrames[((sortedFrames.lastIndex * value).toInt()).coerceIn(0, sortedFrames.lastIndex)]
        }
        return Snapshot(
        motionEvents, historicalSamples, acceptedSamples, discardedSamples, generatedDabs,
        tilesTouched, cacheHits, cacheMisses, residentTiles, residentBytes, dirtyTiles,
        evictions, diskLoads, commandsReplayed, tilesRebuilt, visibleTileMisses,
        prefetchedTiles, inputToPreviewNanos, inputToCommitNanos, brushEvaluationNanos,
        tileRasterNanos, historyReplayNanos, viewportFrameNanos, tilePrefetchNanos, saveNanos,
        indexQueries, indexFallbacks, indexEntryCount, commandsExamined,
        checkpointHits, checkpointMisses, checkpointCreates, checkpointEvictions,
        checkpointRestoreNanos, checkpointCreateNanos, checkpointBytes, checkpointBudgetBytes,
        commandsAfterCheckpoint,
        percentile(.5f), percentile(.95f), percentile(.99f),
        if (tileRasterNanos > 0L) generatedDabs * 1_000_000_000.0 / tileRasterNanos else 0.0,
        rendererFallbacks,
    )
    }

    private inline fun guarded(block: () -> Unit) {
        if (isEnabled()) block()
    }

    private fun isEnabled(): Boolean = isActive
}
