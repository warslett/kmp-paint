package kmppaint.tools

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LinesTest {
    private fun collect(fromX: Int, fromY: Int, toX: Int, toY: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        forEachPixelOnLine(fromX, fromY, toX, toY) { x, y -> points.add(x to y) }
        return points
    }

    @Test
    fun degenerateSegmentPlotsExactlyOnePixelOnce() {
        assertEquals(listOf(3 to 4), collect(3, 4, 3, 4))
    }

    @Test
    fun horizontalSegmentPlotsEveryPixelAlongTheAxisInOrder() {
        assertEquals(
            listOf(1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
            collect(1, 2, 5, 2),
        )
        assertEquals(
            listOf(5 to 2, 4 to 2, 3 to 2, 2 to 2, 1 to 2),
            collect(5, 2, 1, 2),
        )
    }

    @Test
    fun verticalSegmentPlotsEveryPixelAlongTheAxisInOrder() {
        assertEquals(
            listOf(2 to 1, 2 to 2, 2 to 3, 2 to 4),
            collect(2, 1, 2, 4),
        )
        assertEquals(
            listOf(2 to 4, 2 to 3, 2 to 2, 2 to 1),
            collect(2, 4, 2, 1),
        )
    }

    @Test
    fun diagonal45PlotsTheExpectedPixels() {
        assertEquals(
            listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3),
            collect(0, 0, 3, 3),
        )
        assertEquals(
            listOf(0 to 3, 1 to 2, 2 to 1, 3 to 0),
            collect(0, 3, 3, 0),
        )
    }

    @Test
    fun plottedLinesAreContinuousFromStartToEnd() {
        val segments = listOf(
            intArrayOf(0, 0, 5, 2), // shallow, left-to-right
            intArrayOf(5, 2, 0, 0), // shallow, right-to-left
            intArrayOf(0, 0, 2, 5), // steep, top-to-bottom
            intArrayOf(2, 5, 0, 0), // steep, bottom-to-top
            intArrayOf(7, 1, 1, 6), // down-left
            intArrayOf(1, 6, 7, 1), // up-right
            intArrayOf(0, 0, 4, 2), // passes exactly through (2, 1): a tie case
            intArrayOf(4, 2, 0, 0),
            intArrayOf(0, 0, 799, 599), // long, whole-canvas diagonal
            intArrayOf(799, 599, 0, 0),
        )
        for ((fromX, fromY, toX, toY) in segments) {
            val points = collect(fromX, fromY, toX, toY)
            val name = "($fromX, $fromY) -> ($toX, $toY)"
            assertEquals(fromX to fromY, points.first(), "$name: first plotted pixel must be the start")
            assertEquals(toX to toY, points.last(), "$name: last plotted pixel must be the end")
            for (i in 1 until points.size) {
                val (px, py) = points[i - 1]
                val (cx, cy) = points[i]
                assertTrue(
                    abs(cx - px) <= 1 && abs(cy - py) <= 1,
                    "$name: gap between ${points[i - 1]} and ${points[i]}",
                )
                assertNotEquals(points[i - 1], points[i], "$name: consecutive duplicate at index $i")
            }
        }
    }

    @Test
    fun reversedSegmentPlotsTheSameSetOfPixels() {
        val segments = listOf(
            intArrayOf(0, 0, 4, 2), // tie case: naive Bresenham is direction-dependent here
            intArrayOf(1, 2, 7, 5),
            intArrayOf(2, 0, 3, 9),
            intArrayOf(9, 4, 0, 4),
            intArrayOf(0, 0, 799, 599),
        )
        for ((ax, ay, bx, by) in segments) {
            assertEquals(
                collect(ax, ay, bx, by).toSet(),
                collect(bx, by, ax, ay).toSet(),
                "($ax, $ay) -> ($bx, $by) and its reverse must plot the same pixels",
            )
        }
    }
}
