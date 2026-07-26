package kmppaint.tools

import kotlin.math.abs

/**
 * Invokes [plot] for every pixel on the segment from (fromX, fromY) to
 * (toX, toY), endpoints included, in order from start to end (Bresenham).
 * Consecutive plotted pixels are 8-adjacent, so the resulting line has no
 * gaps regardless of direction or length.
 *
 * The plotted pixel set does not depend on the segment's direction: pixels
 * are always computed from the endpoint with the smaller x (or smaller y when
 * the x's are equal), and the sequence is played back in reverse when the
 * caller's start is the other end, so callers still receive the pixels from
 * start to end. (Naive Bresenham picks different pixels on exact-tie lines
 * depending on the direction of travel.)
 *
 * No coordinate clamping happens here and none is needed: `CanvasBitmap.set`
 * already ignores out-of-bounds writes, so segments leaving the canvas are
 * plotted harmlessly into the void.
 */
fun forEachPixelOnLine(fromX: Int, fromY: Int, toX: Int, toY: Int, plot: (x: Int, y: Int) -> Unit) {
    val reversed = fromX > toX || (fromX == toX && fromY > toY)
    if (!reversed) {
        plotLine(fromX, fromY, toX, toY, plot)
        return
    }
    // Buffer the canonical-order pixels, then plot them back to front.
    val points = ArrayList<Pair<Int, Int>>()
    plotLine(toX, toY, fromX, fromY) { x, y -> points.add(x to y) }
    for (i in points.indices.reversed()) {
        plot(points[i].first, points[i].second)
    }
}

/**
 * Classic all-quadrant Bresenham from (fromX, fromY) to (toX, toY), both
 * endpoints included, plotting in order from start to end.
 */
private fun plotLine(fromX: Int, fromY: Int, toX: Int, toY: Int, plot: (x: Int, y: Int) -> Unit) {
    var x = fromX
    var y = fromY
    val dx = abs(toX - fromX)
    val dy = -abs(toY - fromY)
    val sx = if (fromX < toX) 1 else -1
    val sy = if (fromY < toY) 1 else -1
    var err = dx + dy
    while (true) {
        plot(x, y)
        if (x == toX && y == toY) return
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            err += dx
            y += sy
        }
    }
}
