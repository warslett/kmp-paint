package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    /** Draws the 1-px closed rectangle outline (x0, y0)-(x1, y1) with the Pencil. */
    private fun drawClosedRect(bitmap: CanvasBitmap, x0: Int, y0: Int, x1: Int, y1: Int, colour: Int) {
        Pencil.onMove(bitmap, x0, y0, x1, y0, colour)
        Pencil.onMove(bitmap, x1, y0, x1, y1, colour)
        Pencil.onMove(bitmap, x1, y1, x0, y1, colour)
        Pencil.onMove(bitmap, x0, y1, x0, y0, colour)
    }

    @Test
    fun fillingInsideAClosedPencilSquareFillsTheInteriorOnly() {
        val bitmap = CanvasBitmap(12, 12)
        drawClosedRect(bitmap, 2, 2, 9, 9, black)
        Fill.onDown(bitmap, 5, 5, red)
        assertBitmapMatches(bitmap) { x, y ->
            val onOutline = (y == 2 || y == 9) && x in 2..9 || (x == 2 || x == 9) && y in 2..9
            when {
                onOutline -> black
                x in 3..8 && y in 3..8 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun clickingTheOutlineRecoloursOnlyTheOutlineLoop() {
        val bitmap = CanvasBitmap(12, 12)
        drawClosedRect(bitmap, 2, 2, 9, 9, black)
        Fill.onDown(bitmap, 2, 2, red)
        assertBitmapMatches(bitmap) { x, y ->
            val onOutline = (y == 2 || y == 9) && x in 2..9 || (x == 2 || x == 9) && y in 2..9
            if (onOutline) red else WHITE
        }
    }

    @Test
    fun aLineSplitsTheCanvasAndFillStopsExactlyAtIt() {
        val bitmap = CanvasBitmap(10, 10)
        Pencil.onMove(bitmap, 5, 0, 5, 9, black)
        Fill.onDown(bitmap, 2, 5, red)
        assertBitmapMatches(bitmap) { x, _ ->
            when {
                x < 5 -> red
                x == 5 -> black
                else -> WHITE
            }
        }
    }

    @Test
    fun fillRespectsFourConnectivityAcrossADiagonalBoundary() {
        val bitmap = CanvasBitmap(7, 7)
        // A 45-degree, 8-connected Pencil line: a 4-wall but an 8-gap. An
        // 8-connected fill would leak through it; the 4-connected fill must not.
        Pencil.onMove(bitmap, 0, 6, 6, 0, black)
        Fill.onDown(bitmap, 4, 1, red)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x + y == 6 -> black
                x + y < 6 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun fillingWithTheSameColourAlreadyPresentIsANoOp() {
        val bitmap = CanvasBitmap(6, 6)
        Fill.onDown(bitmap, 3, 3, WHITE)
        assertTrue(bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun aRegionTouchingTheCanvasEdgeFillsUpToTheEdge() {
        val bitmap = CanvasBitmap(6, 6)
        drawClosedRect(bitmap, 2, 1, 5, 4, black)
        Fill.onDown(bitmap, 0, 0, red)
        assertBitmapMatches(bitmap) { x, y ->
            val onOutline = (y == 1 || y == 4) && x in 2..5 || (x == 2 || x == 5) && y in 1..4
            when {
                onOutline -> black
                x in 3..4 && y in 2..3 -> WHITE
                else -> red
            }
        }
    }

    @Test
    fun fillingASinglePixelRegionRecoloursJustThatPixel() {
        val bitmap = CanvasBitmap(5, 5)
        bitmap[2, 2] = red
        Fill.onDown(bitmap, 2, 2, blue)
        assertBitmapMatches(bitmap) { x, y -> if (x == 2 && y == 2) blue else WHITE }
    }

    @Test
    fun outOfBoundsClicksDoNotThrowAndChangeNothing() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, -1, 0, red)
        Fill.onDown(bitmap, 5, 0, red)
        Fill.onDown(bitmap, 0, -1, red)
        Fill.onDown(bitmap, 0, 5, red)
        assertTrue(bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun onMoveIsANoOp() {
        val bitmap = CanvasBitmap(8, 8)
        Fill.onMove(bitmap, 0, 0, 7, 7, red)
        assertTrue(bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun fillingABlankCanvasRecoloursEveryPixel() {
        val bitmap = CanvasBitmap(800, 600)
        Fill.onDown(bitmap, 400, 300, red)
        assertTrue(bitmap.copyPixels().all { it == red })
    }
}
