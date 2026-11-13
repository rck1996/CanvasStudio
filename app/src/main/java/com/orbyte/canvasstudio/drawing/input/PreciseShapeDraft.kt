package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.DrawingTool
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Geometry only: an uncommitted figure has no raster or history entry. */
internal data class PreciseShapeDraft(val tool: DrawingTool, val vertices: List<Point>) {
    data class Point(val x: Float, val y: Float)
    data class EllipseFrame(val center: Point, val radiusX: Float, val radiusY: Float, val rotationDegrees: Float)

    val center: Point get() = Point(
        (vertices.sumOf { it.x.toDouble() } / vertices.size).toFloat(),
        (vertices.sumOf { it.y.toDouble() } / vertices.size).toFloat(),
    )

    /** Local ellipse axes; the vertices are the rotated corners of its editing guide. */
    val ellipseFrame: EllipseFrame?
        get() {
            if (tool != DrawingTool.ELLIPSE) return null
            val a = vertices[0]
            val b = vertices[1]
            return EllipseFrame(
                center, distance(a, b) / 2f, distance(a, vertices[3]) / 2f,
                Math.toDegrees(atan2(b.y - a.y, b.x - a.x).toDouble()).toFloat(),
            )
        }

    fun hitsCenter(point: Point, radius: Float): Boolean = distance(center, point) <= radius

    fun translated(dx: Float, dy: Float, pageWidth: Float, pageHeight: Float): PreciseShapeDraft {
        val x = dx.coerceIn(-vertices.minOf { it.x }, max(-vertices.minOf { it.x }, pageWidth - vertices.maxOf { it.x }))
        val y = dy.coerceIn(-vertices.minOf { it.y }, max(-vertices.minOf { it.y }, pageHeight - vertices.maxOf { it.y }))
        return copy(vertices = vertices.map { Point(it.x + x, it.y + y) })
    }

    fun nearestHandle(point: Point, radius: Float): Int? =
        vertices.indices.minByOrNull { distance(vertices[it], point) }
            ?.takeIf { distance(vertices[it], point) <= radius }

    fun moveHandle(index: Int, point: Point, minimum: Float): PreciseShapeDraft {
        if (index !in vertices.indices || !point.x.isFinite() || !point.y.isFinite() ||
            !minimum.isFinite() || minimum <= 0f
        ) return this
        if (tool == DrawingTool.RECTANGLE || tool == DrawingTool.ELLIPSE) {
            // Resize in the draft's own axes so editing a tilted QuickShape
            // never flips it upright or moves the diagonally opposite corner.
            val opposite = vertices[(index + 2) % 4]
            val xAxis = unitVector(vertices[0], vertices[1])
            val yAxis = Point(-xAxis.y, xAxis.x)
            val directionX = if (index == 0 || index == 3) -1f else 1f
            val directionY = if (index < 2) -1f else 1f
            val dx = point.x - opposite.x
            val dy = point.y - opposite.y
            val x = directionX * ((dx * xAxis.x + dy * xAxis.y) * directionX).coerceAtLeast(minimum)
            val y = directionY * ((dx * yAxis.x + dy * yAxis.y) * directionY).coerceAtLeast(minimum)
            return copy(vertices = vertices.indices.map { vertex ->
                val sameColumn = (vertex == 0 || vertex == 3) == (directionX < 0)
                val sameRow = (vertex < 2) == (directionY < 0)
                val localX = if (sameColumn) x else 0f
                val localY = if (sameRow) y else 0f
                Point(opposite.x + xAxis.x * localX + yAxis.x * localY,
                    opposite.y + xAxis.y * localX + yAxis.y * localY)
            })
        }
        val candidate = vertices.toMutableList().apply { this[index] = point }
        if (tool == DrawingTool.LINE) {
            return if (distance(candidate[0], candidate[1]) >= minimum) copy(vertices = candidate) else this
        }
        val a = candidate[0]
        val b = candidate[1]
        val c = candidate[2]
        val area2 = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
        val lengths = listOf(distance(a, b), distance(b, c), distance(c, a))
        if (lengths.min() < minimum || area2 < lengths.max() * minimum) return this
        return copy(vertices = candidate)
    }

    companion object {
        fun fromRecognition(result: QuickShapeContact.Result): PreciseShapeDraft = when (result) {
            is QuickShapeContact.Result.Straight -> with(result.line) {
                PreciseShapeDraft(DrawingTool.LINE, listOf(Point(startX, startY), Point(endX, endY)))
            }
            is QuickShapeContact.Result.Angular -> {
                val vertices = result.polygon.vertices
                PreciseShapeDraft(
                    if (vertices.size == 3) DrawingTool.TRIANGLE else DrawingTool.RECTANGLE,
                    vertices.map { Point(it.x, it.y) },
                )
            }
            is QuickShapeContact.Result.Rounded -> with(result.oval) {
                val radiusX = (right - left) / 2f
                val radiusY = (bottom - top) / 2f
                val centerX = left + radiusX
                val centerY = top + radiusY
                val radians = Math.toRadians(rotationDegrees.toDouble())
                val c = cos(radians).toFloat()
                val s = sin(radians).toFloat()
                val vertices = listOf(Point(-radiusX, -radiusY), Point(radiusX, -radiusY),
                    Point(radiusX, radiusY), Point(-radiusX, radiusY))
                PreciseShapeDraft(DrawingTool.ELLIPSE, vertices.map { (x, y) ->
                    Point(centerX + x * c - y * s, centerY + x * s + y * c)
                })
            }
        }

        fun supports(tool: DrawingTool): Boolean = when (tool) {
            DrawingTool.LINE, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE, DrawingTool.TRIANGLE -> true
            else -> false
        }

        fun fromDrag(tool: DrawingTool, start: Point, end: Point, minimum: Float): PreciseShapeDraft? {
            require(supports(tool))
            if (!start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite() ||
                !minimum.isFinite() || minimum <= 0f
            ) return null
            if (tool == DrawingTool.LINE) {
                return if (distance(start, end) >= minimum) PreciseShapeDraft(tool, listOf(start, end)) else null
            }
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val right = max(start.x, end.x)
            val bottom = max(start.y, end.y)
            if (right - left < minimum || bottom - top < minimum) return null
            val points = if (tool == DrawingTool.RECTANGLE || tool == DrawingTool.ELLIPSE) {
                listOf(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom))
            } else {
                listOf(Point(left + (right - left) / 2f, top), Point(right, bottom), Point(left, bottom))
            }
            return PreciseShapeDraft(tool, points)
        }

        private fun distance(a: Point, b: Point): Float = hypot(b.x - a.x, b.y - a.y)

        private fun unitVector(a: Point, b: Point): Point {
            val length = distance(a, b)
            return Point((b.x - a.x) / length, (b.y - a.y) / length)
        }
    }
}
