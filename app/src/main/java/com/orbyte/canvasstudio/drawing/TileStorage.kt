package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Tile encoding and geometry shared by persistence and the sparse renderer. */
internal object TileStorage {
    const val TILE_SIZE: Int = 512

    enum class WriteResult { WRITTEN, DELETED, FAILED }

    data class Key(val column: Int, val row: Int) {
        val fileName: String get() = "${column}_${row}.png"
    }

    fun allKeys(documentWidth: Int, documentHeight: Int): Set<Key> {
        val columns = ceil(documentWidth / TILE_SIZE.toDouble()).toInt()
        val rows = ceil(documentHeight / TILE_SIZE.toDouble()).toInt()
        return buildSet(columns * rows) {
            repeat(rows) { row ->
                repeat(columns) { column -> add(Key(column, row)) }
            }
        }
    }

    fun keysForBounds(bounds: RectF, documentWidth: Int, documentHeight: Int): Set<Key> {
        if (bounds.isEmpty || documentWidth <= 0 || documentHeight <= 0) return emptySet()
        val clippedLeft = bounds.left.coerceIn(0f, documentWidth.toFloat())
        val clippedTop = bounds.top.coerceIn(0f, documentHeight.toFloat())
        val clippedRight = bounds.right.coerceIn(0f, documentWidth.toFloat())
        val clippedBottom = bounds.bottom.coerceIn(0f, documentHeight.toFloat())
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return emptySet()

        val firstColumn = floor(clippedLeft / TILE_SIZE).toInt()
        val lastColumn = floor((clippedRight - 0.001f) / TILE_SIZE).toInt()
        val firstRow = floor(clippedTop / TILE_SIZE).toInt()
        val lastRow = floor((clippedBottom - 0.001f) / TILE_SIZE).toInt()
        return buildSet {
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) add(Key(column, row))
            }
        }
    }

    /**
     * Visits tiles in visual order without allocating an intermediate [Set].
     *
     * This is used by the render path, which runs for every visible layer on every frame.
     */
    inline fun forEachKeyInBounds(
        bounds: RectF,
        documentWidth: Int,
        documentHeight: Int,
        action: (Key) -> Unit,
    ) {
        if (bounds.isEmpty || documentWidth <= 0 || documentHeight <= 0) return
        val clippedLeft = bounds.left.coerceIn(0f, documentWidth.toFloat())
        val clippedTop = bounds.top.coerceIn(0f, documentHeight.toFloat())
        val clippedRight = bounds.right.coerceIn(0f, documentWidth.toFloat())
        val clippedBottom = bounds.bottom.coerceIn(0f, documentHeight.toFloat())
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return

        val firstColumn = floor(clippedLeft / TILE_SIZE).toInt()
        val lastColumn = floor((clippedRight - 0.001f) / TILE_SIZE).toInt()
        val firstRow = floor(clippedTop / TILE_SIZE).toInt()
        val lastRow = floor((clippedBottom - 0.001f) / TILE_SIZE).toInt()
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) action(Key(column, row))
        }
    }

    fun tileRect(key: Key, documentWidth: Int, documentHeight: Int): Rect {
        val left = key.column * TILE_SIZE
        val top = key.row * TILE_SIZE
        return Rect(
            left,
            top,
            min(documentWidth, left + TILE_SIZE),
            min(documentHeight, top + TILE_SIZE),
        )
    }

    fun saveTileAtomically(source: Bitmap, key: Key, destinationDirectory: File): WriteResult {
        destinationDirectory.mkdirs()
        val rect = tileRect(key, source.width, source.height)
        if (rect.width() <= 0 || rect.height() <= 0) return WriteResult.FAILED
        val tile = synchronized(source) {
            Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        }
        return try {
            saveTileBitmapAtomically(tile, key, destinationDirectory)
        } finally {
            tile.recycle()
        }
    }

    fun saveTileBitmapAtomically(tile: Bitmap, key: Key, destinationDirectory: File): WriteResult {
        destinationDirectory.mkdirs()
        val destination = File(destinationDirectory, key.fileName)
        if (isFullyTransparent(tile)) {
            File(destinationDirectory, "${key.fileName}.tmp").delete()
            return if (!destination.exists() || destination.delete()) WriteResult.DELETED else WriteResult.FAILED
        }
        val temporary = File(destinationDirectory, "${key.fileName}.tmp")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                check(tile.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.fd.sync()
            }
            if (destination.exists() && !destination.delete()) error("No se pudo reemplazar el tile")
            if (!temporary.renameTo(destination)) error("No se pudo completar el tile")
            WriteResult.WRITTEN
        }.getOrElse {
            temporary.delete()
            WriteResult.FAILED
        }
    }

    fun loadTile(file: File): Bitmap? {
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
    }

    fun loadTilesInto(target: Bitmap, tileDirectory: File) {
        if (!tileDirectory.isDirectory) return
        val canvas = Canvas(target)
        existingKeys(tileDirectory).sortedWith(compareBy<Key> { it.row }.thenBy { it.column }).forEach { key ->
            val decoded = loadTile(File(tileDirectory, key.fileName)) ?: return@forEach
            try {
                canvas.drawBitmap(
                    decoded,
                    (key.column * TILE_SIZE).toFloat(),
                    (key.row * TILE_SIZE).toFloat(),
                    null,
                )
            } finally {
                decoded.recycle()
            }
        }
    }

    fun copyTileDirectory(source: File, destination: File): Boolean {
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        if (!source.isDirectory) return true
        return source.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            .orEmpty()
            .all { file -> file.copyTo(File(destination, file.name), overwrite = true).isFile }
    }

    fun existingKeys(tileDirectory: File): Set<Key> =
        tileDirectory.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file -> parseKey(file.nameWithoutExtension) }
            .toSet()

    fun existingTileCount(tileDirectory: File): Int = existingKeys(tileDirectory).size

    fun parseKey(value: String): Key? {
        val parts = value.split('_')
        if (parts.size != 2) return null
        val column = parts[0].toIntOrNull() ?: return null
        val row = parts[1].toIntOrNull() ?: return null
        if (column < 0 || row < 0) return null
        return Key(column, row)
    }

    private fun isFullyTransparent(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                if (pixel ushr 24 != 0) return false
            }
        }
        return true
    }
}
