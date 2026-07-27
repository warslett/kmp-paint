package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class FillTest {
    private val red = 0xFFFF0000.toInt()

    @Test
    fun onDownInsideAClosedRegionFillsExactlyThatRegionAndLeavesTheRestUntouched() {
        val bitmap = CanvasBitmap(8, 8)
        for (x in 2..5) {
            bitmap[x, 2] = red
            bitmap[x, 5] = red
        }
        for (y in 2..5) {
            bitmap[2, y] = red
            bitmap[5, y] = red
        }

        Fill.onDown(bitmap, 3, 3, blue)

        val expectedFilled = buildSet {
            for (y in 3..4) for (x in 3..4) add(x to y)
        }
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = when {
                    x to y in expectedFilled -> blue
                    x in 2..5 && y in 2..5 && (x == 2 || x == 5 || y == 2 || y == 5) -> red
                    else -> WHITE
                }
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onDownOnAWholeBlankCanvasFillsEveryPixel() {
        val bitmap = CanvasBitmap(5, 5)
        Fill.onDown(bitmap, 2, 2, red)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals(red, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun onMoveIsANoOpAndLeavesTheBitmapUnchanged() {
        val bitmap = CanvasBitmap(6, 6)
        bitmap[1, 1] = red
        val before = bitmap.copyPixels()
        Fill.onMove(bitmap, 0, 0, 5, 5, blue)
        assertEquals(before.toList(), bitmap.copyPixels().toList())
    }

    private val blue = 0xFF0000FF.toInt()
}
