package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kmppaint.canvas.CanvasBitmap
import kmppaint.canvas.WHITE

class DiscsTest {
    private val red = 0xFFFF0000.toInt()

    /** The radius-3 offset set pinned in docs/plans/step5.md §2.1, as (dx, dy) pairs. */
    private val radius3Offsets: Set<Pair<Int, Int>> = setOf(
        0 to -3,
        -2 to -2, -1 to -2, 0 to -2, 1 to -2, 2 to -2,
        -2 to -1, -1 to -1, 0 to -1, 1 to -1, 2 to -1,
        -3 to 0, -2 to 0, -1 to 0, 0 to 0, 1 to 0, 2 to 0, 3 to 0,
        -2 to 1, -1 to 1, 0 to 1, 1 to 1, 2 to 1,
        -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2,
        0 to 3,
    )

    private fun collect(cx: Int, cy: Int, radius: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        forEachPixelInDisc(cx, cy, radius) { x, y -> points.add(x to y) }
        return points
    }

    private fun collectOffsets(radius: Int): List<Pair<Int, Int>> = collect(0, 0, radius)

    @Test
    fun radiusZeroDiscPlotsExactlyTheCentrePixelOnce() {
        assertEquals(listOf(5 to 7), collect(5, 7, 0))
    }

    @Test
    fun radiusThreeDiscPlotsExactlyThePinned29PixelSet() {
        val plotted = collect(10, 10, 3).map { (x, y) -> x - 10 to y - 10 }.toSet()
        assertEquals(29, plotted.size)
        assertEquals(radius3Offsets, plotted)
        assertTrue(0 to 0 in plotted, "centre pixel is included")
        for (corner in listOf(-3 to -3, 3 to -3, -3 to 3, 3 to 3)) {
            assertTrue(corner !in plotted, "bounding-box corner $corner is outside the disc")
        }
    }

    @Test
    fun discIsSymmetricUnderMirrorsAndQuarterTurns() {
        val offsets = collectOffsets(3).toSet()
        assertEquals(offsets, offsets.map { (dx, dy) -> -dx to dy }.toSet(), "x-mirror")
        assertEquals(offsets, offsets.map { (dx, dy) -> dx to -dy }.toSet(), "y-mirror")
        assertEquals(offsets, offsets.map { (dx, dy) -> -dy to dx }.toSet(), "90-degree rotation")
    }

    @Test
    fun plottingOrderIsAscendingDyThenAscendingDxWithNoDuplicates() {
        val offsets = collectOffsets(3)
        assertEquals(offsets.size, offsets.toSet().size, "no pixel plotted twice")
        assertEquals(
            offsets.sortedWith(compareBy({ it.second }, { it.first })),
            offsets,
            "ascending dy, then ascending dx",
        )
    }

    @Test
    fun plottedSetIsExactlyTheOffsetsWithinRadiusNoStragglersNoHoles() {
        for (radius in listOf(0, 1, 2, 3, 4, 7)) {
            val plotted = collectOffsets(radius).toSet()
            val expected = mutableSetOf<Pair<Int, Int>>()
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        expected.add(dx to dy)
                    }
                }
            }
            assertEquals(expected, plotted, "radius $radius")
        }
    }

    @Test
    fun discCentredAtACanvasCornerStampsOnlyTheInBoundsQuarterWithoutThrowing() {
        val bitmap = CanvasBitmap(5, 5)
        forEachPixelInDisc(0, 0, 3) { x, y -> bitmap[x, y] = red }
        // In-bounds quarter of the disc: (x, y) with x² + y² ≤ 9 — 11 pixels.
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val want = if (x * x + y * y <= 9) red else WHITE
                assertEquals(want, bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }
}
