package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Invokes [plot] for every pixel in the 4-connected region of pixels equal
 * in colour to the pixel at (startX, startY), starting from that pixel
 * itself (a flood fill / "paint bucket" region). If (startX, startY) is out
 * of bounds, does nothing.
 *
 * Uses a scanline (span-based) flood fill: for the current pixel, extends
 * left and right along the row while the colour matches, marking the whole
 * span as visited/plotted in one pass, then queues one seed above and below
 * the span for each maximal run of matching pixels. This is equivalent to
 * (and much faster than) a naive 4-connected BFS/DFS that queues every
 * individual pixel, while visiting each matching pixel exactly once.
 *
 * Bounds are checked manually before every read: `CanvasBitmap.get` throws
 * on out-of-bounds coordinates (writes clip silently, reads do not), so this
 * helper never reads a coordinate that hasn't first passed `inBounds`.
 */
fun forEachPixelInFloodFillRegion(
    bitmap: CanvasBitmap,
    startX: Int,
    startY: Int,
    plot: (x: Int, y: Int) -> Unit,
) {
    fun inBounds(x: Int, y: Int) = x in 0 until bitmap.width && y in 0 until bitmap.height
    if (!inBounds(startX, startY)) return

    val targetColour = bitmap[startX, startY]
    val visited = BooleanArray(bitmap.width * bitmap.height)
    fun isVisited(x: Int, y: Int) = visited[y * bitmap.width + x]
    fun markVisited(x: Int, y: Int) {
        visited[y * bitmap.width + x] = true
    }
    fun matches(x: Int, y: Int) = inBounds(x, y) && !isVisited(x, y) && bitmap[x, y] == targetColour

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(startX to startY)
    markVisited(startX, startY)

    while (stack.isNotEmpty()) {
        val (x, y) = stack.removeLast()

        var left = x
        while (matches(left - 1, y)) {
            left--
            markVisited(left, y)
        }
        var right = x
        while (matches(right + 1, y)) {
            right++
            markVisited(right, y)
        }

        for (px in left..right) {
            plot(px, y)
            for (ny in intArrayOf(y - 1, y + 1)) {
                if (matches(px, ny)) {
                    markVisited(px, ny)
                    stack.addLast(px to ny)
                }
            }
        }
    }
}
