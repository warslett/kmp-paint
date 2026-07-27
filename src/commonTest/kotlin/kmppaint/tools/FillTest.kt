package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val red = 0xFFFF0000.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun onDownFillsTheRegionUnderThePointer() {
        val bitmap = CanvasBitmap(7, 7)
        for (y in 0 until 7) {
            bitmap[3, y] = black
        }
        Fill.onDown(bitmap, 0, 0, red)
        for (y in 0 until 7) {
            for (x in 0 until 7) {
                val want = when {
                    x == 3 -> black
                    x < 3 -> red
                    else -> WHITE
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onDownOutsideTheCanvasDoesNotThrowAndChangesNothing() {
        val bitmap = CanvasBitmap(4, 4)
        Fill.onDown(bitmap, -1, -1, red)
        Fill.onDown(bitmap, 4, 4, red)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(WHITE, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onMoveIsANoOpBecauseFillingIsAClickAction() {
        val bitmap = CanvasBitmap(6, 6)
        Fill.onMove(bitmap, 0, 0, 5, 5, red)
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                assertEquals(WHITE, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun toolsListIsExactlyPencilThenBrushThenFillInSelectorOrder() {
        assertEquals(listOf(Pencil, Brush, Fill), TOOLS)
    }
}
