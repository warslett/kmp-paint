package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Replaces the contiguous region of like-coloured pixels containing (x, y)
 * with [colour]: the pixel's own colour is the *target*, and every pixel
 * reachable from (x, y) through an unbroken run of the target colour is
 * repainted. Pixels of any other colour bound the region and are left alone,
 * so clicking inside a closed shape fills only its interior (FR-4).
 *
 * Spread is **4-connected** (up/down/left/right), while `forEachPixelOnLine`
 * draws 8-adjacent lines. That pairing is deliberate: a diagonal pencil stroke
 * is a chain of diagonally-touching pixels, which an 8-connected fill would
 * squeeze between — leaking out of any slanted shape — but which a 4-connected
 * fill treats as a solid wall.
 *
 * Unlike the pencil and brush this function cannot rely on `CanvasBitmap.set`
 * ignoring out-of-bounds writes, because it *reads* as it goes and
 * `CanvasBitmap.get` throws out of bounds; every read below is therefore
 * guarded by the canvas edges.
 *
 * No-ops when (x, y) is off-canvas, and when the target colour is already
 * [colour] — the latter would otherwise never terminate, since repainted
 * pixels would go on matching the target.
 */
fun floodFill(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
    if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return
    val target = bitmap[x, y]
    if (target == colour) return

    // Scanline flood fill: fill a whole horizontal run at a time and seed only
    // one pixel per adjacent run on the rows above and below. Recursing per
    // pixel would nest as deep as the canvas has pixels and overflow the
    // stack; even a per-pixel explicit stack would hold hundreds of thousands
    // of entries, whereas spans number in the hundreds.
    val stack = ArrayDeque<Int>()
    stack.addLast(y * bitmap.width + x)

    while (stack.isNotEmpty()) {
        val seed = stack.removeLast()
        val seedY = seed / bitmap.width
        val seedX = seed % bitmap.width
        // The run this seed belonged to may already have been filled by a
        // neighbouring span before we got to it.
        if (bitmap[seedX, seedY] != target) continue

        var left = seedX
        while (left - 1 >= 0 && bitmap[left - 1, seedY] == target) left--
        var right = seedX
        while (right + 1 < bitmap.width && bitmap[right + 1, seedY] == target) right++
        for (px in left..right) {
            bitmap[px, seedY] = colour
        }

        for (row in intArrayOf(seedY - 1, seedY + 1)) {
            if (row < 0 || row >= bitmap.height) continue
            var px = left
            while (px <= right) {
                if (bitmap[px, row] == target) {
                    stack.addLast(row * bitmap.width + px)
                    // Skip the rest of this run: one seed per run is enough.
                    while (px <= right && bitmap[px, row] == target) px++
                } else {
                    px++
                }
            }
        }
    }
}
