package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class BrushTest {
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
    fun onDownStampsExactlyThe29PixelDiscInTheGivenColour() {
        val bitmap = CanvasBitmap(11, 11)
        Brush.onDown(bitmap, 5, 5, red)
        assertBitmapMatches(bitmap) { x, y ->
            val dx = x - 5
            val dy = y - 5
            if (dx * dx + dy * dy <= BRUSH_RADIUS * BRUSH_RADIUS) red else WHITE
        }
        assertEquals(29, bitmap.copyPixels().count { it != WHITE })
    }

    @Test
    fun onDownNearAnEdgeClipsWithoutThrowingAndStampsOnlyTheInBoundsPortion() {
        val bitmap = CanvasBitmap(5, 5)
        Brush.onDown(bitmap, 0, 0, red)
        assertBitmapMatches(bitmap) { x, y ->
            if (x * x + y * y <= BRUSH_RADIUS * BRUSH_RADIUS) red else WHITE
        }
        assertEquals(11, bitmap.copyPixels().count { it != WHITE })
    }

    @Test
    fun horizontalOnMovePaintsA7PixelTallBandWithRoundedCapsAndNoGaps() {
        val bitmap = CanvasBitmap(16, 16)
        Brush.onMove(bitmap, 4, 5, 8, 5, red)
        // Stamps centred at (4..8, 5): a 7 px tall band, narrowed to the stamp
        // centres on the outermost rows — the rounded caps.
        val expectedRows = mapOf(
            2 to (4..8),
            3 to (2..10),
            4 to (2..10),
            5 to (1..11),
            6 to (2..10),
            7 to (2..10),
            8 to (4..8),
        )
        assertBitmapMatches(bitmap) { x, y ->
            val range = expectedRows[y]
            if (range != null && x in range) red else WHITE
        }
    }

    @Test
    fun strokesOfEveryDirectionAndLengthAreContinuousAndStayWithinBrushRadiusOfTheLine() {
        val segments = listOf(
            intArrayOf(1, 1, 30, 1), // horizontal
            intArrayOf(2, 2, 2, 35), // vertical
            intArrayOf(0, 0, 25, 25), // 45-degree diagonal
            intArrayOf(40, 0, 0, 30), // up-left diagonal
            intArrayOf(0, 0, 40, 30), // long, whole-canvas diagonal
        )
        for ((fromX, fromY, toX, toY) in segments) {
            val name = "($fromX, $fromY) -> ($toX, $toY)"
            val bitmap = CanvasBitmap(50, 40)
            Brush.onMove(bitmap, fromX, fromY, toX, toY, red)
            val centres = mutableSetOf<Pair<Int, Int>>()
            forEachPixelOnLine(fromX, fromY, toX, toY) { x, y -> centres.add(x to y) }
            // Continuity: every pixel of the underlying centre line is painted.
            for ((cx, cy) in centres) {
                assertEquals(red, bitmap[cx, cy], "$name: centre-line pixel ($cx, $cy)")
            }
            // No scatter: every painted pixel lies within the disc of some line pixel.
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (bitmap[x, y] == WHITE) continue
                    val covered = centres.any { (cx, cy) ->
                        val dx = x - cx
                        val dy = y - cy
                        dx * dx + dy * dy <= BRUSH_RADIUS * BRUSH_RADIUS
                    }
                    assertTrue(covered, "$name: painted pixel ($x, $y) is farther than $BRUSH_RADIUS from the line")
                }
            }
        }
    }

    @Test
    fun brushPaintsAtLeastThreeTimesAsManyPixelsAsPencilForTheSameSegment() {
        val brushBitmap = CanvasBitmap(40, 30)
        val pencilBitmap = CanvasBitmap(40, 30)
        Brush.onMove(brushBitmap, 5, 5, 25, 15, red)
        Pencil.onMove(pencilBitmap, 5, 5, 25, 15, red)
        val brushCount = brushBitmap.copyPixels().count { it != WHITE }
        val pencilCount = pencilBitmap.copyPixels().count { it != WHITE }
        assertTrue(
            brushCount >= 3 * pencilCount,
            "brush ($brushCount px) must be visibly thicker than pencil ($pencilCount px)",
        )
    }

    @Test
    fun onMoveWithOutOfBoundsEndpointsDoesNotThrowAndPaintsTheInBoundsPortion() {
        val bitmap = CanvasBitmap(10, 8)
        // Both endpoints off-canvas; the horizontal stroke crosses the whole width.
        Brush.onMove(bitmap, -5, 4, 14, 4, red)
        // Rows 1..7 (within dy ±3 of the centre row) are painted edge to edge;
        // row 0 (dy = -4) is beyond the disc and stays white.
        assertBitmapMatches(bitmap) { _, y ->
            if (y in 1..7) red else WHITE
        }
    }

    @Test
    fun theColourDrawnIsExactlyTheColourArgument() {
        val bitmap = CanvasBitmap(22, 11)
        Brush.onDown(bitmap, 5, 5, red)
        Brush.onDown(bitmap, 16, 5, blue)
        assertEquals(red, bitmap[5, 5])
        assertEquals(red, bitmap[2, 5], "left edge of the red dab")
        assertEquals(blue, bitmap[16, 5])
        assertEquals(blue, bitmap[19, 5], "right edge of the blue dab")
        assertEquals(2 * 29, bitmap.copyPixels().count { it != WHITE })
        assertEquals(29, bitmap.copyPixels().count { it == red })
        assertEquals(29, bitmap.copyPixels().count { it == blue })
    }
}
