package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class PencilTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun onDownMarksExactlyOnePixelInTheGivenColour() {
        val bitmap = CanvasBitmap(5, 5)
        Pencil.onDown(bitmap, 2, 3, red)
        assertEquals(red, bitmap[2, 3])
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (x != 2 || y != 3) {
                    assertEquals(WHITE, bitmap[x, y], "neighbour ($x, $y)")
                }
            }
        }
    }

    @Test
    fun onMoveDrawsEveryPixelOfTheSegmentEndpointsIncluded() {
        val bitmap = CanvasBitmap(6, 6)
        Pencil.onMove(bitmap, 0, 0, 4, 2, red)
        val expected = setOf(0 to 0, 1 to 1, 2 to 1, 3 to 2, 4 to 2)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = if (x to y in expected) red else WHITE
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun horizontalOnMoveStaysOnePixelWide() {
        val bitmap = CanvasBitmap(6, 6)
        Pencil.onMove(bitmap, 1, 2, 4, 2, red)
        for (x in 1..4) {
            assertEquals(red, bitmap[x, 2], "row pixel ($x, 2)")
        }
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (y != 2) {
                    assertEquals(WHITE, bitmap[x, y], "off-row pixel ($x, $y)")
                }
            }
        }
    }

    @Test
    fun onMoveWithOutOfBoundsEndpointsDoesNotThrowAndDrawsTheInBoundsPortion() {
        val bitmap = CanvasBitmap(5, 3)
        // Both endpoints off-canvas; the segment crosses the whole width.
        Pencil.onMove(bitmap, -2, 1, 7, 1, red)
        for (x in 0 until bitmap.width) {
            assertEquals(red, bitmap[x, 1], "in-bounds pixel ($x, 1)")
        }
        for (y in intArrayOf(0, 2)) {
            for (x in 0 until bitmap.width) {
                assertEquals(WHITE, bitmap[x, y], "untouched pixel ($x, $y)")
            }
        }
    }

    @Test
    fun theColourDrawnIsExactlyTheColourArgument() {
        val bitmap = CanvasBitmap(4, 4)
        Pencil.onDown(bitmap, 0, 0, red)
        Pencil.onDown(bitmap, 1, 0, blue)
        Pencil.onMove(bitmap, 0, 1, 3, 1, red)
        Pencil.onMove(bitmap, 0, 2, 3, 2, blue)
        assertEquals(red, bitmap[0, 0])
        assertEquals(blue, bitmap[1, 0])
        for (x in 0..3) {
            assertEquals(red, bitmap[x, 1], "red row pixel ($x, 1)")
            assertEquals(blue, bitmap[x, 2], "blue row pixel ($x, 2)")
        }
        assertTrue(bitmap.copyPixels().count { it != WHITE } == 10)
    }
}
