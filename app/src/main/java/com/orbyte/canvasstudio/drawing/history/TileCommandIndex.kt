package com.orbyte.canvasstudio.drawing.history

import com.orbyte.canvasstudio.drawing.TileStorage

/** In-memory reverse index only; project tiles remain the persistence authority. */
internal data class HistorySurfaceKey(val layerId: String, val target: HistoryRasterTarget)
internal enum class HistoryRasterTarget { CONTENT, MASK }

internal class TileCommandIndex {
    private data class Entry(val commandId: String, val historyPosition: Int, val replayCost: Int, val order: Long)
    private val byTile = mutableMapOf<Pair<HistorySurfaceKey, TileStorage.Key>, MutableList<Entry>>()
    private val byCommand = mutableMapOf<String, Map<HistorySurfaceKey, Set<TileStorage.Key>>>()
    private val positions = mutableMapOf<String, Int>()
    private val costs = mutableMapOf<String, Int>()
    private var nextOrder = 0L

    fun register(commandId: String, historyPosition: Int, affected: Map<HistorySurfaceKey, Set<TileStorage.Key>>, replayCost: Int) {
        check(commandId !in byCommand) { "Duplicate history command: $commandId" }
        byCommand[commandId] = affected
        positions[commandId] = historyPosition
        costs[commandId] = replayCost
        affected.forEach { (surface, tiles) -> tiles.forEach { tile ->
            byTile.getOrPut(surface to tile) { mutableListOf() } += Entry(commandId, historyPosition, replayCost, nextOrder)
        } }
        nextOrder++
    }

    fun commandIdsFor(
        surface: HistorySurfaceKey,
        tiles: Set<TileStorage.Key>,
        fromExclusive: Int = -1,
        toInclusive: Int = Int.MAX_VALUE,
    ): List<String> =
        tiles.asSequence().flatMap { byTile[surface to it].orEmpty().asSequence() }
            .filter { it.historyPosition > fromExclusive && it.historyPosition <= toInclusive }
            .sortedWith(compareBy<Entry> { it.historyPosition }.thenBy { it.order })
            .map { it.commandId }.distinct().toList()

    fun affectedTilesFor(commandId: String): Map<HistorySurfaceKey, Set<TileStorage.Key>> = byCommand[commandId].orEmpty()

    fun truncateAfter(historyPosition: Int) =
        removeCommands(positions.filterValues { it > historyPosition }.keys.toSet())

    fun removeCommands(commandIds: Set<String>) {
        commandIds.forEach { id ->
            val affected = byCommand.remove(id).orEmpty()
            positions.remove(id)
            costs.remove(id)
            affected.forEach { (surface, tiles) -> tiles.forEach { tile ->
                byTile[surface to tile]?.let { entries ->
                    entries.removeAll { it.commandId == id }
                    if (entries.isEmpty()) byTile.remove(surface to tile)
                }
            } }
        }
    }

    fun removeSurface(surface: HistorySurfaceKey) {
        val affected = byCommand.filterValues { surface in it }.keys.toList()
        affected.forEach { id ->
            val maps = byCommand[id].orEmpty()
            maps[surface].orEmpty().forEach { tile ->
                byTile[surface to tile]?.let { entries ->
                    entries.removeAll { it.commandId == id }
                    if (entries.isEmpty()) byTile.remove(surface to tile)
                }
            }
            val remaining = maps - surface
            if (remaining.isEmpty()) {
                byCommand.remove(id)
                positions.remove(id)
                costs.remove(id)
            } else {
                byCommand[id] = remaining
            }
        }
    }

    fun replayCostFor(commandIds: Collection<String>): Int = commandIds.sumOf { costs[it] ?: 0 }
    fun clear() { byTile.clear(); byCommand.clear(); positions.clear(); costs.clear(); nextOrder = 0L }
    fun entryCount(): Int = byTile.values.sumOf { it.size }
}
