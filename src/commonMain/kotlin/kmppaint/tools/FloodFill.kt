package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * 4-connected flood fill seeded at (startX, startY): every pixel reachable
 * from the seed by 4-adjacent steps where `bitmap[x, y] == target` is
 * repainted with [replacement]. Pixels of any other colour act as fill
 * boundaries, so a closed pencil outline encloses a region that the fill
 * paints without leaking past it.
 *
 * Iterative (explicit `ArrayList<Int>` stack of (x, y) pairs), **not**
 * recursive: a 800×600 canvas can have 480 000 pixels and the default call
 * stack would overflow on a deep recursion (long thin spiral regions). The
 * replacement write doubles as the "visited" mark, so no separate visited
 * bitmap is needed and no pixel is pushed more than four times.
 *
 * Bounds are checked **before** each read because `CanvasBitmap.get` throws
 * on out-of-bounds reads (CanvasBitmap.kt:25-28); out-of-bounds pops are
 * discarded.
 */
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, replacement: Int) {
    if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
    val target = bitmap[startX, startY]
    if (target == replacement) return

    val width = bitmap.width
    val height = bitmap.height
    // Stack of (x, y) pairs packed as consecutive Ints; largest possible is
    // the whole canvas, so size capacity roughly to avoid frequent grows.
    val stack = ArrayList<Int>(width * height * 2)
    stack.add(startX)
    stack.add(startY)
    while (stack.isNotEmpty()) {
        val y = stack.removeAt(stack.lastIndex)
        val x = stack.removeAt(stack.lastIndex)
        if (x !in 0 until width || y !in 0 until height) continue
        if (bitmap[x, y] != target) continue
        bitmap[x, y] = replacement
        stack.add(x + 1); stack.add(y)
        stack.add(x - 1); stack.add(y)
        stack.add(x); stack.add(y + 1)
        stack.add(x); stack.add(y - 1)
    }
}