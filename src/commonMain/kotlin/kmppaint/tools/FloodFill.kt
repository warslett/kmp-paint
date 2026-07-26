package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * N/S/E/W neighbour offsets. The fill is deliberately 4-connected, not
 * 8-connected (see docs/fill-tool.md §3.1): `forEachPixelOnLine` is Bresenham,
 * so a diagonal pencil stroke lays down pixels that are only *diagonally*
 * adjacent. An 8-connected fill would escape through those gaps and leak out
 * of any diagonally-drawn outline; a 4-connected fill treats a diagonal chain
 * as a wall, so hand-drawn closed shapes contain the fill.
 */
private val NEIGHBOUR_DX = intArrayOf(0, 0, -1, 1)
private val NEIGHBOUR_DY = intArrayOf(-1, 1, 0, 0)

/** Starting size of the index stack; it doubles on demand, so this is only a guess at typical fills. */
private const val INITIAL_STACK_CAPACITY = 1024

/**
 * Recolours the maximal 4-connected region of pixels sharing the colour at
 * (seedX, seedY) with [colour] (FR-4). Pixels of any other colour bound the
 * region: they are never recoloured and the fill never crosses them.
 *
 * Unlike [forEachPixelOnLine] and [forEachPixelInDisc], which are pure
 * geometry generators taking a `plot` callback, this takes the bitmap
 * directly. It has to: the region depends on the pixels already painted, so
 * reading and writing cannot be separated.
 *
 * Implementation notes (docs/fill-tool.md §3.2–3.4):
 * - Iterative, with an explicit [IntArray] stack of packed `y * width + x`
 *   indices. The worst case is the whole 800×600 canvas, where the textbook
 *   recursive version would overflow the JVM stack; a primitive array also
 *   avoids the boxing an `ArrayDeque<Int>` would incur.
 * - Pixels are recoloured when *pushed*, not when popped, so the fill colour
 *   doubles as the visited mark: a painted pixel no longer matches the target
 *   and cannot be pushed again. Every pixel is therefore pushed at most once.
 * - No-op if the seed is out of bounds — `CanvasBitmap.get` throws on
 *   out-of-bounds reads, and pointer events can carry off-canvas coordinates.
 * - No-op if the seed already holds [colour]. This guard is required for
 *   *termination*, not just speed: with paint-as-visited, painting a pixel its
 *   existing colour never stops it matching, so the loop would never end.
 */
fun floodFill(bitmap: CanvasBitmap, seedX: Int, seedY: Int, colour: Int) {
    val width = bitmap.width
    val height = bitmap.height
    if (seedX !in 0 until width || seedY !in 0 until height) return

    val target = bitmap[seedX, seedY]
    if (target == colour) return

    var stack = IntArray(INITIAL_STACK_CAPACITY)
    var top = 0

    bitmap[seedX, seedY] = colour
    stack[top++] = seedY * width + seedX

    while (top > 0) {
        val packed = stack[--top]
        val x = packed % width
        val y = packed / width
        for (i in NEIGHBOUR_DX.indices) {
            val nx = x + NEIGHBOUR_DX[i]
            val ny = y + NEIGHBOUR_DY[i]
            if (nx !in 0 until width || ny !in 0 until height) continue
            if (bitmap[nx, ny] != target) continue
            bitmap[nx, ny] = colour
            if (top == stack.size) {
                stack = stack.copyOf(stack.size * 2)
            }
            stack[top++] = ny * width + nx
        }
    }
}
