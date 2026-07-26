package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val black = 0xFF000000.toInt()
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun assertBitmapMatches(bitmap: CanvasBitmap, want: (x: Int, y: Int) -> Int) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(want(x, y), bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onDownOnAnEmptyCanvasFillsEveryPixel() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, red)
        assertBitmapMatches(bitmap) { _, _ -> red }
    }

    @Test
    fun aClosedAxisAlignedPencilRectangleContainsTheFill() {
        val bitmap = CanvasBitmap(9, 7)
        Pencil.onMove(bitmap, 2, 1, 6, 1, blue)
        Pencil.onMove(bitmap, 6, 1, 6, 5, blue)
        Pencil.onMove(bitmap, 6, 5, 2, 5, blue)
        Pencil.onMove(bitmap, 2, 5, 2, 1, blue)
        Fill.onDown(bitmap, 4, 3, red)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x in 3..5 && y in 2..4 -> red // interior
                x in 2..6 && y in 1..5 -> blue // boundary
                else -> WHITE // exterior
            }
        }
    }

    @Test
    fun aDiagonalOnlyPencilBoundaryContainsTheFill() {
        // A diamond whose edges are 45-degree Bresenham lines: boundary
        // pixels touch diagonally only, so only a 4-connected fill stays in.
        val bitmap = CanvasBitmap(11, 11)
        Pencil.onMove(bitmap, 5, 1, 9, 5, blue)
        Pencil.onMove(bitmap, 9, 5, 5, 9, blue)
        Pencil.onMove(bitmap, 5, 9, 1, 5, blue)
        Pencil.onMove(bitmap, 1, 5, 5, 1, blue)
        Fill.onDown(bitmap, 5, 5, red)
        assertBitmapMatches(bitmap) { x, y ->
            val distance = kotlin.math.abs(x - 5) + kotlin.math.abs(y - 5)
            when {
                distance < 4 -> red // interior, 25 px
                distance == 4 -> blue // boundary
                else -> WHITE // exterior
            }
        }
        assertEquals(25, bitmap.copyPixels().count { it == red })
    }

    @Test
    fun onDownWithTheTargetColourIsANoOpAndReturns() {
        // Without the target == colour guard this call would never return:
        // painted pixels would still match and be re-pushed forever.
        val bitmap = CanvasBitmap(4, 4)
        bitmap[0, 0] = blue
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertEquals(blue, bitmap[0, 0])
        assertEquals(15, bitmap.copyPixels().count { it == WHITE })
    }

    @Test
    fun onDownWithAnOutOfBoundsSeedIsANoOpAndDoesNotThrow() {
        val bitmap = CanvasBitmap(5, 3)
        for ((x, y) in listOf(-1 to 0, 0 to -1, 5 to 0, 0 to 3)) {
            Fill.onDown(bitmap, x, y, red)
        }
        assertBitmapMatches(bitmap) { _, _ -> WHITE }
    }

    @Test
    fun pixelsOfOtherColoursInsideTheRegionActAsEdgesAndSurvive() {
        val bitmap = CanvasBitmap(9, 7)
        Pencil.onMove(bitmap, 2, 1, 6, 1, black)
        Pencil.onMove(bitmap, 6, 1, 6, 5, black)
        Pencil.onMove(bitmap, 6, 5, 2, 5, black)
        Pencil.onMove(bitmap, 2, 5, 2, 1, black)
        bitmap[5, 4] = blue
        Fill.onDown(bitmap, 3, 2, red)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x == 5 && y == 4 -> blue // the enclosed dot is never recoloured
                x in 3..5 && y in 2..4 -> red // the fill wraps around the dot
                x in 2..6 && y in 1..5 -> black // boundary
                else -> WHITE
            }
        }
    }

    @Test
    fun theCanvasEdgeBoundsTheRegionExactlyWithNoWraparound() {
        // The filled region is the border column itself: a row-major indexing
        // slip would leak into the last column of the row above/below.
        val bitmap = CanvasBitmap(5, 5)
        Pencil.onMove(bitmap, 1, 0, 1, 4, blue)
        Fill.onDown(bitmap, 0, 2, red)
        assertBitmapMatches(bitmap) { x, _ ->
            when (x) {
                0 -> red
                1 -> blue
                else -> WHITE
            }
        }
    }

    @Test
    fun onMoveIsANoOp() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onMove(bitmap, 0, 0, 4, 4, red)
        assertBitmapMatches(bitmap) { _, _ -> WHITE }
    }

    @Test
    fun aVerticalPencilLineSplitsTheBitmapAndExactlyOneSideFills() {
        val bitmap = CanvasBitmap(7, 5)
        Pencil.onMove(bitmap, 3, 0, 3, 4, blue)
        Fill.onDown(bitmap, 5, 2, red)
        assertBitmapMatches(bitmap) { x, _ ->
            when {
                x < 3 -> WHITE // the other side is untouched
                x == 3 -> blue // the line
                else -> red // the filled side, top to bottom
            }
        }
    }
}
