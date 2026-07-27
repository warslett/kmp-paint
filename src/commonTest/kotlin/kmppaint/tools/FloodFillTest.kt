package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FloodFillTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun collect(bitmap: CanvasBitmap, startX: Int, startY: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        forEachPixelInFloodFillRegion(bitmap, startX, startY) { x, y -> points.add(x to y) }
        return points
    }

    @Test
    fun wholeCanvasSingleColourFillsEveryPixelExactlyOnce() {
        val bitmap = CanvasBitmap(6, 5)
        val plotted = collect(bitmap, 3, 2)
        assertEquals(bitmap.width * bitmap.height, plotted.size)
        assertEquals(plotted.toSet().size, plotted.size, "no pixel plotted twice")
        val expected = buildSet {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) add(x to y)
        }
        assertEquals(expected, plotted.toSet())
    }

    @Test
    fun fillInsideAClosedRectangleOutlinePlotsExactlyTheInteriorNotTheBorderOrOutside() {
        // A 10x10 canvas with a red 1px rectangular border from (2,2) to (7,7)
        // on a white background. Interior is (3,3)..(6,6): a 4x4 block.
        val bitmap = CanvasBitmap(10, 10)
        for (x in 2..7) {
            bitmap[x, 2] = red
            bitmap[x, 7] = red
        }
        for (y in 2..7) {
            bitmap[2, y] = red
            bitmap[7, y] = red
        }

        val plotted = collect(bitmap, 4, 4).toSet()

        val expectedInterior = buildSet {
            for (y in 3..6) for (x in 3..6) add(x to y)
        }
        assertEquals(expectedInterior, plotted)
        // Sanity: border pixels and outside-the-shape pixels are excluded.
        for (borderPixel in listOf(2 to 2, 7 to 7, 4 to 2, 2 to 5)) {
            assertTrue(borderPixel !in plotted, "border pixel $borderPixel must not be filled")
        }
        for (outsidePixel in listOf(0 to 0, 9 to 9, 0 to 9, 9 to 0)) {
            assertTrue(outsidePixel !in plotted, "outside pixel $outsidePixel must not be filled")
        }
    }

    @Test
    fun fillStaysConfinedWhenTheOutlineTouchesTheCanvasEdge() {
        // Rectangle whose left border sits exactly on the canvas edge (x = 0),
        // so there is no "outside the shape but still in bounds" pixel to the
        // left of the border.
        val bitmap = CanvasBitmap(8, 8)
        for (x in 0..5) {
            bitmap[x, 1] = red
            bitmap[x, 5] = red
        }
        for (y in 1..5) {
            bitmap[0, y] = red
            bitmap[5, y] = red
        }

        val plotted = collect(bitmap, 2, 3).toSet()
        val expectedInterior = buildSet {
            for (y in 2..4) for (x in 1..4) add(x to y)
        }
        assertEquals(expectedInterior, plotted)
        // Nothing beyond the right border (x = 6, 7) is touched.
        for (x in 6..7) {
            for (y in 0 until bitmap.height) {
                assertTrue(x to y !in plotted, "pixel ($x, $y) is outside the shape")
            }
        }
    }

    @Test
    fun fillFollowsAnIrregularStaircaseBoundaryWithoutLeaking() {
        // An L-shaped red boundary enclosing a small white interior region,
        // including a diagonal "staircase" segment, on a 9x9 canvas.
        // Boundary path (closed loop), 4-connected pixel by pixel:
        val boundary = listOf(
            2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2,
            6 to 3, 6 to 4,
            5 to 4, 5 to 5,
            4 to 5, 4 to 6,
            3 to 6, 2 to 6,
            2 to 5, 2 to 4, 2 to 3,
        )
        val bitmap = CanvasBitmap(9, 9)
        for ((x, y) in boundary) bitmap[x, y] = red

        val plotted = collect(bitmap, 4, 3).toSet()

        // None of the plotted pixels are boundary pixels, and every plotted
        // pixel is strictly inside the loop's bounding box.
        for (b in boundary) assertTrue(b !in plotted, "boundary pixel $b must not be filled")
        for ((x, y) in plotted) {
            assertTrue(x in 3..5 && y in 3..5, "plotted pixel ($x, $y) should be inside the bounding box")
        }
        assertTrue(plotted.isNotEmpty())
    }

    @Test
    fun disjointRegionsOfTheSameColourAreNotBothFilled() {
        val bitmap = CanvasBitmap(10, 3)
        // Two separate red blobs on a white background, columns 0-2 and 5-7.
        for (x in 0..2) bitmap[x, 1] = red
        for (x in 5..7) bitmap[x, 1] = red

        val plotted = collect(bitmap, 1, 1).toSet()
        assertEquals(setOf(0 to 1, 1 to 1, 2 to 1), plotted)
    }

    @Test
    fun clickingAnIsolatedSinglePixelPlotsExactlyThatPixel() {
        val bitmap = CanvasBitmap(5, 5)
        bitmap[2, 2] = red
        val plotted = collect(bitmap, 2, 2)
        assertEquals(listOf(2 to 2), plotted)
    }

    @Test
    fun clickingOutOfBoundsCoordinatesInvokesPlotZeroTimes() {
        val bitmap = CanvasBitmap(4, 4)
        for ((x, y) in listOf(-1 to 0, 0 to -1, 4 to 0, 0 to 4, -5 to -5, 100 to 100)) {
            val plotted = collect(bitmap, x, y)
            assertTrue(plotted.isEmpty(), "($x, $y) is out of bounds; plot must not be called")
        }
    }

    @Test
    fun largeUniformRegionCompletesAndPlotsWidthTimesHeightPixels() {
        val bitmap = CanvasBitmap(200, 200)
        val plotted = collect(bitmap, 0, 0)
        assertEquals(200 * 200, plotted.size)
        assertEquals(plotted.toSet().size, plotted.size, "no pixel plotted twice")
    }

    @Test
    fun eachMatchingPixelIsPlottedExactlyOnce() {
        val bitmap = CanvasBitmap(12, 12)
        for (x in 3..8) {
            bitmap[x, 3] = red
            bitmap[x, 8] = red
        }
        for (y in 3..8) {
            bitmap[3, y] = red
            bitmap[8, y] = red
        }
        val counts = mutableMapOf<Pair<Int, Int>, Int>()
        forEachPixelInFloodFillRegion(bitmap, 5, 5) { x, y ->
            counts[x to y] = (counts[x to y] ?: 0) + 1
        }
        assertTrue(counts.values.all { it == 1 }, "every plotted pixel must be plotted exactly once")
    }

    @Test
    fun onlyPixelsExactlyMatchingTheStartingColourAreConsidered() {
        val bitmap = CanvasBitmap(5, 5, fill = WHITE)
        bitmap[2, 2] = blue
        // A red neighbour must not be swept in even though it's adjacent.
        bitmap[2, 3] = red
        val plotted = collect(bitmap, 2, 2)
        assertEquals(listOf(2 to 2), plotted)
    }
}
