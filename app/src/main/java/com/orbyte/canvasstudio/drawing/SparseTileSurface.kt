package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.max

/**
 * Sparse, disk-backed raster surface.
 *
 * Only tiles that are currently visible or being edited are decoded. Resident tiles are kept in an
 * access-ordered LRU cache. Modified tiles are flushed to the session directory before eviction, so
 * a large document does not need a full-size bitmap per layer in memory.
 */
internal class SparseTileSurface(
    val width: Int,
    val height: Int,
    val workingDirectory: File,
    cacheBudgetBytes: Long,
) {
    data class SaveSnapshot(
        val dirtyVersions: Map<TileStorage.Key, Long>,
        val deletedVersions: Map<TileStorage.Key, Long>,
    )

    data class Stats(
        val residentTiles: Int,
        val storedTiles: Int,
        val dirtyTiles: Int,
        val cacheBytes: Long,
        val evictions: Long,
        val diskLoads: Long,
    )

    private data class PendingFlush(
        val key: TileStorage.Key,
        val bitmap: Bitmap,
        val version: Long,
    )

    private val lock = Any()
    private val cache = LinkedHashMap<TileStorage.Key, Bitmap>(16, 0.75f, true)
    private val knownKeys = TileStorage.existingKeys(workingDirectory).toMutableSet()
    private val pendingWriteKeys = mutableSetOf<TileStorage.Key>()
    private val dirtyVersions = mutableMapOf<TileStorage.Key, Long>()
    private val deletedVersions = mutableMapOf<TileStorage.Key, Long>()
    private var revision = 0L
    private var cacheBudgetBytes = cacheBudgetBytes.coerceAtLeast(MIN_CACHE_BYTES)
    private var evictions = 0L
    private var diskLoads = 0L
    private var residentBytes = 0L
    private val renderDestination = RectF()
    // Rendering deliberately never decodes PNGs on the UI thread. Keep tiles from the last
    // rendered viewport resident so an autosave/cache trim cannot turn visible strokes blank.
    private val retainedVisibleBounds = RectF()

    init {
        workingDirectory.mkdirs()
    }

    fun setCacheBudget(bytes: Long) = synchronized(lock) {
        cacheBudgetBytes = bytes.coerceAtLeast(MIN_CACHE_BYTES)
        trimCache(emptySet())
    }

    fun draw(bounds: RectF, block: (Canvas) -> Unit): Boolean = synchronized(lock) {
        val keys = TileStorage.keysForBounds(bounds, width, height)
        if (keys.isEmpty()) return@synchronized false
        keys.forEach { key ->
            val bitmap = obtainTile(key, createIfMissing = true) ?: return@forEach
            val rect = TileStorage.tileRect(key, width, height)
            val tileCanvas = Canvas(bitmap)
            tileCanvas.save()
            tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
            tileCanvas.clipRect(
                RectF(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.right.toFloat(),
                    rect.bottom.toFloat(),
                ),
            )
            block(tileCanvas)
            tileCanvas.restore()
            markModified(key)
        }
        trimCache(emptySet())
        true
    }

    /**
     * Variant used by command replay so the renderer can reject segments that do
     * not intersect the current tile instead of replaying a long stroke in full.
     */
    fun drawPerTile(bounds: RectF, block: (Canvas, RectF) -> Unit): Boolean = synchronized(lock) {
        val keys = TileStorage.keysForBounds(bounds, width, height)
        if (keys.isEmpty()) return@synchronized false
        keys.forEach { key ->
            val bitmap = obtainTile(key, createIfMissing = true) ?: return@forEach
            val rect = TileStorage.tileRect(key, width, height)
            val tileBounds = RectF(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
            )
            val tileCanvas = Canvas(bitmap)
            tileCanvas.save()
            tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
            tileCanvas.clipRect(tileBounds)
            block(tileCanvas, tileBounds)
            tileCanvas.restore()
            markModified(key)
        }
        trimCache(emptySet())
        true
    }

    /** Draws while preserving the alpha that already exists in each affected tile. */
    fun drawPreservingAlpha(bounds: RectF, block: (Canvas) -> Unit): Boolean = synchronized(lock) {
        val keys = TileStorage.keysForBounds(bounds, width, height)
        if (keys.isEmpty()) return@synchronized false
        keys.forEach { key ->
            val bitmap = obtainTile(key, createIfMissing = false) ?: return@forEach
            val rect = TileStorage.tileRect(key, width, height)
            val temporary = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                val temporaryCanvas = Canvas(temporary)
                temporaryCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
                temporaryCanvas.clipRect(
                    RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
                )
                block(temporaryCanvas)
                val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                }
                Canvas(bitmap).drawBitmap(temporary, 0f, 0f, alphaPaint)
                alphaPaint.xfermode = null
                markModified(key)
            } finally {
                temporary.recycle()
            }
        }
        trimCache(emptySet())
        true
    }

    /**
     * Supplies one mutable ARGB buffer per affected tile to a non-Canvas raster backend.
     * Pixel ownership never escapes this call and modified tiles retain normal cache/save semantics.
     */
    fun mutateTilePixels(
        bounds: RectF,
        createIfMissing: Boolean,
        block: (TileStorage.Key, Rect, IntArray) -> Boolean,
    ): Int = synchronized(lock) {
        var changedTiles = 0
        TileStorage.keysForBounds(bounds, width, height).forEach { key ->
            val bitmap = obtainTile(key, createIfMissing) ?: return@forEach
            val rect = TileStorage.tileRect(key, width, height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            if (block(key, rect, pixels)) {
                bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                markModified(key)
                changedTiles += 1
            }
        }
        trimCache(emptySet())
        changedTiles
    }

    /**
     * Draws only resident tiles. Disk decoding is intentionally excluded from the UI path; missing
     * tiles are requested by [prefetch] and appear on the following frame instead of stalling input.
     */
    fun drawVisible(target: Canvas, visibleBounds: RectF, paint: Paint?) = synchronized(lock) {
        retainedVisibleBounds.set(visibleBounds)
        TileStorage.forEachKeyInBounds(visibleBounds, width, height) { key ->
            val bitmap = cache[key] ?: return@forEachKeyInBounds
            if (bitmap.isRecycled) return@forEachKeyInBounds
            val rect = TileStorage.tileRect(key, width, height)
            // Fractional canvas zoom can rasterize two mathematically adjacent quads with a
            // sub-pixel gap. Bleed half a document pixel on every side so neighbouring tiles
            // overlap at the compositor; this is display-only and never changes persisted PNGs.
            renderDestination.set(
                rect.left - TILE_EDGE_BLEED_PX,
                rect.top - TILE_EDGE_BLEED_PX,
                rect.right + TILE_EDGE_BLEED_PX,
                rect.bottom + TILE_EDGE_BLEED_PX,
            )
            target.drawBitmap(bitmap, null, renderDestination, paint)
        }
    }

    /**
     * Indicates that a tile which exists on disk is absent from the resident cache. The caller can
     * then schedule a background prefetch; the UI renderer must never decode a PNG synchronously.
     */
    fun hasMissingVisibleTiles(bounds: RectF): Boolean = synchronized(lock) {
        var missing = false
        TileStorage.forEachKeyInBounds(bounds, width, height) { key ->
            if (key in knownKeys && (cache[key] == null || cache[key]?.isRecycled == true)) {
                missing = true
            }
        }
        missing
    }

    /** Decodes nearby stored tiles without holding the surface lock during disk I/O. */
    fun prefetch(bounds: RectF): Int {
        val keys = TileStorage.keysForBounds(bounds, width, height)
        var loaded = 0
        keys.forEach { key ->
            val shouldLoad = synchronized(lock) { key in knownKeys && key !in cache }
            if (!shouldLoad) return@forEach
            val file = File(workingDirectory, key.fileName)
            val decoded = (if (file.isFile) TileStorage.loadTile(file) else null) ?: return@forEach
            synchronized(lock) {
                val existing = cache[key]
                if (existing == null) {
                    cache[key] = decoded
                    residentBytes += bitmapBytes(decoded)
                    diskLoads += 1L
                    loaded += 1
                    trimCache(setOf(key))
                } else {
                    decoded.recycle()
                }
            }
        }
        synchronized(lock) { trimCache(emptySet()) }
        return loaded
    }

    fun drawAll(target: Canvas, paint: Paint?) = synchronized(lock) {
        allCurrentKeys().sortedWith(compareBy<TileStorage.Key> { it.row }.thenBy { it.column })
            .forEach { key ->
                val bitmap = obtainTile(key, createIfMissing = false) ?: return@forEach
                val rect = TileStorage.tileRect(key, width, height)
                target.drawBitmap(bitmap, rect.left.toFloat(), rect.top.toFloat(), paint)
            }
        trimCache(emptySet())
    }

    fun drawAtPoint(target: Canvas, x: Float, y: Float, paint: Paint?) = synchronized(lock) {
        if (x < 0f || x >= width.toFloat() || y < 0f || y >= height.toFloat()) return@synchronized
        val key = TileStorage.Key(
            column = (x / TileStorage.TILE_SIZE).toInt(),
            row = (y / TileStorage.TILE_SIZE).toInt(),
        )
        val bitmap = obtainTile(key, createIfMissing = false) ?: return@synchronized
        val rect = TileStorage.tileRect(key, width, height)
        target.drawBitmap(bitmap, rect.left - x, rect.top - y, paint)
        trimCache(setOf(key))
    }

    /** Samples one document-space pixel without allocating a temporary bitmap. */
    fun samplePixel(x: Float, y: Float): Int? = synchronized(lock) {
        if (x < 0f || x >= width.toFloat() || y < 0f || y >= height.toFloat()) {
            return@synchronized null
        }
        val key = TileStorage.Key(
            column = (x / TileStorage.TILE_SIZE).toInt(),
            row = (y / TileStorage.TILE_SIZE).toInt(),
        )
        val bitmap = obtainTile(key, createIfMissing = false) ?: return@synchronized null
        val rect = TileStorage.tileRect(key, width, height)
        val localX = (x.toInt() - rect.left).coerceIn(0, bitmap.width - 1)
        val localY = (y.toInt() - rect.top).coerceIn(0, bitmap.height - 1)
        bitmap.getPixel(localX, localY)
    }

    fun renderBitmap(maxPixels: Long): Bitmap = synchronized(lock) {
        val pixels = width.toLong() * height.toLong()
        require(pixels <= maxPixels) { "La operación supera el límite seguro de memoria." }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawAll(Canvas(bitmap), null)
        bitmap
    }

    fun extract(bounds: RectF, clipPath: Path? = null): Bitmap? = synchronized(lock) {
        val clipped = clippedRect(bounds) ?: return@synchronized null
        val requiredBytes = clipped.width().toLong() * clipped.height().toLong() * 4L
        require(requiredBytes <= MAX_SELECTION_BYTES) {
            "La selección es demasiado grande para transformarla en este dispositivo."
        }
        val bitmap = Bitmap.createBitmap(clipped.width(), clipped.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-clipped.left.toFloat(), -clipped.top.toFloat())
        val keys = TileStorage.keysForBounds(
            RectF(clipped.left.toFloat(), clipped.top.toFloat(), clipped.right.toFloat(), clipped.bottom.toFloat()),
            width,
            height,
        )
        keys.forEach { key ->
            val tile = obtainTile(key, createIfMissing = false) ?: return@forEach
            val tileRect = TileStorage.tileRect(key, width, height)
            canvas.drawBitmap(tile, tileRect.left.toFloat(), tileRect.top.toFloat(), null)
        }
        if (clipPath != null) {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawPath(clipPath, maskPaint)
            maskPaint.xfermode = null
        }
        trimCache(keys)
        bitmap
    }

    fun clearPath(bounds: RectF, path: Path): Boolean = draw(bounds) { canvas ->
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawPath(path, clearPaint)
        clearPaint.xfermode = null
    }

    fun drawBitmap(bounds: RectF, bitmap: Bitmap, matrix: Matrix, preserveAlpha: Boolean = false): Boolean {
        val operation: (Canvas) -> Unit = { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        return if (preserveAlpha) drawPreservingAlpha(bounds, operation) else draw(bounds, operation)
    }

    fun clearAll() = synchronized(lock) {
        val affected = allCurrentKeys()
        cache.values.forEach(Bitmap::recycle)
        cache.clear()
        residentBytes = 0L
        pendingWriteKeys.clear()
        knownKeys.clear()
        workingDirectory.deleteRecursively()
        workingDirectory.mkdirs()
        affected.forEach { key ->
            revision += 1L
            dirtyVersions[key] = revision
            deletedVersions[key] = revision
        }
    }

    /** Restores the working raster while preserving project-dirty bookkeeping for undo/redo. */
    fun resetWorkingFrom(sourceDirectory: File?) = synchronized(lock) {
        cache.values.forEach(Bitmap::recycle)
        cache.clear()
        residentBytes = 0L
        pendingWriteKeys.clear()
        workingDirectory.deleteRecursively()
        workingDirectory.mkdirs()
        if (sourceDirectory?.isDirectory == true) {
            TileStorage.copyTileDirectory(sourceDirectory, workingDirectory)
        }
        knownKeys.clear()
        knownKeys += TileStorage.existingKeys(workingDirectory)
    }

    /** Restores only the affected tiles from the immutable base, avoiding full-layer rebuilds. */
    fun resetRegionFrom(sourceDirectory: File?, bounds: RectF) = synchronized(lock) {
        val keys = TileStorage.keysForBounds(bounds, width, height)
        keys.forEach { key ->
            cache.remove(key)?.let { bitmap ->
                residentBytes = (residentBytes - bitmapBytes(bitmap)).coerceAtLeast(0L)
                bitmap.recycle()
            }
            pendingWriteKeys.remove(key)
            val destination = File(workingDirectory, key.fileName)
            val source = sourceDirectory?.let { File(it, key.fileName) }
            val restored = if (source?.isFile == true) {
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
                true
            } else {
                destination.delete()
                false
            }
            revision += 1L
            dirtyVersions[key] = revision
            if (restored) {
                knownKeys += key
                deletedVersions.remove(key)
            } else {
                knownKeys.remove(key)
                deletedVersions[key] = revision
            }
        }
    }

    fun replaceFromBitmap(bitmap: Bitmap, markProjectDirty: Boolean) = synchronized(lock) {
        cache.values.forEach(Bitmap::recycle)
        cache.clear()
        residentBytes = 0L
        pendingWriteKeys.clear()
        workingDirectory.deleteRecursively()
        workingDirectory.mkdirs()
        knownKeys.clear()

        TileStorage.allKeys(width, height).forEach { key ->
            when (TileStorage.saveTileAtomically(bitmap, key, workingDirectory)) {
                TileStorage.WriteResult.WRITTEN -> knownKeys += key
                TileStorage.WriteResult.DELETED -> Unit
                TileStorage.WriteResult.FAILED -> error("No se pudo importar el tile ${key.fileName}")
            }
        }
        if (markProjectDirty) markAllProjectDirtyLocked()
    }

    /**
     * Persists modified tiles without keeping the surface lock during PNG encoding.
     *
     * Copying one 512 px tile is short; compression and fsync happen after releasing the lock, so
     * stylus input is not forced to wait for all pending tiles to reach disk.
     */
    fun flushPending(): Boolean {
        while (true) {
            val (snapshot, done) = synchronized(lock) {
                val key = pendingWriteKeys.firstOrNull()
                if (key == null) {
                    trimCache(emptySet())
                    null to true
                } else {
                    val source = cache[key]
                    if (source == null || source.isRecycled) {
                        pendingWriteKeys.remove(key)
                        null to false
                    } else {
                        val version = dirtyVersions[key] ?: run {
                            revision += 1L
                            dirtyVersions[key] = revision
                            revision
                        }
                        PendingFlush(
                            key = key,
                            bitmap = source.copy(Bitmap.Config.ARGB_8888, false),
                            version = version,
                        ) to false
                    }
                }
            }
            if (done) return true
            snapshot ?: continue

            val result = try {
                TileStorage.saveTileBitmapAtomically(snapshot.bitmap, snapshot.key, workingDirectory)
            } finally {
                snapshot.bitmap.recycle()
            }

            if (result == TileStorage.WriteResult.FAILED) return false

            synchronized(lock) {
                val unchanged = dirtyVersions[snapshot.key] == snapshot.version
                when (result) {
                    TileStorage.WriteResult.WRITTEN -> {
                        knownKeys += snapshot.key
                        if (unchanged) {
                            pendingWriteKeys.remove(snapshot.key)
                            deletedVersions.remove(snapshot.key)
                        }
                    }
                    TileStorage.WriteResult.DELETED -> {
                        knownKeys.remove(snapshot.key)
                        if (unchanged) {
                            pendingWriteKeys.remove(snapshot.key)
                            deletedVersions[snapshot.key] = snapshot.version
                        }
                    }
                    TileStorage.WriteResult.FAILED -> Unit
                }
                trimCache(pendingWriteKeys)
            }
        }
    }

    fun copyCurrentTo(destination: File): Boolean {
        if (!flushPending()) return false
        return TileStorage.copyTileDirectory(workingDirectory, destination)
    }

    fun markProjectDirty(bounds: RectF) = synchronized(lock) {
        TileStorage.keysForBounds(bounds, width, height).forEach { key ->
            revision += 1L
            dirtyVersions[key] = revision
            if (key in knownKeys || key in cache) deletedVersions.remove(key)
        }
    }

    fun markAllProjectDirty() = synchronized(lock) {
        markAllProjectDirtyLocked()
    }

    fun saveSnapshot(): SaveSnapshot {
        while (true) {
            check(flushPending()) { "No se pudieron preparar los tiles para guardar" }
            val snapshot = synchronized(lock) {
                if (pendingWriteKeys.isNotEmpty()) {
                    null
                } else {
                    val existingDirty = dirtyVersions.filterKeys { key ->
                        File(workingDirectory, key.fileName).isFile
                    }
                    val deleted = deletedVersions.toMap() + dirtyVersions
                        .filterKeys { key -> !File(workingDirectory, key.fileName).isFile }
                    SaveSnapshot(existingDirty, deleted)
                }
            }
            if (snapshot != null) return snapshot
        }
    }

    fun acknowledgeSave(snapshot: SaveSnapshot) = synchronized(lock) {
        snapshot.dirtyVersions.forEach { (key, version) ->
            if (dirtyVersions[key] == version) dirtyVersions.remove(key)
        }
        snapshot.deletedVersions.forEach { (key, version) ->
            if (dirtyVersions[key] == version) dirtyVersions.remove(key)
            if (deletedVersions[key] == version) deletedVersions.remove(key)
        }
    }

    fun fileFor(key: TileStorage.Key): File = File(workingDirectory, key.fileName)

    /** Returns a detached immutable tile snapshot. Null represents a transparent tile. */
    fun snapshotTile(key: TileStorage.Key): Bitmap? = synchronized(lock) {
        obtainTile(key, createIfMissing = false)?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Replaces exactly one tile from a detached checkpoint copy. */
    fun restoreTile(key: TileStorage.Key, snapshot: Bitmap?) = synchronized(lock) {
        cache.remove(key)?.let { residentBytes = (residentBytes - bitmapBytes(it)).coerceAtLeast(0L); it.recycle() }
        pendingWriteKeys.remove(key)
        val destination = fileFor(key)
        if (snapshot == null) {
            destination.delete(); knownKeys.remove(key)
        } else {
            val mutable = snapshot.copy(Bitmap.Config.ARGB_8888, true)
            cache[key] = mutable; residentBytes += bitmapBytes(mutable); knownKeys += key
        }
        pendingWriteKeys += key
        revision += 1L; dirtyVersions[key] = revision
        if (snapshot == null) deletedVersions[key] = revision else deletedVersions.remove(key)
        trimCache(setOf(key))
    }

    fun stats(): Stats = synchronized(lock) {
        Stats(
            residentTiles = cache.size,
            storedTiles = knownKeys.size,
            dirtyTiles = dirtyVersions.size,
            cacheBytes = residentBytes,
            evictions = evictions,
            diskLoads = diskLoads,
        )
    }

    fun recycle() {
        flushPending()
        synchronized(lock) {
            cache.values.forEach(Bitmap::recycle)
            cache.clear()
            residentBytes = 0L
            pendingWriteKeys.clear()
        }
    }

    private fun obtainTile(key: TileStorage.Key, createIfMissing: Boolean): Bitmap? {
        cache[key]?.let { return it }
        val file = File(workingDirectory, key.fileName)
        val decoded = if (file.isFile) TileStorage.loadTile(file) else null
        val bitmap = when {
            decoded != null -> {
                diskLoads += 1L
                decoded
            }
            createIfMissing -> {
                val rect = TileStorage.tileRect(key, width, height)
                Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            }
            else -> return null
        }
        cache[key] = bitmap
        residentBytes += bitmapBytes(bitmap)
        return bitmap
    }

    private fun markModified(key: TileStorage.Key) {
        revision += 1L
        dirtyVersions[key] = revision
        deletedVersions.remove(key)
        pendingWriteKeys += key
    }

    private fun markAllProjectDirtyLocked() {
        allCurrentKeys().forEach { key ->
            revision += 1L
            dirtyVersions[key] = revision
            deletedVersions.remove(key)
        }
    }

    private fun flushTile(key: TileStorage.Key, bitmap: Bitmap): Boolean {
        val version = dirtyVersions[key] ?: run {
            revision += 1L
            dirtyVersions[key] = revision
            revision
        }
        return when (TileStorage.saveTileBitmapAtomically(bitmap, key, workingDirectory)) {
            TileStorage.WriteResult.WRITTEN -> {
                knownKeys += key
                pendingWriteKeys.remove(key)
                deletedVersions.remove(key)
                true
            }
            TileStorage.WriteResult.DELETED -> {
                knownKeys.remove(key)
                pendingWriteKeys.remove(key)
                deletedVersions[key] = version
                true
            }
            TileStorage.WriteResult.FAILED -> false
        }
    }

    private fun trimCache(protectedKeys: Set<TileStorage.Key>) {
        if (residentBytes <= cacheBudgetBytes) return
        val iterator = cache.entries.iterator()
        while (iterator.hasNext() && residentBytes > cacheBudgetBytes) {
            val entry = iterator.next()
            if (entry.key in protectedKeys) continue
            if (isInRetainedVisibleBounds(entry.key)) continue
            // Never PNG-encode a dirty tile from the UI drawing path. Autosave flushes it later
            // without holding the lock; until then a dirty tile is temporarily protected.
            if (entry.key in pendingWriteKeys) continue
            residentBytes = (residentBytes - bitmapBytes(entry.value)).coerceAtLeast(0L)
            entry.value.recycle()
            iterator.remove()
            evictions += 1L
        }
    }

    private fun isInRetainedVisibleBounds(key: TileStorage.Key): Boolean {
        if (retainedVisibleBounds.isEmpty) return false
        val left = key.column * TileStorage.TILE_SIZE
        val top = key.row * TileStorage.TILE_SIZE
        return left < retainedVisibleBounds.right &&
            left + TileStorage.TILE_SIZE > retainedVisibleBounds.left &&
            top < retainedVisibleBounds.bottom &&
            top + TileStorage.TILE_SIZE > retainedVisibleBounds.top
    }

    private fun allCurrentKeys(): Set<TileStorage.Key> = buildSet {
        addAll(knownKeys)
        addAll(cache.keys)
        removeAll(deletedVersions.keys.filter { key -> key !in cache && key !in knownKeys }.toSet())
    }


    private fun bitmapBytes(bitmap: Bitmap): Long =
        max(1L, bitmap.width.toLong() * bitmap.height.toLong() * 4L)

    private fun clippedRect(bounds: RectF): Rect? {
        val left = bounds.left.toInt().coerceIn(0, width)
        val top = bounds.top.toInt().coerceIn(0, height)
        val right = kotlin.math.ceil(bounds.right.toDouble()).toInt().coerceIn(0, width)
        val bottom = kotlin.math.ceil(bounds.bottom.toDouble()).toInt().coerceIn(0, height)
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val MIN_CACHE_BYTES = 1L * 1024L * 1024L
        const val MAX_SELECTION_BYTES = 64L * 1024L * 1024L
        const val TILE_EDGE_BLEED_PX = 2f
    }
}
