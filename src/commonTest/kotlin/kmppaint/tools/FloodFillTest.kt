package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FloodFillTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val black = 0xFF000000.toInt()

    /** Draws the outline of the rectangle spanning [left]..[right] x [top]..[bottom]. */
    private fun CanvasBitmap.drawRectOutline(left: Int, top: Int, right: Int, bottom: Int, colour: Int) {
        for (x in left..right) {
            this[x, top] = colour
            this[x, bottom] = colour
        }
        for (y in top..bottom) {
            this[left, y] = colour
            this[right, y] = colour
        }
    }

    private fun assertPixels(bitmap: CanvasBitmap, want: (x: Int, y: Int) -> Int) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(want(x, y), bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fillsAUniformBitmapEntirely() {
        val bitmap = CanvasBitmap(5, 5)
        floodFill(bitmap, 2, 2, red)
        assertPixels(bitmap) { _, _ -> red }
    }

    @Test
    fun fillsOnlyInsideAClosedRectangle() {
        val bitmap = CanvasBitmap(9, 9)
        bitmap.drawRectOutline(2, 2, 6, 6, black)
        floodFill(bitmap, 4, 4, red)
        assertPixels(bitmap) { x, y ->
            val onOutline = (x in 2..6 && y in 2..6) && (x == 2 || x == 6 || y == 2 || y == 6)
            val inside = x in 3..5 && y in 3..5
            when {
                onOutline -> black
                inside -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun fillsOnlyOutsideAClosedRectangle() {
        val bitmap = CanvasBitmap(9, 9)
        bitmap.drawRectOutline(2, 2, 6, 6, black)
        floodFill(bitmap, 0, 0, red)
        assertPixels(bitmap) { x, y ->
            val onOutline = (x in 2..6 && y in 2..6) && (x == 2 || x == 6 || y == 2 || y == 6)
            val inside = x in 3..5 && y in 3..5
            when {
                onOutline -> black
                inside -> WHITE
                else -> red
            }
        }
    }

    @Test
    fun doesNotLeakThroughADiagonalBoundary() {
        // The pencil draws 8-adjacent lines, so a diagonal has 1px diagonal
        // gaps. A 4-connected fill must treat it as a solid wall.
        val bitmap = CanvasBitmap(8, 8)
        forEachPixelOnLine(0, 0, 7, 7) { x, y -> bitmap[x, y] = black }
        // Fill the region strictly above the diagonal (y < x).
        floodFill(bitmap, 7, 0, red)
        assertPixels(bitmap) { x, y ->
            when {
                x == y -> black
                y < x -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun doesNotLeakThroughASlantedBoundary() {
        val bitmap = CanvasBitmap(11, 11)
        // A closed diamond: four slanted pencil strokes.
        forEachPixelOnLine(5, 1, 9, 5) { x, y -> bitmap[x, y] = black }
        forEachPixelOnLine(9, 5, 5, 9) { x, y -> bitmap[x, y] = black }
        forEachPixelOnLine(5, 9, 1, 5) { x, y -> bitmap[x, y] = black }
        forEachPixelOnLine(1, 5, 5, 1) { x, y -> bitmap[x, y] = black }
        floodFill(bitmap, 5, 5, red)
        // The interior is filled, the corners of the canvas are untouched.
        assertEquals(red, bitmap[5, 5], "diamond centre")
        assertEquals(red, bitmap[5, 3], "diamond interior")
        assertEquals(WHITE, bitmap[0, 0], "outside the diamond")
        assertEquals(WHITE, bitmap[10, 10], "outside the diamond")
        assertEquals(WHITE, bitmap[10, 0], "outside the diamond")
        assertEquals(WHITE, bitmap[0, 10], "outside the diamond")
    }

    @Test
    fun leaksThroughAOnePixelGapInTheBoundary() {
        // Defining behaviour of a flood fill, not a bug: an unclosed shape
        // spills into the surrounding region.
        val bitmap = CanvasBitmap(9, 9)
        bitmap.drawRectOutline(2, 2, 6, 6, black)
        bitmap[4, 2] = WHITE
        floodFill(bitmap, 4, 4, red)
        assertPixels(bitmap) { x, y ->
            val onOutline = (x in 2..6 && y in 2..6) &&
                (x == 2 || x == 6 || y == 2 || y == 6) &&
                !(x == 4 && y == 2)
            if (onOutline) black else red
        }
    }

    @Test
    fun fillingWithTheColourAlreadyThereIsANoOp() {
        val bitmap = CanvasBitmap(5, 5)
        bitmap[1, 1] = red
        floodFill(bitmap, 2, 2, WHITE)
        floodFill(bitmap, 1, 1, red)
        assertPixels(bitmap) { x, y -> if (x == 1 && y == 1) red else WHITE }
    }

    @Test
    fun outOfBoundsClickIsANoOp() {
        val bitmap = CanvasBitmap(4, 4)
        floodFill(bitmap, -1, 2, red)
        floodFill(bitmap, 2, -1, red)
        floodFill(bitmap, 4, 2, red)
        floodFill(bitmap, 2, 4, red)
        floodFill(bitmap, 100, 100, red)
        assertPixels(bitmap) { _, _ -> WHITE }
    }

    @Test
    fun fillsARegionThatRequiresUpwardAndDownwardSpanSeeding() {
        // A U-shape: the right arm is only reachable by going down the left
        // arm, across the base, and back up again.
        val bitmap = CanvasBitmap(7, 7)
        for (y in 0..4) {
            bitmap[3, y] = black
        }
        floodFill(bitmap, 0, 0, red)
        assertPixels(bitmap) { x, y -> if (x == 3 && y <= 4) black else red }
    }

    @Test
    fun fillsASpiralRegionEntirely() {
        val bitmap = CanvasBitmap(9, 9)
        // Concentric walls with staggered openings force a winding traversal.
        for (x in 0..8) bitmap[x, 2] = black
        bitmap[8, 2] = WHITE
        for (x in 0..6) bitmap[x, 4] = black
        bitmap[0, 4] = WHITE
        for (x in 2..8) bitmap[x, 6] = black
        bitmap[8, 6] = WHITE
        val walls = bitmap.copyPixels().withIndex().filter { it.value == black }.map { it.index }.toSet()
        floodFill(bitmap, 4, 0, red)
        assertPixels(bitmap) { x, y -> if (y * bitmap.width + x in walls) black else red }
    }

    @Test
    fun fillsALargeCanvasWithoutStackOverflow() {
        val bitmap = CanvasBitmap(800, 600)
        floodFill(bitmap, 400, 300, blue)
        assertEquals(800 * 600, bitmap.copyPixels().count { it == blue })
    }

    @Test
    fun fillsOnlyTheRegionOfTheClickedColour() {
        // Two adjacent solid blocks of different colours: filling one must not
        // touch the other, even though they are 4-adjacent.
        val bitmap = CanvasBitmap(6, 4)
        for (y in 0 until 4) {
            for (x in 0 until 3) {
                bitmap[x, y] = black
            }
        }
        floodFill(bitmap, 4, 2, red)
        assertPixels(bitmap) { x, _ -> if (x < 3) black else red }
    }
}
