package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
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
}
