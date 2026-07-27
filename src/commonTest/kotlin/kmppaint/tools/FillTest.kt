package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
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
    fun onDownFillsABoundedRectangle() {
        val bitmap = CanvasBitmap(10, 10)
        // Draw a rectangular border with Pencil.
        for (x in 2..7) {
            bitmap[x, 2] = red
            bitmap[x, 7] = red
        }
        for (y in 2..7) {
            bitmap[2, y] = red
            bitmap[7, y] = red
        }
        // Fill inside the rectangle.
        Fill.onDown(bitmap, 4, 4, blue)
        assertBitmapMatches(bitmap) { x, y ->
            when {
                x in 3..6 && y in 3..6 -> blue
                x in 2..7 && y in 2..7 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun onDownFillReachesCanvasEdges() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, red)
        assertTrue(bitmap.copyPixels().all { it == red })
    }

    @Test
    fun onDownIsNoOpWhenTargetColourEqualsFillColour() {
        val bitmap = CanvasBitmap(5, 5)
        val before = bitmap.copyPixels()
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertTrue(before.contentEquals(bitmap.copyPixels()))
    }

    @Test
    fun onDownFillsASingleIsolatedPixel() {
        val bitmap = CanvasBitmap(5, 5)
        bitmap[2, 2] = red
        Fill.onDown(bitmap, 2, 2, blue)
        assertBitmapMatches(bitmap) { x, y ->
            if (x == 2 && y == 2) blue else WHITE
        }
    }

    @Test
    fun onMoveIsNoOp() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onMove(bitmap, 0, 0, 4, 4, red)
        assertEquals(WHITE, bitmap[0, 0])
        assertEquals(WHITE, bitmap[4, 4])
    }

    @Test
    fun onDownDoesNotBleedThroughBoundaries() {
        // Two rectangles side by side with red borders.
        val bitmap = CanvasBitmap(20, 10)
        for (x in 2..7) {
            bitmap[x, 2] = red
            bitmap[x, 7] = red
        }
        for (y in 2..7) {
            bitmap[2, y] = red
            bitmap[7, y] = red
        }
        for (x in 12..17) {
            bitmap[x, 2] = red
            bitmap[x, 7] = red
        }
        for (y in 2..7) {
            bitmap[12, y] = red
            bitmap[17, y] = red
        }
        // Fill the left rectangle.
        Fill.onDown(bitmap, 4, 4, blue)
        // Left interior should be blue.
        for (x in 3..6) for (y in 3..6) assertEquals(blue, bitmap[x, y], "left interior ($x, $y)")
        // Right interior should still be white.
        for (x in 13..16) for (y in 3..6) assertEquals(WHITE, bitmap[x, y], "right interior ($x, $y)")
    }

    @Test
    fun onDownWorksAtCanvasOrigin() {
        val bitmap = CanvasBitmap(5, 5)
        // Place a red pixel at the origin surrounded by a white field.
        bitmap[0, 0] = red
        Fill.onDown(bitmap, 0, 0, blue)
        assertEquals(blue, bitmap[0, 0])
        for (x in 1 until 5) assertEquals(WHITE, bitmap[x, 0], "row 0, col $x")
        for (y in 1 until 5) assertEquals(WHITE, bitmap[0, y], "col 0, row $y")
    }

    @Test
    fun fillDoesNotCrossHorizontalBoundaryOfDifferentColour() {
        val bitmap = CanvasBitmap(8, 8)
        // Red horizontal line across the middle at y=4.
        for (x in 0 until 8) bitmap[x, 4] = red
        // Fill above the line.
        Fill.onDown(bitmap, 2, 2, blue)
        // Everything above the line should be blue.
        for (y in 0..3) {
            for (x in 0 until 8) {
                assertEquals(blue, bitmap[x, y], "above line ($x, $y)")
            }
        }
        // The line itself should still be red.
        for (x in 0 until 8) assertEquals(red, bitmap[x, 4], "line pixel ($x, 4)")
        // Everything below the line should still be white.
        for (y in 5 until 8) {
            for (x in 0 until 8) {
                assertEquals(WHITE, bitmap[x, y], "below line ($x, $y)")
            }
        }
    }

    @Test
    fun toolsListIncludesFillAfterPencilAndBrush() {
        assertEquals(Pencil, TOOLS[0])
        assertEquals(Brush, TOOLS[1])
        assertEquals(Fill, TOOLS[2])
        assertEquals(3, TOOLS.size)
    }
}
