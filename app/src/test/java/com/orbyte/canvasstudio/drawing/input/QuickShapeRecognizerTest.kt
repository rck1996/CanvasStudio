package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class QuickShapeRecognizerTest {
    private fun point(x: Float, y: Float, t: Long): StrokePoint =
        StrokePoint(x, y, 1f, 0f, t)

    private fun straight(): List<StrokePoint> =
        (0..10).map { n -> point(n * 12f, n * .1f, (n * 16).toLong()) }

    @Test fun heldStraightStrokeSnapsToLine() {
        val detected = QuickShapeRecognizer.detectLine(straight(), 120f, 1f, 720L)
        assertNotNull(detected)
        assertEquals(0f, detected!!.startX, .01f)
        assertEquals(120f, detected.endX, .01f)
    }

    @Test fun smoothedEndpointLagStillSnapsWhenRawPenIsStationary() {
        val filtered = (0..12).map { n ->
            val raw = n * 40f
            val lag = if (n == 0) 0f else 14f
            point(raw - lag, 180f, n * 16L)
        }
        val detected = QuickShapeRecognizer.detectLine(filtered, 480f, 180f, 700L)
        assertNotNull(detected)
        assertEquals(480f, detected!!.endX, .01f)
    }

    @Test fun insufficientRawHoldDurationRejectsEvenLongStrokes() {
        assertNull(QuickShapeRecognizer.detectLine(straight(), 120f, 1f, 60L))
    }

    @Test fun normalPenLiftNeverSnaps() {
        assertNull(QuickShapeRecognizer.detectLine(straight(), 120f, 1f, 200L))
    }

    @Test fun noisyStationaryHoldDoesNotRestartTimer() {
        val jitter = straight() + listOf(
            point(120.5f, 1.2f, 250L),
            point(119.6f, .9f, 370L),
            point(120.3f, 1f, 490L),
        )
        assertNotNull(QuickShapeRecognizer.detectLine(jitter, 120f, 1f, 950L))
    }

    @Test fun curvedStrokeRemainsFreehandEvenAfterHold() {
        val curved = (0..20).map { n ->
            point(n * 8f, sin(n / 20.0 * Math.PI).toFloat() * 42f, n * 16L)
        }
        assertNull(QuickShapeRecognizer.detectLine(curved, 160f, 0f, 1000L))
    }

    @Test fun retracedStrokeDoesNotBecomeAStraightLine() {
        val retraced = listOf(
            point(0f, 0f, 0),
            point(70f, 0f, 20),
            point(140f, 0f, 40),
            point(100f, 0f, 60),
            point(140f, 0f, 80),
            point(150f, 0f, 100),
        )
        assertNull(QuickShapeRecognizer.detectLine(retraced, 150f, 0f, 720L))
    }

    @Test fun farReleaseIsNotConsideredAHold() {
        assertNull(QuickShapeRecognizer.detectLine(straight(), 145f, 1f, 900L))
    }

    @Test fun tooShortStrokeOrInsufficientSamplesCannotSnap() {
        assertNull(QuickShapeRecognizer.detectLine(listOf(point(0f,0f,0), point(10f,0f,10)), 10f, 0f, 1000L))
        val short = (0..5).map { point(it * 3f, 0f, it * 15L) }
        assertNull(QuickShapeRecognizer.detectLine(short, 15f, 0f, 1000L))
    }

    @Test fun badCoordinatesNeverGenerateInvalidGeometry() {
        val points = straight().toMutableList()
        points[2] = point(Float.NaN, 0f, 40)
        assertNull(QuickShapeRecognizer.detectLine(points, 120f, 1f, 900L))
    }

    @Test fun heldClosedOvalKeepsItsDistinctAxes() {
        val points = oval(80f, 48f)
        val result = QuickShapeRecognizer.detectOval(points, 280f, 200f, 600L)
        assertNotNull(result)
        assertEquals(120f, result!!.left, .01f)
        assertEquals(152f, result.top, .01f)
        assertEquals(280f, result.right, .01f)
        assertEquals(248f, result.bottom, .01f)
    }

    @Test fun nearRoundShapeBecomesAnExactCircleWithoutMovingItsCenter() {
        val result = QuickShapeRecognizer.detectOval(oval(52f, 50f), 252f, 200f, 650L)
        assertNotNull(result)
        assertEquals(result!!.right - result.left, result.bottom - result.top, .01f)
        assertEquals(200f, (result.right + result.left) / 2f, .01f)
        assertEquals(200f, (result.top + result.bottom) / 2f, .01f)
    }

    @Test fun releasingAClosedStrokeWithoutHoldingDoesNotSnap() {
        assertNull(QuickShapeRecognizer.detectOval(oval(80f, 48f), 280f, 200f, 120L))
    }

    @Test fun openArcAndRetracedOvalStayFreehand() {
        val points = oval(80f, 48f)
        val open = points.take(34)
        assertNull(QuickShapeRecognizer.detectOval(open, open.last().x, open.last().y, 650L))
        assertNull(QuickShapeRecognizer.detectOval(points + points.reversed(), 280f, 200f, 650L))
    }

    @Test fun roundedContourDoesNotAcceptRectangleCorners() {
        val rectangle = (0..64).map { index ->
            val side = index / 16 % 4
            val t = (index % 16) / 16f
            when (side) {
                0 -> point(120f + t * 160f, 150f, index * 8L)
                1 -> point(280f, 150f + t * 100f, index * 8L)
                2 -> point(280f - t * 160f, 250f, index * 8L)
                else -> point(120f, 250f - t * 100f, index * 8L)
            }
        }
        assertNull(QuickShapeRecognizer.detectOval(rectangle, 120f, 150f, 750L))
    }

    @Test fun rotatedImperfectOvalWithSmallGapAndUnevenSamplingSnaps() {
        val rotation = Math.toRadians(36.0)
        val raw = (0..45).map { i ->
            val t = (i / 45.0).let { it * (.65 + .35 * it) } * (2 * PI - .17)
            val wobble = 1 + .045 * sin(3 * t)
            val x = 105 * cos(t) * wobble
            val y = 48 * sin(t) * wobble
            point((270 + x * cos(rotation) - y * sin(rotation)).toFloat(),
                (220 + x * sin(rotation) + y * cos(rotation)).toFloat(), i * 11L)
        }
        val result = QuickShapeRecognizer.detectOval(raw, raw.last().x, raw.last().y, 345L)
        assertNotNull(result)
        assertEquals(36f, result!!.rotationDegrees, 6f)
        assertTrue((result.right - result.left) / (result.bottom - result.top) > 1.9f)
    }

    @Test fun heldRectangleStartingMidEdgeIsRectified() {
        val contour = outline(130f to 110f, 222f to 150f, 185f to 222f,
            42f to 162f, 74f to 86f)
        val corners = requireNotNull(QuickShapeRecognizer.detectPolygon(contour)).vertices
        assertEquals(4, corners.size)
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val c = corners[(i + 2) % 4]
            val dot = (b.x - a.x) * (c.x - b.x) + (b.y - a.y) * (c.y - b.y)
            assertEquals(0f, dot, .01f)
        }
    }

    @Test fun triangleFitsButOpenAndSelfIntersectingContoursDoNot() {
        val triangle = outline(95f to 220f, 180f to 60f, 275f to 214f)
        assertEquals(3, QuickShapeRecognizer.detectPolygon(triangle)?.vertices?.size)
        assertNull(QuickShapeRecognizer.detectPolygon(triangle.dropLast(8)))
        val bowTie = outline(100f to 100f, 280f to 230f, 100f to 230f, 280f to 100f)
        assertNull(QuickShapeRecognizer.detectPolygon(bowTie))
        assertNull(QuickShapeRecognizer.detectPolygon(oval(80f, 50f)))
    }

    @Test fun roundedRectangleWithBowedSidesAndImperfectClosureStillFits() {
        val trace = roundedTrace(
            20f to 25f, 235f to 48f, 220f to 184f, 17f to 168f,
            startIndex = 10, missingSamples = 4,
        )
        val fitted = requireNotNull(QuickShapeRecognizer.detectPolygon(trace))
        assertEquals(4, fitted.vertices.size)
    }

    @Test fun roundedTriangleFitsEvenWhenTheStartAndEndDoNotTouch() {
        val trace = roundedTrace(
            104f to 196f, 174f to 59f, 280f to 218f,
            startIndex = 19, missingSamples = 3,
        )
        val fitted = requireNotNull(QuickShapeRecognizer.detectPolygon(trace))
        assertEquals(3, fitted.vertices.size)
    }

    @Test fun intentionalCurvesAndFiveSidedOutlineDoNotBecomePolygons() {
        for (scale in listOf(.35f, 1f, 2f)) {
            assertNull(QuickShapeRecognizer.detectPolygon(oval(58f, 54f), scale))
            assertNull(QuickShapeRecognizer.detectPolygon(oval(95f, 31f), scale))
        }
        val pentagon = outline(160f to 40f, 270f to 110f, 225f to 225f,
            105f to 225f, 55f to 110f)
        assertNull(QuickShapeRecognizer.detectPolygon(pentagon))
    }

    @Test fun fastAngularGesturesCanFitFromOnlyTheirCornersAndClosure() {
        val rect = listOf(35f to 50f, 210f to 50f, 210f to 140f, 35f to 140f, 35f to 50f)
        val tri = listOf(35f to 140f, 110f to 35f, 210f to 140f, 35f to 140f)
        for ((vertices, count) in listOf(rect to 4, tri to 3)) {
            val raw = vertices.mapIndexed { i, (x, y) -> point(x, y, i * 16L) }
            assertEquals(count, QuickShapeRecognizer.detectPolygon(raw)?.vertices?.size)
            assertNull(QuickShapeRecognizer.detectPolygon(raw.dropLast(1)))
        }
    }

    @Test fun narrowRotatedRectangleKeepsBothShortSidesAtDifferentZooms() {
        val corners = listOf(60f to 48f, 332f to 106f, 325f to 135f, 53f to 77f)
        // The corner can be sampled sparsely even when the long sides contain many points.
        val trace = roundedTrace(*corners.toTypedArray(), startIndex = 6, missingSamples = 1)
        for (scale in listOf(.8f, 1.6f, 2.5f)) {
            val fit = requireNotNull(QuickShapeRecognizer.detectPolygon(trace, scale)) { "scale=$scale" }
            assertEquals(4, fit.vertices.size)
            assertTrue(fit.vertices.all { vertex ->
                corners.any { (x, y) -> kotlin.math.hypot(vertex.x - x, vertex.y - y) < 7f }
            })
        }
    }

    @Test fun rectangleClosesEvenWhenRawHoldAnchorStopsShortOfTheLastSamples() {
        // At high pen speed, the last part of the left edge may arrive in just
        // two events, both inside the stationary slop of the pen anchor.
        val trace = listOf(40f to 40f, 78f to 41f, 150f to 40f, 217f to 40f,
            220f to 65f, 219f to 142f, 218f to 180f, 165f to 179f, 92f to 181f,
            41f to 180f, 40f to 123f, 41f to 90f, 40f to 75f, 40f to 71f, 40f to 70f)
        val contact = feedContact(trace, holdSlop = 12f, scale = 2f)
        assertNull(contact.recognize(contact.lastMotionTimeMillis + QuickShapeRecognizer.HOLD_MILLIS - 1))
        val fit = contact.recognize(trace.lastIndex * 12L + QuickShapeRecognizer.HOLD_MILLIS)
        assertEquals(4, (fit as? QuickShapeContact.Result.Angular)?.polygon?.vertices?.size)
    }

    @Test fun triangleWithAnUnevenTraceAndShortClosingHookKeepsItsThreeCorners() {
        val trace = listOf(74f to 160f, 83f to 142f, 93f to 117f, 117f to 57f, 127f to 46f,
            137f to 57f, 177f to 103f, 221f to 162f, 256f to 203f, 257f to 212f,
            243f to 214f, 201f to 215f, 115f to 213f, 73f to 211f, 55f to 209f,
            53f to 201f, 62f to 184f, 72f to 164f, 79f to 151f, 91f to 146f)
        for (contour in listOf(trace, trace.reversed())) {
            val contact = feedContact(contour, holdSlop = 7f)
            val fit = contact.recognize(contour.lastIndex * 12L + QuickShapeRecognizer.HOLD_MILLIS)
            val vertices = requireNotNull((fit as? QuickShapeContact.Result.Angular)?.polygon?.vertices)
            assertEquals(3, vertices.size)
            assertTrue(vertices.any { it.y < 55f })
        }
    }

    @Test fun angularFitDoesNotInventAnEdgeOrIgnoreAPartialRetrace() {
        val openU = outline(45f to 40f, 45f to 180f, 217f to 180f, 217f to 40f).take(37)
        assertNull(QuickShapeRecognizer.detectPolygon(openU))
        val retrace = outline(45f to 40f, 220f to 40f, 220f to 182f, 45f to 182f)
        val partialRetrace = retrace.take(21) + retrace.subList(13, 20).reversed() + retrace.drop(13)
        assertNull(QuickShapeRecognizer.detectPolygon(partialRetrace))
        // A sharp turn alone is not enough to accept a concave freehand loop.
        val concave = outline(45f to 40f, 220f to 40f, 125f to 89f, 45f to 182f)
        assertNull(QuickShapeRecognizer.detectPolygon(concave))
    }

    private fun feedContact(points: List<Pair<Float, Float>>, holdSlop: Float, scale: Float = 1f): QuickShapeContact {
        val contact = QuickShapeContact(point(points[0].first, points[0].second, 0), scale, holdSlop)
        points.drop(1).forEachIndexed { index, (x, y) -> contact.move(x, y, (index + 1) * 12L) }
        return contact
    }

    private fun roundedTrace(
        vararg corners: Pair<Float, Float>,
        startIndex: Int,
        missingSamples: Int,
    ): List<StrokePoint> {
        fun mix(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float) =
            (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
        val contour = corners.indices.flatMap { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val c = corners[(i + 2) % corners.size]
            val leave = mix(a, b, .12f)
            val enter = mix(b, a, .12f)
            val exit = mix(b, c, .12f)
            (0..20).map { j ->
                val t = j / 20f
                val (x, y) = mix(leave, enter, t)
                x to (y + 3.4f * sin(t * PI).toFloat())
            } + (1..7).map { j ->
                val t = j / 8f
                mix(mix(enter, b, t), mix(b, exit, t), t)
            }
        }
        return (0 until contour.size - missingSamples).map { i ->
            val (x, y) = contour[(i + startIndex) % contour.size]
            point(x, y, i * 9L)
        }
    }

    @Test fun rawContactToleratesLiftJitterButCancelsOnContinuedMovement() {
        val contact = QuickShapeContact(point(0f, 0f, 100L), pixelsPerUnit = 1f, holdSlopPx = 6f)
        (1..12).forEach { contact.move(it * 10f, it * .45f, 100L + it * 12L) }
        assertNull(contact.recognize(515L))
        assertTrue(!contact.move(124f, 7f, 545L))
        assertTrue(contact.canRelease(128f, 7f))
        assertTrue(contact.recognize(570L) is QuickShapeContact.Result.Straight)
        assertTrue(contact.move(121f, 42f, 572L))
        assertNull(contact.recognize(580L))
        assertTrue(!contact.canRelease(153f, 70f))
    }

    private fun outline(vararg corners: Pair<Float, Float>): List<StrokePoint> =
        (0..corners.size * 12).map { i ->
            val a = corners[(i / 12) % corners.size]
            val b = corners[(i / 12 + 1) % corners.size]
            val t = (i % 12) / 12f
            point(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, i * 12L)
        }

    private fun oval(rx: Float, ry: Float): List<StrokePoint> =
        (0..64).map { index ->
            val angle = index / 64.0 * PI * 2
            point(200f + cos(angle).toFloat() * rx, 200f + sin(angle).toFloat() * ry, index * 8L)
        }
}
