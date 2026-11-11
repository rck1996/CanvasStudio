package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderPathOptimizationTest {
    @Test fun checkerboardMatchesOriginal48PixelSquareColors() {
        val bitmap = CheckerboardTexture.create(Color.RED, Color.BLUE)
        try {
            assertEquals(96, bitmap.width)
            assertEquals(96, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(Color.RED, bitmap.getPixel(47, 47))
            assertEquals(Color.BLUE, bitmap.getPixel(48, 47))
            assertEquals(Color.BLUE, bitmap.getPixel(47, 48))
            assertEquals(Color.RED, bitmap.getPixel(48, 48))
            assertEquals(Color.RED, bitmap.getPixel(95, 95))
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun defaultSymmetryDoesNotCopyTheStrokeOrItsInputPoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = DrawingView(context)
            view.configureDocument(1024, 1024)
            val points = List(1_000) { index ->
                StrokePoint(
                    30f + index * 0.25f,
                    80f + index * 0.1f,
                    .6f, .15f, index.toLong() * 12L,
                )
            }
            val original = StrokeCommand(
                points = points,
                tool = DrawingTool.BRUSH,
                settings = BrushSettings(kind = BrushKind.INK),
            )
            val unmodified = view.debugSymmetryCommandsForTest(original)
            assertEquals(1, unmodified.size)
            assertSame("Identity symmetry should not clone commands", original, unmodified[0])
            assertSame("Input point list must be retained", points, (unmodified[0] as StrokeCommand).points)
            view.setRadialSymmetry(4)
            val radial = view.debugSymmetryCommandsForTest(original)
            assertEquals(4, radial.size)
            assertNotSame(original, radial[0])
            assertEquals(original.id, (radial[0] as StrokeCommand).id)
        }
    }

    @Test fun symmetryCacheIsInvalidatedOnlyWhenGeometryOrModeChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = DrawingView(context)
            val initial = view.debugSymmetryMatricesForTest()
            assertEquals(1, initial.size)
            assertSame(initial, view.debugSymmetryMatricesForTest())
            view.setRadialSymmetry(8)
            val radial = view.debugSymmetryMatricesForTest()
            assertNotSame(initial, radial)
            assertEquals(8, radial.size)
            assertSame(radial, view.debugSymmetryMatricesForTest())
            view.configureDocument(960, 1280)
            val resized = view.debugSymmetryMatricesForTest()
            assertNotSame(radial, resized)
            assertEquals(8, resized.size)
            view.setVerticalSymmetry(true)
            val mirrored = view.debugSymmetryMatricesForTest()
            assertEquals(2, mirrored.size)
            assertSame(mirrored, view.debugSymmetryMatricesForTest())
        }
    }
}
