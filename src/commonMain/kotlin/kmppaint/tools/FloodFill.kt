package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Flood-fills the 4-connected region of (startX, startY)'s colour with
 * [colour]: every pixel reachable from (startX, startY) by stepping
 * up/down/left/right across pixels of the region's original colour is
 * recoloured, and any pixel of a different colour is an edge the fill does
 * not cross. Because `forEachPixelOnLine` strokes are 8-connected, 4-
 * connectivity guarantees that a closed pencil outline always contains the
 * fill (an 8-connected fill would leak through corner-touching boundary
 * pixels on diagonal segments).
 *
 * Iterative — an explicit stack of packed `y * width + x` indices, no
 * recursion, so a full-canvas region cannot overflow the stack — and
 * mark-on-push: a pixel is recoloured when it is pushed, and since the
 * region's colour differs from [colour], a recoloured pixel never matches
 * the target again, so no pixel is pushed twice and no separate visited set
 * is needed.
 *
 * No-ops when (startX, startY) is out of bounds (canvas input may report
 * off-canvas positions, and `CanvasBitmap.get` would throw) or when the
 * clicked pixel already is [colour].
 */
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, colour: Int) {
    if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
    val target = bitmap[startX, startY]
    if (target == colour) return
    val pending = ArrayDeque<Int>()
    bitmap[startX, startY] = colour
    pending.addLast(startY * bitmap.width + startX)
    while (pending.isNotEmpty()) {
        val index = pending.removeLast()
        val x = index % bitmap.width
        val y = index / bitmap.width
        pushIfTarget(bitmap, pending, x - 1, y, target, colour)
        pushIfTarget(bitmap, pending, x + 1, y, target, colour)
        pushIfTarget(bitmap, pending, x, y - 1, target, colour)
        pushIfTarget(bitmap, pending, x, y + 1, target, colour)
    }
}

/** Bounds-checks (x, y), and if it holds [target], recolours it and pushes it. */
private fun pushIfTarget(
    bitmap: CanvasBitmap,
    pending: ArrayDeque<Int>,
    x: Int,
    y: Int,
    target: Int,
    colour: Int,
) {
    if (x in 0 until bitmap.width && y in 0 until bitmap.height && bitmap[x, y] == target) {
        bitmap[x, y] = colour
        pending.addLast(y * bitmap.width + x)
    }
}
