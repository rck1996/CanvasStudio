package com.orbyte.canvasstudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bounded, off-main-thread preview decoding shared by gallery and version history.
 *
 * Full-resolution previews are never required for 260dp gallery cards. Keeping a small
 * bitmap cache avoids repeated PNG decompression when scrolling between projects.
 * Entries are NOT eagerly recycled on eviction: Compose might still be displaying one.
 */
internal object PreviewBitmapRepository {
    private const val CACHE_BYTES = 12 * 1024 * 1024
    // Striped locks coalesce simultaneous requests for the same thumbnail without
    // serializing every gallery card behind one expensive PNG decode.
    private val decodeLocks = Array(16) { Mutex() }
    private val previews = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(file: File, revision: Long = 0L, maxEdge: Int = 768): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
            val edge = maxEdge.coerceIn(64, 1024)
            val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$revision:$edge"
            synchronized(previews) {
                previews.get(key)?.takeUnless(Bitmap::isRecycled)
            }?.let { return@withContext it }

            val stripe = (key.hashCode() and Int.MAX_VALUE) % decodeLocks.size
            decodeLocks[stripe].withLock {
                // Another card may have populated the cache while this one waited.
                synchronized(previews) {
                    previews.get(key)?.takeUnless(Bitmap::isRecycled)
                }?.let { return@withLock it }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withLock null

            var sample = 1
            while (options.outWidth / sample > edge || options.outHeight / sample > edge) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return@withLock null
            if (decoded.width > edge || decoded.height > edge) {
                // Odd decoder rounding can exceed the requested edge; cap explicitly.
                val factor = minOf(edge.toFloat() / decoded.width, edge.toFloat() / decoded.height)
                val resized = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * factor).toInt().coerceAtLeast(1),
                    (decoded.height * factor).toInt().coerceAtLeast(1),
                    true,
                )
                if (resized !== decoded) decoded.recycle()
                synchronized(previews) { previews.put(key, resized) }
                resized
            } else {
                synchronized(previews) { previews.put(key, decoded) }
                decoded
            }
            }
        }
}
