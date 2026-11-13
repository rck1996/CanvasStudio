package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceDabCursorTest {
    @Test
    fun coarseAndFinePartitionsProduceTheSameDabLocationsAndSeeds() {
        val coarse = sampleLine(listOf(9f, 15f))
        val fine = sampleLine(List(16) { 1.5f })

        assertEquals(6, coarse.size)
        assertEquals(coarse.size, fine.size)
        coarse.indices.forEach { index ->
            assertEquals(4f * (index + 1), coarse[index].position, EPSILON)
            assertEquals(coarse[index].position, fine[index].position, EPSILON)
            assertEquals(index + 1, coarse[index].seed)
            assertEquals(coarse[index].seed, fine[index].seed)
        }
    }

    @Test
    fun subStepDistanceCarriesAcrossStationaryEventsWithoutConsumingSeeds() {
        val cursor = DistanceDabCursor()

        assertNull(cursor.advance(0f, 4f))
        assertNull(cursor.advance(1f, 4f))
        assertNull(cursor.advance(0f, 8f))
        assertNull(cursor.advance(2f, 4f))

        val first = checkNotNull(cursor.advance(3f, 4f))
        assertEquals(1, first.count)
        assertEquals(1, first.firstSeed)
        assertEquals(1f / 3f, first.fractionAt(0), EPSILON)

        assertNull(cursor.advance(0f, 4f))
        val second = checkNotNull(cursor.advance(2f, 4f))
        assertEquals(1, second.count)
        assertEquals(2, second.firstSeed)
        assertEquals(1f, second.fractionAt(0), EPSILON)
    }

    @Test
    fun spacingChangesPreserveFractionalPhase() {
        val cursor = DistanceDabCursor()

        assertNull(cursor.advance(2f, 8f)) // One quarter of the old step.
        val tighter = checkNotNull(cursor.advance(4f, 4f))
        assertEquals(1, tighter.count)
        assertEquals(0.75f, tighter.fractionAt(0), EPSILON)

        // The remaining quarter phase leaves six pixels to travel at an 8 px step.
        val wider = checkNotNull(cursor.advance(6f, 8f))
        assertEquals(1, wider.count)
        assertEquals(1f, wider.fractionAt(0), EPSILON)
    }

    @Test
    fun extremeSegmentProducesAtMost48DabsInSegmentOrder() {
        val span = checkNotNull(DistanceDabCursor().advance(1_000_000f, 0.25f))

        assertEquals(48, span.count)
        var previous = 0f
        repeat(span.count) { index ->
            val fraction = span.fractionAt(index)
            assertTrue("Dab ${index + 1}: fraction $fraction after $previous", fraction > previous && fraction <= 1f)
            previous = fraction
        }
    }

    @Test
    fun distanceDabsCoverFeltAtAnySizeAndBroadWetOrBristleBrushes() {
        val eligibleKinds = setOf(BrushKind.BRISTLE, BrushKind.WATERCOLOR, BrushKind.OIL)

        for (kind in BrushKind.entries) {
            for (sizePx in listOf(47.99f, 48f, 96f)) {
                // Supply a plain color so the JVM test never calls Android's Color.rgb.
                val settings = BrushSettings(sizePx = sizePx, color = 0, kind = kind)
                for (tool in DrawingTool.entries) {
                    assertEquals(
                        "$tool / $kind / $sizePx px",
                        tool == DrawingTool.BRUSH &&
                            (kind == BrushKind.MARKER || kind in eligibleKinds && sizePx >= 48f),
                        usesDistanceDabs(settings, tool),
                    )
                }
            }
        }
    }

    private fun sampleLine(partitions: List<Float>): List<SampledDab> = buildList {
        val cursor = DistanceDabCursor()
        var start = 0f
        for (distance in partitions) {
            cursor.advance(distance, 4f)?.let { span ->
                repeat(span.count) { index ->
                    add(SampledDab(start + distance * span.fractionAt(index), span.firstSeed + index))
                }
            }
            start += distance
        }
    }

    private data class SampledDab(val position: Float, val seed: Int)

    private companion object {
        const val EPSILON = 0.00001f
    }
}
