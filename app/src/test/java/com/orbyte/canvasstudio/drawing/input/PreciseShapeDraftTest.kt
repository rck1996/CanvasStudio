package com.orbyte.canvasstudio.drawing.input

import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.input.PreciseShapeDraft.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot

class PreciseShapeDraftTest {
    @Test fun rectangleKeepsOppositeCornerAndRightAngles() {
        val rect = requireNotNull(PreciseShapeDraft.fromDrag(DrawingTool.RECTANGLE,
            Point(220f, 160f), Point(30f, 50f), 5f))
        assertEquals(0, rect.nearestHandle(Point(31f, 52f), 5f))
        assertNull(rect.nearestHandle(Point(100f, 100f), 5f))
        val resized = rect.moveHandle(0, Point(60f, 80f), 5f)
        assertEquals(listOf(Point(60f, 80f), Point(220f, 80f), Point(220f, 160f), Point(60f, 160f)),
            resized.vertices)
    }

    @Test fun triangleMovesOneVertexAndCannotCollapseIntoLine() {
        val triangle = requireNotNull(PreciseShapeDraft.fromDrag(DrawingTool.TRIANGLE,
            Point(10f, 20f), Point(100f, 150f), 5f))
        assertEquals(triangle, triangle.moveHandle(0, Point(62f, 150f), 5f))
        val moved = triangle.moveHandle(0, Point(71f, 29f), 5f)
        assertEquals(Point(71f, 29f), moved.vertices.first())
        assertEquals(triangle.vertices.drop(1), moved.vertices.drop(1))
    }

    @Test fun movingTheCenterPreservesShapeAndKeepsItInsideThePage() {
        val triangle = requireNotNull(PreciseShapeDraft.fromDrag(DrawingTool.TRIANGLE,
            Point(20f, 40f), Point(90f, 100f), 5f))
        val moved = triangle.translated(100f, -100f, pageWidth = 150f, pageHeight = 170f)
        assertEquals(triangle.vertices.map { Point(it.x + 60f, it.y - 40f) }, moved.vertices)
        assertEquals(Point(triangle.center.x + 60f, triangle.center.y - 40f), moved.center)
    }

    @Test fun lineKeepsEndpointOrderAndEllipseKeepsItsBoundingBox() {
        val line = requireNotNull(PreciseShapeDraft.fromDrag(DrawingTool.LINE,
            Point(5f, 80f), Point(60f, 80f), 5f))
        val inclined = line.moveHandle(1, Point(65f, 25f), 5f)
        assertEquals(listOf(Point(5f, 80f), Point(65f, 25f)), inclined.vertices)
        val ellipse = requireNotNull(PreciseShapeDraft.fromDrag(DrawingTool.ELLIPSE,
            Point(20f, 35f), Point(110f, 70f), 5f))
        val resized = ellipse.moveHandle(0, Point(30f, 40f), 5f)
        assertEquals(listOf(Point(30f, 40f), Point(110f, 40f), Point(110f, 70f), Point(30f, 70f)),
            resized.vertices)
    }

    @Test fun tiltedRectangleKeepsRotationAndPinnedCornerWhenResizing() {
        val vertices = listOf(Point(30f, 60f), Point(94f, 108f), Point(70f, 140f), Point(6f, 92f))
        val fit = QuickShapeContact.Result.Angular(QuickShapeRecognizer.Polygon(
            vertices.map { QuickShapeRecognizer.Vertex(it.x, it.y) },
        ))
        val draft = PreciseShapeDraft.fromRecognition(fit)
        val moved = draft.moveHandle(0, Point(25f, 35f), 5f)
        assertEquals(DrawingTool.RECTANGLE, moved.tool)
        assertEquals(Point(70f, 140f), moved.vertices[2])
        assertClose(Point(25f, 35f), moved.vertices[0])
        val (a, b, c) = moved.vertices
        val norm = hypot(b.x - a.x, b.y - a.y)
        assertEquals(.8f, (b.x - a.x) / norm, .001f)
        assertEquals(.6f, (b.y - a.y) / norm, .001f)
        assertEquals(0f, (b.x - a.x) * (c.x - b.x) + (b.y - a.y) * (c.y - b.y), .01f)
    }

    @Test fun tiltedEllipseExposesLocalAxesAndCanResizeWithoutChangingAngle() {
        val result = QuickShapeContact.Result.Rounded(QuickShapeRecognizer.Oval(40f, 70f, 160f, 130f, 37f))
        val draft = PreciseShapeDraft.fromRecognition(result)
        val original = requireNotNull(draft.ellipseFrame)
        assertClose(Point(100f, 100f), original.center)
        assertEquals(60f, original.radiusX, .001f)
        assertEquals(30f, original.radiusY, .001f)
        assertEquals(37f, original.rotationDegrees, .001f)

        val opposite = draft.vertices[2]
        val resized = draft.moveHandle(0, opposite, 6f)
        assertEquals(opposite, resized.vertices[2])
        val resizedFrame = requireNotNull(resized.ellipseFrame)
        assertEquals(3f, resizedFrame.radiusX, .001f)
        assertEquals(3f, resizedFrame.radiusY, .001f)
        assertEquals(37f, resizedFrame.rotationDegrees, .001f)
    }

    @Test fun angularAndLinearDraftsKeepVertexIdentity() {
        val points = listOf(QuickShapeRecognizer.Vertex(90f, 80f),
            QuickShapeRecognizer.Vertex(75f, 20f), QuickShapeRecognizer.Vertex(30f, 50f))
        val triangle = PreciseShapeDraft.fromRecognition(
            QuickShapeContact.Result.Angular(QuickShapeRecognizer.Polygon(points)),
        )
        assertEquals(DrawingTool.TRIANGLE, triangle.tool)
        assertEquals(points.map { Point(it.x, it.y) }, triangle.vertices)
        assertEquals(triangle, triangle.moveHandle(-1, Point(0f, 0f), 4f))
        assertNull(triangle.ellipseFrame)
        val line = PreciseShapeDraft.fromRecognition(
            QuickShapeContact.Result.Straight(QuickShapeRecognizer.Line(70f, 40f, 5f, 20f)),
        )
        assertEquals(listOf(Point(70f, 40f), Point(5f, 20f)), line.vertices)
    }

    private fun assertClose(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
