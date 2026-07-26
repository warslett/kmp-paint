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

    /** Draws the border of the rectangle [left]..[right] x [top]..[bottom] one pixel at a time. */
    private fun drawRectangleOutline(
        bitmap: CanvasBitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        colour: Int,
    ) {
        for (x in left..right) {
            Pencil.onDown(bitmap, x, top, colour)
            Pencil.onDown(bitmap, x, bottom, colour)
        }
        for (y in top..bottom) {
            Pencil.onDown(bitmap, left, y, colour)
            Pencil.onDown(bitmap, right, y, colour)
        }
    }

    @Test
    fun fillOnUniformCanvasRecoloursEveryPixel() {
        val bitmap = CanvasBitmap(5, 4)
        Fill.onDown(bitmap, 2, 2, red)
        assertBitmapMatches(bitmap) { _, _ -> red }
    }

    @Test
    fun fillStopsAtTheOutlineOfARectangle() {
        val bitmap = CanvasBitmap(9, 7)
        drawRectangleOutline(bitmap, 1, 1, 7, 5, red)
        Fill.onDown(bitmap, 4, 3, blue)
        assertBitmapMatches(bitmap) { x, y ->
            val onOutline = (x in 1..7 && y in 1..5) && (x == 1 || x == 7 || y == 1 || y == 5)
            val inside = x in 2..6 && y in 2..4
            when {
                onOutline -> red
                inside -> blue
                else -> WHITE
            }
        }
    }

    /**
     * The 4-versus-8-connectivity regression test (docs/fill-tool.md §3.1).
     *
     * A diamond drawn with four pure-diagonal pencil strokes is exactly the
     * Manhattan ring |x - 5| + |y - 5| == 5: its pixels touch only at their
     * corners, so an 8-connected fill would escape between them. A 4-connected
     * fill must treat the chain as a wall and stay inside.
     */
    @Test
    fun fillIsContainedByADiagonalPencilOutline() {
        val bitmap = CanvasBitmap(11, 11)
        Pencil.onMove(bitmap, 5, 0, 10, 5, red)
        Pencil.onMove(bitmap, 10, 5, 5, 10, red)
        Pencil.onMove(bitmap, 5, 10, 0, 5, red)
        Pencil.onMove(bitmap, 0, 5, 5, 0, red)
        // The outline really is the Manhattan ring, or the test below proves nothing.
        assertBitmapMatches(bitmap) { x, y ->
            if (abs(x - 5) + abs(y - 5) == 5) red else WHITE
        }

        Fill.onDown(bitmap, 5, 5, blue)
        assertBitmapMatches(bitmap) { x, y ->
            when (abs(x - 5) + abs(y - 5)) {
                in 0..4 -> blue
                5 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun fillLeaksThroughAGapInAnOpenShape() {
        val bitmap = CanvasBitmap(9, 7)
        drawRectangleOutline(bitmap, 1, 1, 7, 5, red)
        // Punch a one-pixel hole in the top edge.
        bitmap[4, 1] = WHITE
        Fill.onDown(bitmap, 4, 3, blue)
        assertBitmapMatches(bitmap) { x, y ->
            val onOutline = (x in 1..7 && y in 1..5) &&
                (x == 1 || x == 7 || y == 1 || y == 5) &&
                !(x == 4 && y == 1)
            if (onOutline) red else blue
        }
    }

    @Test
    fun fillOnlyAffectsTheRegionContainingTheSeed() {
        val bitmap = CanvasBitmap(7, 5)
        for (y in 0 until bitmap.height) {
            bitmap[3, y] = red
        }
        Fill.onDown(bitmap, 1, 2, blue)
        assertBitmapMatches(bitmap) { x, _ ->
            when {
                x < 3 -> blue
                x == 3 -> red
                else -> WHITE
            }
        }
    }

    /**
     * A serpentine corridor: one region whose 4-connected traversal is far
     * longer than the bitmap is wide, so the index stack has to grow past its
     * initial capacity. Also covers deeply non-convex regions.
     */
    @Test
    fun fillTraversesALongSerpentineCorridor() {
        // ~3300 corridor pixels, comfortably past the 1024-entry initial stack.
        val size = 81
        val bitmap = CanvasBitmap(size, size)
        // Odd rows are walls with a single gap, alternating between the two ends.
        fun isWall(x: Int, y: Int): Boolean =
            y % 2 == 1 && if ((y / 2) % 2 == 0) x <= size - 2 else x >= 1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isWall(x, y)) bitmap[x, y] = red
            }
        }
        Fill.onDown(bitmap, 0, 0, blue)
        assertBitmapMatches(bitmap) { x, y -> if (isWall(x, y)) red else blue }
    }

    @Test
    fun fillWithTheColourAlreadyUnderTheSeedIsANoOp() {
        val bitmap = CanvasBitmap(5, 5)
        // Same colour as the canvas: must return rather than loop forever.
        Fill.onDown(bitmap, 2, 2, WHITE)
        assertBitmapMatches(bitmap) { _, _ -> WHITE }

        // Same colour as an already-filled region.
        Fill.onDown(bitmap, 2, 2, red)
        Fill.onDown(bitmap, 0, 0, red)
        assertBitmapMatches(bitmap) { _, _ -> red }
    }

    @Test
    fun fillWithAnOutOfBoundsSeedIsANoOp() {
        val bitmap = CanvasBitmap(5, 4)
        val seeds = listOf(-1 to 2, 5 to 2, 2 to -1, 2 to 4, -3 to -3, 100 to 100)
        for ((x, y) in seeds) {
            Fill.onDown(bitmap, x, y, red)
            assertBitmapMatches(bitmap) { _, _ -> WHITE }
        }
    }

    @Test
    fun fillWorksOnDegenerateBitmapGeometry() {
        val singlePixel = CanvasBitmap(1, 1)
        Fill.onDown(singlePixel, 0, 0, red)
        assertEquals(red, singlePixel[0, 0])

        val column = CanvasBitmap(1, 6)
        column[0, 3] = green
        Fill.onDown(column, 0, 0, red)
        assertBitmapMatches(column) { _, y ->
            when {
                y < 3 -> red
                y == 3 -> green
                else -> WHITE
            }
        }

        val row = CanvasBitmap(6, 1)
        row[3, 0] = green
        Fill.onDown(row, 5, 0, red)
        assertBitmapMatches(row) { x, _ ->
            when {
                x > 3 -> red
                x == 3 -> green
                else -> WHITE
            }
        }
    }

    @Test
    fun fillUsesExactlyTheColourArgument() {
        val bitmap = CanvasBitmap(7, 5)
        for (y in 0 until bitmap.height) {
            bitmap[3, y] = green
        }
        Fill.onDown(bitmap, 0, 0, red)
        Fill.onDown(bitmap, 6, 4, blue)
        val pixels = bitmap.copyPixels()
        assertEquals(3 * 5, pixels.count { it == red })
        assertEquals(3 * 5, pixels.count { it == blue })
        assertEquals(1 * 5, pixels.count { it == green })
        assertEquals(0, pixels.count { it == WHITE })
    }

    /** Guards against stack overflow and quadratic blow-up at the real canvas size. */
    @Test
    fun fillingTheFullCanvasCompletes() {
        val bitmap = CanvasBitmap(800, 600)
        Fill.onDown(bitmap, 400, 300, red)
        assertEquals(800 * 600, bitmap.copyPixels().count { it == red })
    }

    @Test
    fun fillOnMoveDoesNothing() {
        val bitmap = CanvasBitmap(9, 7)
        drawRectangleOutline(bitmap, 1, 1, 7, 5, red)
        val before = bitmap.copyPixels()
        Fill.onMove(bitmap, 4, 3, 0, 0, blue)
        Fill.onMove(bitmap, 0, 0, 8, 6, blue)
        assertTrue(before.contentEquals(bitmap.copyPixels()), "onMove must leave the bitmap untouched")
    }

    @Test
    fun floodFillHelperMatchesTheFillTool() {
        val viaTool = CanvasBitmap(9, 7)
        val viaHelper = CanvasBitmap(9, 7)
        for (bitmap in listOf(viaTool, viaHelper)) {
            drawRectangleOutline(bitmap, 1, 1, 7, 5, red)
        }
        Fill.onDown(viaTool, 4, 3, blue)
        floodFill(viaHelper, 4, 3, blue)
        assertTrue(viaTool.copyPixels().contentEquals(viaHelper.copyPixels()))
    }
}
