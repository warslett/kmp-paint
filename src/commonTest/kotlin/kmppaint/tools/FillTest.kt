package kmppaint.tools

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun fillingAnOpenCanvasRecoloursEveryPixelWithoutRecursionLimits() {
        val bitmap = CanvasBitmap(800, 600)

        Fill.onDown(bitmap, 400, 300, red)

        assertTrue(bitmap.copyPixels().all { it == red })
    }

    @Test
    fun pencilRectangleContainsFillAndPreservesItsOutlineAndExterior() {
        val bitmap = CanvasBitmap(7, 7)
        Pencil.onMove(bitmap, 1, 1, 5, 1, black)
        Pencil.onMove(bitmap, 5, 1, 5, 5, black)
        Pencil.onMove(bitmap, 5, 5, 1, 5, black)
        Pencil.onMove(bitmap, 1, 5, 1, 1, black)

        Fill.onDown(bitmap, 3, 3, red)

        assertBitmapMatches(bitmap) { x, y ->
            val onBorder = (x in 1..5 && (y == 1 || y == 5)) ||
                (y in 1..5 && (x == 1 || x == 5))
            when {
                onBorder -> black
                x in 2..4 && y in 2..4 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun diagonalPencilBoundaryContainsFourConnectedFill() {
        val bitmap = CanvasBitmap(7, 7)
        Pencil.onMove(bitmap, 3, 0, 6, 3, black)
        Pencil.onMove(bitmap, 6, 3, 3, 6, black)
        Pencil.onMove(bitmap, 3, 6, 0, 3, black)
        Pencil.onMove(bitmap, 0, 3, 3, 0, black)

        Fill.onDown(bitmap, 3, 3, red)

        assertBitmapMatches(bitmap) { x, y ->
            when (abs(x - 3) + abs(y - 3)) {
                3 -> black
                0, 1, 2 -> red
                else -> WHITE
            }
        }
    }

    @Test
    fun disconnectedRegionWithTheSameTargetColourIsNotChanged() {
        val bitmap = CanvasBitmap(7, 3, fill = blue)
        for (y in 0 until bitmap.height) bitmap[3, y] = black

        Fill.onDown(bitmap, 1, 1, red)

        assertBitmapMatches(bitmap) { x, _ ->
            when {
                x < 3 -> red
                x == 3 -> black
                else -> blue
            }
        }
    }

    @Test
    fun pixelsOfAnotherColourArePreserved() {
        val bitmap = CanvasBitmap(5, 5)
        for (x in 1..3) bitmap[x, 2] = black
        for (y in 1..3) bitmap[2, y] = black

        Fill.onDown(bitmap, 0, 0, red)

        assertBitmapMatches(bitmap) { x, y ->
            if ((y == 2 && x in 1..3) || (x == 2 && y in 1..3)) black else red
        }
    }

    @Test
    fun fillAtAnEdgeDoesNotWrapToTheNextRow() {
        val bitmap = CanvasBitmap(4, 3, fill = black)
        bitmap[3, 0] = WHITE
        bitmap[3, 1] = WHITE
        bitmap[0, 2] = WHITE

        Fill.onDown(bitmap, 3, 0, red)

        assertEquals(red, bitmap[3, 0])
        assertEquals(red, bitmap[3, 1])
        assertEquals(WHITE, bitmap[0, 2])
        assertEquals(black, bitmap[0, 1])
    }

    @Test
    fun fillingWithTheTargetColourIsANoOp() {
        val bitmap = CanvasBitmap(4, 3, fill = red)
        val before = bitmap.copyPixels()

        Fill.onDown(bitmap, 2, 1, red)

        assertContentEquals(before, bitmap.copyPixels())
    }

    @Test
    fun outOfBoundsPressesAreNoOps() {
        val bitmap = CanvasBitmap(4, 3)
        val before = bitmap.copyPixels()

        Fill.onDown(bitmap, -1, 0, red)
        Fill.onDown(bitmap, bitmap.width, 0, red)
        Fill.onDown(bitmap, 0, -1, red)
        Fill.onDown(bitmap, 0, bitmap.height, red)

        assertContentEquals(before, bitmap.copyPixels())
    }

    @Test
    fun pointerMovementDoesNotFillAnotherRegion() {
        val bitmap = CanvasBitmap(4, 3)
        val before = bitmap.copyPixels()

        Fill.onMove(bitmap, 0, 0, 3, 2, red)

        assertContentEquals(before, bitmap.copyPixels())
    }

    @Test
    fun toolsListIsExactlyPencilThenBrushThenFillInSelectorOrder() {
        assertEquals(listOf(Pencil, Brush, Fill), TOOLS)
    }
}
