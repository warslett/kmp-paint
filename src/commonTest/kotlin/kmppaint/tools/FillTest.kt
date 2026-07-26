package kmppaint.tools

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF008000.toInt()

    private fun assertBitmapMatches(bitmap: CanvasBitmap, want: (x: Int, y: Int) -> Int) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(want(x, y), bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun seedOnAnEmptyCanvasFillsEverything() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, red)
        assertBitmapMatches(bitmap) { _, _ -> red }
    }

    @Test
    fun aClosedAxisAlignedRectangleContainsTheFill() {
        val bitmap = CanvasBitmap(9, 9)
        // Blue rectangle border from (2, 2) to (6, 6).
        Pencil.onMove(bitmap, 2, 2, 6, 2, blue)
        Pencil.onMove(bitmap, 6, 2, 6, 6, blue)
        Pencil.onMove(bitmap, 6, 6, 2, 6, blue)
        Pencil.onMove(bitmap, 2, 6, 2, 2, blue)
        Fill.onDown(bitmap, 4, 4, red)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x in 3..5 && y in 3..5 -> red
                x in 2..6 && y in 2..6 -> blue
                else -> WHITE
            }
        }
    }

    @Test
    fun aDiagonalOnlyBoundaryContainsTheFill() {
        val bitmap = CanvasBitmap(9, 9)
        // A diamond centred at (4, 4): each 45-degree Bresenham edge plots
        // pixels that touch only diagonally, yet no fill may leak through.
        Pencil.onMove(bitmap, 4, 0, 8, 4, blue)
        Pencil.onMove(bitmap, 8, 4, 4, 8, blue)
        Pencil.onMove(bitmap, 4, 8, 0, 4, blue)
        Pencil.onMove(bitmap, 0, 4, 4, 0, blue)
        Fill.onDown(bitmap, 4, 4, red)
        assertBitmapMatches(bitmap) { x, y ->
            val distance = abs(x - 4) + abs(y - 4)
            when {
                distance < 4 -> red
                distance == 4 -> blue
                else -> WHITE
            }
        }
    }

    @Test
    fun clickingARegionThatAlreadyHasTheFillColourIsANoOpAndReturns() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertBitmapMatches(bitmap) { _, _ -> WHITE }
    }

    @Test
    fun anOutOfBoundsSeedIsANoOpAndDoesNotThrow() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, -1, 0, red)
        Fill.onDown(bitmap, 5, 0, red)
        Fill.onDown(bitmap, 0, -1, red)
        Fill.onDown(bitmap, 0, 5, red)
        assertBitmapMatches(bitmap) { _, _ -> WHITE }
    }

    @Test
    fun otherColoursInsideTheRegionAreEdges() {
        val bitmap = CanvasBitmap(9, 9)
        Pencil.onMove(bitmap, 2, 2, 6, 2, blue)
        Pencil.onMove(bitmap, 6, 2, 6, 6, blue)
        Pencil.onMove(bitmap, 6, 6, 2, 6, blue)
        Pencil.onMove(bitmap, 2, 6, 2, 2, blue)
        Pencil.onDown(bitmap, 5, 3, green) // a foreign pixel inside the rectangle
        Fill.onDown(bitmap, 4, 4, red)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x == 5 && y == 3 -> green // wrapped around, never recoloured
                x in 3..5 && y in 3..5 -> red
                x in 2..6 && y in 2..6 -> blue
                else -> WHITE
            }
        }
    }

    @Test
    fun theCanvasEdgeBoundsTheRegionExactlyWithoutWraparound() {
        val bitmap = CanvasBitmap(5, 5)
        Pencil.onMove(bitmap, 0, 2, 4, 2, blue) // horizontal wall, edge to edge
        Fill.onDown(bitmap, 0, 4, red) // seed on the bottom-left corner
        assertBitmapMatches(bitmap) { _, y ->
            when {
                y < 2 -> WHITE
                y == 2 -> blue
                else -> red // border pixels included; nothing wrapped around
            }
        }
    }

    @Test
    fun onMoveIsANoOp() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, red)
        val before = bitmap.copyPixels()
        Fill.onMove(bitmap, 0, 0, 4, 4, blue)
        Fill.onMove(bitmap, 4, 4, 0, 0, blue)
        assertTrue(before.contentEquals(bitmap.copyPixels()))
    }

    @Test
    fun fillingOneHalfOfABitmapSplitByAPencilLineChangesOnlyThatSide() {
        val bitmap = CanvasBitmap(7, 5)
        Pencil.onMove(bitmap, 3, 0, 3, 4, blue) // vertical wall, edge to edge
        Fill.onDown(bitmap, 5, 2, red)
        assertBitmapMatches(bitmap) { x, _ ->
            when {
                x < 3 -> WHITE
                x == 3 -> blue
                else -> red
            }
        }
    }
}
