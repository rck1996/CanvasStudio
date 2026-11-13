package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Conservative QuickShape recognizer. It only accepts an intentionally held
 * endpoint and a low-deviation, non-retracing stroke. It never guesses from an
 * ordinary released stroke, and it does not allocate a raster or a bitmap.
 */
internal object QuickShapeRecognizer {
    const val HOLD_MILLIS = 320L

    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
    )

    data class Oval(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val rotationDegrees: Float = 0f,
    )

    data class Vertex(val x: Float, val y: Float)
    data class Polygon(val vertices: List<Vertex>)

    fun detectLine(
        points: List<StrokePoint>,
        releaseX: Float,
        releaseY: Float,
        heldDurationMillis: Long,
        pixelsPerUnit: Float = 1f,
    ): Line? {
        if (points.size < 3) return null
        if (!releaseX.isFinite() || !releaseY.isFinite()) return null
        val first = points.first()
        val endpoint = points.last()
        if (heldDurationMillis < HOLD_MILLIS) return null
        val length = hypot(releaseX - first.x, releaseY - first.y)
        if (!length.isFinite() || length * pixelsPerUnit < 25f) return null
        // StrokeSampler smooths incoming points, so its final point may lag
        // behind the real pen coordinate. Permit that bounded lag, while the
        // view separately verifies that the raw pointer has truly been held.
        val filteredLength = hypot(endpoint.x - first.x, endpoint.y - first.y)
        val smoothingLag = hypot(releaseX - endpoint.x, releaseY - endpoint.y)
        if (!filteredLength.isFinite() || smoothingLag > (12f + length * .055f).coerceAtMost(60f)) return null

        // Reject wide arcs and retracing/zigzags. Use cross-product distance,
        // avoiding expensive curve fitting, and bound length/endpoint ratio.
        var walked = 0f
        var maxDeviation = 0f
        for (index in 1 until points.size) {
            val prev = points[index - 1]
            val curr = points[index]
            if (!curr.x.isFinite() || !curr.y.isFinite()) return null
            walked += hypot(curr.x - prev.x, curr.y - prev.y)
            val cross = abs(
                (releaseX - first.x) * (curr.y - first.y) -
                (releaseY - first.y) * (curr.x - first.x),
            )
            maxDeviation = max(maxDeviation, cross / length)
        }
        if (walked > length * 1.23f) return null
        if (maxDeviation > max(6f / pixelsPerUnit, length * 0.072f)) return null
        return Line(first.x, first.y, releaseX, releaseY)
    }

    /** Fit an ellipse in the contact contour's principal axes, independent of brush stabilization. */
    fun detectOval(
        points: List<StrokePoint>,
        releaseX: Float,
        releaseY: Float,
        heldDurationMillis: Long,
        pixelsPerUnit: Float = 1f,
    ): Oval? {
        if (heldDurationMillis < HOLD_MILLIS || points.size < 12 ||
            !releaseX.isFinite() || !releaseY.isFinite()
        ) return null
        val originX = points.first().x
        val originY = points.first().y
        var weight = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        var ax = releaseX - originX
        var ay = releaseY - originY
        // Length-weighted moments: drawing faster on one side or pausing
        // midway must not pull the center or axis toward denser input.
        for (p in points) {
            if (!p.x.isFinite() || !p.y.isFinite()) return null
            val bx = p.x - originX
            val by = p.y - originY
            val w = hypot(bx - ax, by - ay).toDouble()
            weight += w
            sx += w * (ax + bx) * .5
            sy += w * (ay + by) * .5
            sxx += w * (ax * ax + ax * bx + bx * bx) / 3
            syy += w * (ay * ay + ay * by + by * by) / 3
            sxy += w * (2 * ax * ay + ax * by + bx * ay + 2 * bx * by) / 6
            ax = bx
            ay = by
        }
        if (weight <= 0 || !weight.isFinite()) return null
        val xx = sxx / weight - (sx / weight) * (sx / weight)
        val yy = syy / weight - (sy / weight) * (sy / weight)
        val xy = sxy / weight - (sx / weight) * (sy / weight)
        var angle = atan2(2 * xy, xx - yy) * .5
        // Prefer an unrotated bounding box for essentially horizontal/vertical
        // contours and nearly round gestures, where the angle is unstable.
        if (abs(sin(2 * angle)) < .07 || hypot(xx - yy, 2 * xy) < (xx + yy) * .09) angle = 0.0
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        fun projectX(x: Float, y: Float) = (x - originX) * c + (y - originY) * s
        fun projectY(x: Float, y: Float) = (y - originY) * c - (x - originX) * s
        var left = projectX(releaseX, releaseY)
        var top = projectY(releaseX, releaseY)
        var right = left
        var bottom = top
        for (point in points) {
            val x = projectX(point.x, point.y)
            val y = projectY(point.x, point.y)
            left = min(left, x)
            top = min(top, y)
            right = max(right, x)
            bottom = max(bottom, y)
        }
        val width = right - left
        val height = bottom - top
        val smallAxis = min(width, height)
        val largeAxis = max(width, height)
        if (!largeAxis.isFinite() || smallAxis * pixelsPerUnit < 20f || smallAxis < largeAxis * .18f) return null
        val closure = max(8f / pixelsPerUnit, smallAxis * .29f).coerceAtMost(52f / pixelsPerUnit)
        val first = points.first()
        val last = points.last()
        if (hypot(releaseX - first.x, releaseY - first.y) > closure ||
            hypot(releaseX - last.x, releaseY - last.y) > closure
        ) return null

        val centerX = left + width * .5f
        val centerY = top + height * .5f
        var previousAngle = 0.0
        var netAngle = 0.0
        var totalAngle = 0.0
        var errorIntegral = 0.0
        var previousError = 0f
        // Include the raw release position, but do not invent a closing segment.
        // Weight fit error by angle so stationary hold samples do not bias it.
        for (index in 0..points.size) {
            val x = if (index == points.size) releaseX else points[index].x
            val y = if (index == points.size) releaseY else points[index].y
            val nx = (projectX(x, y) - centerX) / (width * .5f)
            val ny = (projectY(x, y) - centerY) / (height * .5f)
            val error = abs(hypot(nx, ny) - 1f)
            if (error > .32f) return null
            val pointAngle = atan2(ny.toDouble(), nx.toDouble())
            if (index > 0) {
                var delta = pointAngle - previousAngle
                if (delta > PI) delta -= 2 * PI
                else if (delta < -PI) delta += 2 * PI
                val magnitude = abs(delta)
                if (magnitude > PI * .48) return null
                netAngle += delta
                totalAngle += magnitude
                errorIntegral += magnitude * (error + previousError) * .5
            }
            previousAngle = pointAngle
            previousError = error
        }
        if (abs(netAngle) !in (PI * 1.68)..(PI * 2.24) ||
            totalAngle - abs(netAngle) > .75 ||
            errorIntegral / totalAngle > .105
        ) return null

        // Near-round gestures are often intended as circles. Keep the center
        // and use the mean diameter instead of biasing toward either axis.
        val worldX = originX + centerX * c - centerY * s
        val worldY = originY + centerX * s + centerY * c
        return if (smallAxis / largeAxis >= .92f) {
            val radius = width * .25f + height * .25f
            Oval(worldX - radius, worldY - radius, worldX + radius, worldY + radius)
        } else Oval(worldX - width / 2f, worldY - height / 2f, worldX + width / 2f,
            worldY + height / 2f, Math.toDegrees(angle).toFloat())
    }

    /** Fit straight sides rather than requiring a sample on each exact corner. */
    fun detectPolygon(points: List<StrokePoint>, pixelsPerUnit: Float = 1f): Polygon? {
        if (points.size < 4 || !pixelsPerUnit.isFinite() || pixelsPerUnit <= 0f ||
            points.any { !it.x.isFinite() || !it.y.isFinite() }
        ) return null
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        val shortExtent = min(width, height)
        val diagonal = hypot(width, height)
        if (!diagonal.isFinite() || diagonal * pixelsPerUnit < 32f || shortExtent * pixelsPerUnit < 15f) return null
        val outline = points.map { Vertex(it.x, it.y) }
        if (distance(outline.first(), outline.last()) > min(diagonal * .19f, 64f / pixelsPerUnit)) return null
        // The gesture may pass its starting point and leave a small pen hook.
        // Remove it only if real input crossed the seam; never trim an open side.
        val reversed = outline.asReversed()
        val overrun = trimClosingOverrun(outline, diagonal, pixelsPerUnit)
        overrun?.let { fitClosedOutline(it, diagonal, shortExtent, pixelsPerUnit)?.let { fit -> return fit } }
        val leadIn = trimClosingOverrun(reversed, diagonal, pixelsPerUnit)
        leadIn?.let { fitClosedOutline(it.asReversed(), diagonal, shortExtent, pixelsPerUnit)?.let { fit -> return fit } }
        return fitClosedOutline(outline, diagonal, shortExtent, pixelsPerUnit)
    }

    private fun trimClosingOverrun(outline: List<Vertex>, diagonal: Float, pixelsPerUnit: Float): List<Vertex>? {
        val start = outline.first()
        val hookLimit = min(diagonal * .12f, 30f / pixelsPerUnit)
        val seamLimit = min(diagonal * .023f, 7f / pixelsPerUnit)
        val minHook = 4f / pixelsPerUnit
        // A full traced side must remain much longer than the allowed tail.
        if (distance(start, outline.last()) < minHook) return null
        var tail = 0f
        var bestDistance = seamLimit
        var bestIndex = -1
        var bestPoint = start
        for (index in outline.lastIndex downTo 2) {
            val a = outline[index - 1]
            val b = outline[index]
            val length = distance(a, b)
            if (length == 0f) continue
            val position = projectionOnSegment(start, a, b, length * length)
            val beyondSeam = tail + (1f - position) * length
            if (beyondSeam in minHook..hookLimit) {
                val candidate = Vertex(a.x + (b.x - a.x) * position, a.y + (b.y - a.y) * position)
                val gap = distance(start, candidate)
                if (gap < bestDistance) {
                    bestDistance = gap
                    bestIndex = index
                    bestPoint = candidate
                }
            }
            tail += length
            if (tail > hookLimit) break
        }
        return if (bestIndex == -1) null else outline.take(bestIndex) + bestPoint
    }

    private fun fitClosedOutline(
        outline: List<Vertex>,
        diagonal: Float,
        shortExtent: Float,
        pixelsPerUnit: Float,
    ): Polygon? {
        // Keep one copy of the seam if the user touched the starting point exactly.
        val ring = if (outline.first() == outline.last()) outline.dropLast(1) else outline
        val size = ring.size
        if (size < 3) return null
        val cycle = ring + ring + ring.first()
        val arc = FloatArray(cycle.size)
        for (i in 1 until arc.size) arc[i] = arc[i - 1] + distance(cycle[i - 1], cycle[i])
        val perimeter = arc[size]
        if (perimeter !in diagonal * 1.9f..diagonal * 3.4f) return null

        // Seed the reduction at the strongest turns as well as at pen down.
        // A seam at a rounded corner or mid-edge must not determine the fit.
        val window = max(shortExtent * .22f, 4f / pixelsPerUnit).coerceAtMost(perimeter * .045f)
        val turns = FloatArray(size) { index ->
            val offset = arc[index]
            val before = pointAt(cycle, arc, if (offset < window) perimeter + offset - window else offset - window)
            val after = pointAt(cycle, arc, offset + window)
            val pivot = cycle[index]
            val incomingX = pivot.x - before.x
            val incomingY = pivot.y - before.y
            val outgoingX = after.x - pivot.x
            val outgoingY = after.y - pivot.y
            abs(atan2(incomingX * outgoingY - incomingY * outgoingX, incomingX * outgoingX + incomingY * outgoingY))
        }
        val starts = mutableListOf(0)
        for (seed in 1..3) {
            var next = -1
            for (index in turns.indices) {
                if (next != -1 && turns[index] <= turns[next]) continue
                if (starts.all { start ->
                    val gap = abs(arc[index] - arc[start])
                    min(gap, perimeter - gap) > window
                }) next = index
            }
            if (next == -1) break
            starts += next
        }
        val seeds = starts.map { start ->
            start to (start + 1 until start + size).maxBy { distance(cycle[start], cycle[it]) }
        }
        // The short dimension limits simplification so skinny rectangles keep
        // both end caps, even if the long side is many times their width.
        val tolerances = floatArrayOf(
            min(diagonal * .014f, shortExtent * .10f),
            min(diagonal * .028f, shortExtent * .17f),
            min(diagonal * .043f, shortExtent * .26f),
            min(diagonal * .064f, shortExtent * .34f),
        )
        val minEdge = max(8f / pixelsPerUnit, diagonal * .034f)
        for (tolerance in tolerances) {
            val epsilon = max(1f / pixelsPerUnit, tolerance)
            for ((start, opposite) in seeds) {
                val indices = polygonCorners(cycle, start, size, opposite, epsilon)
                if (indices.size !in 3..4) continue
                fitPolygon(cycle, arc, size, indices, diagonal, minEdge, pixelsPerUnit)?.let { return it }
            }
        }
        return null
    }

    private fun polygonCorners(
        outline: List<Vertex>, start: Int, size: Int, opposite: Int, epsilon: Float,
    ): List<Int> {
        val end = start + size
        val corners = mutableListOf(start)
        fun reduce(from: Int, to: Int) {
            if (corners.size > 8) return
            var pivot = -1
            var error = epsilon
            for (index in from + 1 until to) {
                val candidate = distanceToSegment(outline[index], outline[from], outline[to])
                if (candidate > error) {
                    pivot = index
                    error = candidate
                }
            }
            if (pivot != -1) {
                reduce(from, pivot)
                reduce(pivot, to)
            } else if (to != end) corners += to
        }
        reduce(start, opposite)
        reduce(opposite, end)
        if (corners.size > 8) return emptyList()
        while (corners.size > 3) {
            fun deviation(i: Int): Float {
                val n = corners.size
                return distanceToSegment(outline[corners[i]], outline[corners[(i + n - 1) % n]],
                    outline[corners[(i + 1) % n]])
            }
            val redundant = corners.indices.minBy(::deviation)
            if (deviation(redundant) > epsilon) break
            corners.removeAt(redundant)
        }
        return corners
    }

    private data class Side(val from: Vertex, val to: Vertex) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy)
    }

    private fun fitPolygon(
        outline: List<Vertex>,
        arc: FloatArray,
        size: Int,
        indices: List<Int>,
        diagonal: Float,
        minEdge: Float,
        pixelsPerUnit: Float,
    ): Polygon? {
        val n = indices.size
        val sides = indices.mapIndexed { i, start ->
            val end = if (i + 1 == n) indices[0] + size else indices[i + 1]
            val chord = distance(outline[start], outline[end])
            if (chord < minEdge || arc[end] - arc[start] > chord * 1.3f) return null
            val span = arc[end] - arc[start]
            // Estimate direction from the middle half with length-uniform
            // regression, instead of pinning the fit to two samples close to
            // rounded corners (especially fragile on short sides).
            val samples = List(7) { sample -> pointAt(outline, arc, arc[start] + span * (.25f + sample / 12f)) }
            val origin = samples[3]
            var x = 0f
            var y = 0f
            var dx = 0f
            var dy = 0f
            for (sample in samples.indices) {
                val offsetX = samples[sample].x - origin.x
                val offsetY = samples[sample].y - origin.y
                x += offsetX
                y += offsetY
                dx += (sample - 3) * offsetX
                dy += (sample - 3) * offsetY
            }
            x = origin.x + x / samples.size
            y = origin.y + y / samples.size
            // Sum of squares for offsets -3..3 is 28. Extend the fitted
            // segment to the original 15–85% interval for the angle tests.
            dx *= 4.2f / 28f
            dy *= 4.2f / 28f
            val core = Side(Vertex(x - dx, y - dy), Vertex(x + dx, y + dy))
            if (core.length < chord * .50f) return null
            val edgeError = max(1.1f / pixelsPerUnit, core.length * .082f)
            if (samples.any { distanceToSegment(it, core.from, core.to) > edgeError }) return null
            // A fitted line can pass through the middle of a curved arc:
            // the chord through the ends still needs a sufficiently flat core.
            val coreChord = Side(samples.first(), samples.last())
            if (samples.any { distanceToSegment(it, coreChord.from, coreChord.to) > edgeError }) return null
            core
        }
        var turnSign = 0
        val corners = indices.mapIndexed { i, index ->
            val a = sides[(i + n - 1) % n]
            val b = sides[i]
            val cross = a.dx * b.dy - a.dy * b.dx
            val product = a.length * b.length
            if (abs(cross) < product * .24f) return null
            val sign = if (cross > 0f) 1 else -1
            if (turnSign != 0 && turnSign != sign) return null
            turnSign = sign
            if (n == 4 && abs(a.dx * b.dx + a.dy * b.dy) > product * .50f) return null
            val t = ((b.from.x - a.from.x) * b.dy - (b.from.y - a.from.y) * b.dx) / cross
            val corner = Vertex(a.from.x + t * a.dx, a.from.y + t * a.dy)
            if (distance(corner, outline[index]) > diagonal * .16f) return null
            corner
        }
        val lengths = corners.indices.map { distance(corners[it], corners[(it + 1) % n]) }
        val perimeter = lengths.sum()
        if (arc[size] !in perimeter * .80f..perimeter * 1.22f) return null
        if (n == 3) return Polygon(corners)

        // A rounded triangle can appear to have a fourth, very short side
        // near the tip. True rectangles, including thin ones, have opposing
        // sides of comparable lengths; compare each pair, not the aspect ratio.
        for (i in 0..1) {
            if (min(lengths[i], lengths[i + 2]) < max(lengths[i], lengths[i + 2]) * .55f) return null
        }

        // Average opposing edge directions, then construct a truly orthogonal rectangle.
        val axis = if (lengths[0] + lengths[2] >= lengths[1] + lengths[3]) 0 else 1
        val a = corners[axis]
        val b = corners[axis + 1]
        val c = corners[axis + 2]
        val d = corners[(axis + 3) % 4]
        val angle = atan2((b.y - a.y) + (c.y - d.y), (b.x - a.x) + (c.x - d.x))
        val ux = cos(angle)
        val uy = sin(angle)
        val xs = corners.map { (it.x - a.x) * ux + (it.y - a.y) * uy }
        val ys = corners.map { (it.y - a.y) * ux - (it.x - a.x) * uy }
        fun vertex(x: Float, y: Float) = Vertex(a.x + x * ux - y * uy, a.y + x * uy + y * ux)
        return Polygon(listOf(vertex(xs.min(), ys.min()), vertex(xs.max(), ys.min()),
            vertex(xs.max(), ys.max()), vertex(xs.min(), ys.max())))
    }

    private fun distance(a: Vertex, b: Vertex) = hypot(a.x - b.x, a.y - b.y)

    private fun pointAt(outline: List<Vertex>, arc: FloatArray, target: Float): Vertex {
        var lo = 1
        var hi = arc.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (arc[mid] < target) lo = mid + 1 else hi = mid
        }
        val a = outline[lo - 1]
        val b = outline[lo]
        val ratio = (target - arc[lo - 1]) / (arc[lo] - arc[lo - 1]).coerceAtLeast(.0001f)
        return Vertex(a.x + (b.x - a.x) * ratio, a.y + (b.y - a.y) * ratio)
    }

    private fun projectionOnSegment(p: Vertex, a: Vertex, b: Vertex, lengthSquared: Float): Float =
        (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / lengthSquared).coerceIn(0f, 1f)

    private fun distanceToSegment(p: Vertex, a: Vertex, b: Vertex): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val squared = dx * dx + dy * dy
        if (squared == 0f) return distance(p, a)
        val t = projectionOnSegment(p, a, b, squared)
        return hypot(p.x - a.x - dx * t, p.y - a.y - dy * t)
    }
}
