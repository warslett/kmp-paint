package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun fillingBlankCanvasRecoloursEveryPixel() {
        val bitmap = CanvasBitmap(6, 5)
        Fill.onDown(bitmap, 2, 2, red)
        assertTrue(bitmap.copyPixels().all { it == red })
    }

    @Test
    fun fillInsideClosedPencilSquareColoursOnlyTheInterior() {
        val bitmap = CanvasBitmap(9, 9)
        drawSquareOutline(bitmap)
        Fill.onDown(bitmap, 4, 4, red)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = when {
                    x in 3..5 && y in 3..5 -> red
                    x in 2..6 && y in 2..6 -> blue
                    else -> WHITE
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fillOutsideClosedPencilSquareColoursEverythingExceptTheInterior() {
        val bitmap = CanvasBitmap(9, 9)
        drawSquareOutline(bitmap)
        Fill.onDown(bitmap, 0, 0, red)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = when {
                    x in 3..5 && y in 3..5 -> WHITE
                    x in 2..6 && y in 2..6 -> blue
                    else -> red
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fillDoesNotLeakThroughDiagonallyTouchingBarrierPixels() {
        val bitmap = CanvasBitmap(3, 3)
        // Barriers touching only at a corner seal (0, 0) off from the rest.
        bitmap[1, 0] = blue
        bitmap[0, 1] = blue
        Fill.onDown(bitmap, 0, 0, red)
        assertEquals(red, bitmap[0, 0])
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (x to y !in setOf(0 to 0, 1 to 0, 0 to 1)) {
                    assertEquals(WHITE, bitmap[x, y], "unreachable pixel ($x, $y)")
                }
            }
        }
    }

    @Test
    fun fillInsideDiagonalPencilDiamondStaysContained() {
        val bitmap = CanvasBitmap(11, 11)
        // A diamond whose sides are pure 45-degree Bresenham diagonals.
        Pencil.onMove(bitmap, 5, 1, 9, 5, blue)
        Pencil.onMove(bitmap, 9, 5, 5, 9, blue)
        Pencil.onMove(bitmap, 5, 9, 1, 5, blue)
        Pencil.onMove(bitmap, 1, 5, 5, 1, blue)
        Fill.onDown(bitmap, 5, 5, red)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val distance = kotlin.math.abs(x - 5) + kotlin.math.abs(y - 5)
                val want = when {
                    distance <= 3 -> red
                    distance == 4 -> blue
                    else -> WHITE
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fillingAPixelAlreadyInTheFillColourIsANoOp() {
        val bitmap = CanvasBitmap(4, 4)
        bitmap[1, 1] = red
        val before = bitmap.copyPixels()
        Fill.onDown(bitmap, 1, 1, red)
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertTrue(before.contentEquals(bitmap.copyPixels()))
    }

    @Test
    fun fillingAtOutOfBoundsCoordinatesIsANoOpAndDoesNotThrow() {
        val bitmap = CanvasBitmap(4, 4)
        for ((x, y) in listOf(-1 to 0, 0 to -1, 4 to 0, 3 to 4, -7 to -7)) {
            Fill.onDown(bitmap, x, y, red)
        }
        assertTrue(bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun fillStopsAtCanvasEdgeAndBoundaryLine() {
        val bitmap = CanvasBitmap(6, 5)
        Pencil.onMove(bitmap, 3, 0, 3, 4, blue)
        Fill.onDown(bitmap, 0, 2, red)
        for (y in 0 until bitmap.height) {
            for (x in 0..2) {
                assertEquals(red, bitmap[x, y], "left region pixel ($x, $y)")
            }
            assertEquals(blue, bitmap[3, y], "boundary pixel (3, $y)")
            for (x in 4..5) {
                assertEquals(WHITE, bitmap[x, y], "right region pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onMoveIsANoOp() {
        val bitmap = CanvasBitmap(5, 5)
        drawSquareOutline(bitmap, 1, 1, 3, 3)
        val before = bitmap.copyPixels()
        Fill.onMove(bitmap, 2, 2, 0, 0, red)
        assertTrue(before.contentEquals(bitmap.copyPixels()))
    }

    @Test
    fun toolsListsEveryToolInDisplayOrder() {
        assertEquals(listOf(Pencil, Brush, Fill), TOOLS)
        assertEquals("Fill", Fill.label)
    }

    private fun drawSquareOutline(
        bitmap: CanvasBitmap,
        left: Int = 2,
        top: Int = 2,
        right: Int = 6,
        bottom: Int = 6,
    ) {
        Pencil.onMove(bitmap, left, top, right, top, blue)
        Pencil.onMove(bitmap, right, top, right, bottom, blue)
        Pencil.onMove(bitmap, right, bottom, left, bottom, blue)
        Pencil.onMove(bitmap, left, bottom, left, top, blue)
    }
}
