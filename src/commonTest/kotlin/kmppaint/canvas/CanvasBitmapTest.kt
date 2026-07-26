package kmppaint.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanvasBitmapTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun newBitmapIsWhiteByDefault() {
        val bitmap = CanvasBitmap(4, 3)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(WHITE, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun newBitmapUsesCustomFillColour() {
        val bitmap = CanvasBitmap(3, 2, fill = red)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(red, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun setThenGetRoundTripsAndLeavesNeighboursUnchanged() {
        val bitmap = CanvasBitmap(4, 3)
        bitmap[2, 1] = red
        assertEquals(red, bitmap[2, 1])
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (x != 2 || y != 1) {
                    assertEquals(WHITE, bitmap[x, y], "neighbour ($x, $y)")
                }
            }
        }
    }

    @Test
    fun setIgnoresOutOfBoundsWrites() {
        val bitmap = CanvasBitmap(4, 3)
        val w = bitmap.width
        val h = bitmap.height
        // Must not throw:
        bitmap[-1, 0] = red
        bitmap[w, 0] = red
        bitmap[0, -1] = red
        bitmap[0, h] = red
        // ...and the buffer must be unchanged:
        assertTrue(bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun getThrowsOnOutOfBoundsReads() {
        val bitmap = CanvasBitmap(4, 3)
        val w = bitmap.width
        val h = bitmap.height
        assertFailsWith<IllegalArgumentException> { bitmap[-1, 0] }
        assertFailsWith<IllegalArgumentException> { bitmap[w, 0] }
        assertFailsWith<IllegalArgumentException> { bitmap[0, -1] }
        assertFailsWith<IllegalArgumentException> { bitmap[0, h] }
    }

    @Test
    fun zeroOrNegativeDimensionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { CanvasBitmap(0, 5) }
        assertFailsWith<IllegalArgumentException> { CanvasBitmap(5, 0) }
        assertFailsWith<IllegalArgumentException> { CanvasBitmap(-1, 5) }
        assertFailsWith<IllegalArgumentException> { CanvasBitmap(5, -1) }
    }

    @Test
    fun fillRecoloursWholeBuffer() {
        val bitmap = CanvasBitmap(3, 2)
        bitmap[1, 0] = red
        bitmap.fill(blue)
        assertTrue(bitmap.copyPixels().all { it == blue })
    }

    @Test
    fun copyPixelsReturnsIndependentArray() {
        val bitmap = CanvasBitmap(3, 2)
        val copy = bitmap.copyPixels()
        copy[0] = red
        assertEquals(WHITE, bitmap[0, 0])
    }
}
