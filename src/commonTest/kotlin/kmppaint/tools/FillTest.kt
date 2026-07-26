package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF00FF00.toInt()

    private fun assertBitmapMatches(bitmap: CanvasBitmap, want: (x: Int, y: Int) -> Int) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(want(x, y), bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    private fun pencilRect(bitmap: CanvasBitmap, x0: Int, y0: Int, x1: Int, y1: Int, colour: Int) {
        Pencil.onMove(bitmap, x0, y0, x1, y0, colour)
        Pencil.onMove(bitmap, x0, y1, x1, y1, colour)
        Pencil.onMove(bitmap, x0, y0, x0, y1, colour)
        Pencil.onMove(bitmap, x1, y0, x1, y1, colour)
    }

    @Test
    fun fillsTheInteriorOfAClosedPencilRectangleWithoutLeakingPastTheOutline() {
        val bitmap = CanvasBitmap(7, 7)
        pencilRect(bitmap, 1, 1, 5, 5, red)
        Fill.onDown(bitmap, 3, 3, blue)
        // Interior (2..4 × 2..4) is blue, outline red, outside white.
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x in 2..4 && y in 2..4 -> blue
                x in 1..5 && y in 1..5 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun fillDoesNotLeakPastTheBoundaryOntoUntouchedPixels() {
        val bitmap = CanvasBitmap(7, 7)
        pencilRect(bitmap, 1, 1, 5, 5, red)
        Fill.onDown(bitmap, 3, 3, blue)
        // Pixels strictly outside the rectangle are untouched white.
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (x !in 1..5 && y !in 1..5) {
                    assertEquals(WHITE, bitmap[x, y], "outside pixel ($x, $y) was leaked into")
                }
            }
        }
    }

    @Test
    fun replacesOnlyTheClickedColourLeavingDifferentColourRegionsAlone() {
        val bitmap = CanvasBitmap(5, 5)
        for (x in 0..2) for (y in 0 until 5) bitmap[x, y] = red
        for (x in 3..4) for (y in 0 until 5) bitmap[x, y] = blue
        Fill.onDown(bitmap, 0, 0, green)
        assertBitmapMatches(bitmap) { x, y -> if (x in 0..2) green else blue }
    }

    @Test
    fun clickingTheActiveColourOnARegionOfThatColourIsANoOp() {
        val bitmap = CanvasBitmap(6, 6)
        for (x in 0 until 6) for (y in 0 until 6) bitmap[x, y] = WHITE
        val before = bitmap.copyPixels().toList()
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertEquals(before, bitmap.copyPixels().toList(), "fill on same-colour region must not mutate any pixel")
    }

    @Test
    fun outOfBoundsClickDoesNotThrowAndLeavesTheBitmapUnchanged() {
        val bitmap = CanvasBitmap(4, 4)
        val before = bitmap.copyPixels().toList()
        Fill.onDown(bitmap, -1, 0, red)
        Fill.onDown(bitmap, 0, -1, red)
        Fill.onDown(bitmap, 4, 0, red)
        Fill.onDown(bitmap, 0, 4, red)
        assertEquals(before, bitmap.copyPixels().toList(), "out-of-bounds clicks must not mutate any pixel")
    }

    @Test
    fun fillsTheWholeCanvasWhenSeededAtTheCentreOfAnAllWhiteCanvas() {
        val bitmap = CanvasBitmap(10, 10)
        Fill.onDown(bitmap, 5, 5, red)
        assertBitmapMatches(bitmap) { _, _ -> red }
    }

    @Test
    fun diagonallyTouchingSameColourRegionsAreNotConnectedUnder4Connectivity() {
        val bitmap = CanvasBitmap(3, 3)
        // Checkerboard: red at corners and centre, white elsewhere. Same-colour
        // red pixels touch only at corners, so a fill seeded at one changes only
        // that pixel.
        val reds = setOf(0 to 0, 2 to 0, 0 to 2, 2 to 2, 1 to 1)
        for ((x, y) in reds) bitmap[x, y] = red
        Fill.onDown(bitmap, 1, 1, blue)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = when {
                    x == 1 && y == 1 -> blue
                    x to y in reds -> red
                    else -> WHITE
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fillOnMoveIsANoOpEvenOnAPopulatedBitmap() {
        val bitmap = CanvasBitmap(5, 5)
        pencilRect(bitmap, 1, 1, 3, 3, red)
        val before = bitmap.copyPixels().toList()
        Fill.onMove(bitmap, 2, 2, 4, 4, blue)
        Fill.onMove(bitmap, 0, 0, 4, 4, blue)
        assertEquals(before, bitmap.copyPixels().toList(), "dragging the fill tool must not paint")
    }

    @Test
    fun reFillingARegionThatWasJustFilledReplacesOnlyThatRegion() {
        // Fill a red-outlined rectangle interior with blue, then fill it again
        // with green: the outline stays red, the interior flips to green.
        val bitmap = CanvasBitmap(7, 7)
        pencilRect(bitmap, 1, 1, 5, 5, red)
        Fill.onDown(bitmap, 3, 3, blue)
        Fill.onDown(bitmap, 3, 3, green)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x in 2..4 && y in 2..4 -> green
                x in 1..5 && y in 1..5 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun fillsIterativelyWithoutOverflowingTheStackOnAWholeCanvasFill() {
        // Regression-ish: a naive recursive fill would SOE on a long thin region.
        // Use a 1×400 strip and fill it: the iteration must complete without throwing.
        val bitmap = CanvasBitmap(1, 400)
        Fill.onDown(bitmap, 0, 0, red)
        for (y in 0 until bitmap.height) {
            assertEquals(red, bitmap[0, y], "strip pixel y=$y")
        }
    }

    @Test
    fun fillSeededOnTheOutlinePicksUpOnlyTheOutlineColourRegion() {
        // Clicking on the outline (red) with active green recolours every
        // 4-connected outline pixel, leaving the white interior white.
        val bitmap = CanvasBitmap(7, 7)
        pencilRect(bitmap, 1, 1, 5, 5, red)
        Fill.onDown(bitmap, 1, 1, green)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val onOutline = x in 1..5 && y in 1..5 && (x == 1 || x == 5 || y == 1 || y == 5)
                val want = if (onOutline) green else WHITE
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
        assertTrue(bitmap.copyPixels().any { it == WHITE }, "interior pixels remain white")
    }
}