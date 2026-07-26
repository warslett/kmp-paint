package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Recolours every pixel 4-connected to (seedX, seedY) that shares its colour
 * (flood fill, FR-4): the region bounded by pixels of any other colour is
 * repainted in [colour]. Region matching is plain `==` on the packed ARGB
 * value — palette colours are exact and opaque, and the pencil/brush write
 * exactly those values, so no tolerance is needed.
 *
 * Connectivity is 4-way (N/S/E/W), not 8-way: pencil strokes are Bresenham
 * lines, continuous only under 8-adjacency, so a hand-drawn closed shape
 * routinely has corners where boundary pixels touch diagonally only. An
 * 8-connected fill would leak through those diagonal joints; a 4-connected
 * fill treats diagonal contact as a sealed edge (and matches MS Paint).
 *
 * The algorithm is iterative with an explicit work stack — never recursion.
 * The canvas is 800×600 = 480,000 px, and a worst-case fill (empty canvas)
 * would nest one stack frame per pixel and overflow the JVM stack. The stack
 * holds packed coordinates (`y * width + x`); depth-first (`removeLast`)
 * keeps the pending set small on typical fills. O(filled pixels) time and
 * heap.
 *
 * Visited marking is done by painting: each pixel is recoloured *as it is
 * pushed*, not as it is popped, so the bitmap itself doubles as the visited
 * set and each pixel is pushed at most once. Consequently the early return
 * `if (target == colour) return` is a correctness requirement, not an
 * optimisation: if the fill colour equalled the target colour, painting
 * would not change the pixel, the visited marker would never register, and
 * the loop would re-push neighbours forever. It also makes clicking a region
 * that already has the active colour a free no-op.
 *
 * An out-of-bounds seed is a no-op: `CanvasBitmap.get` throws out of bounds,
 * so the seed coordinates are validated before the first read, per the
 * `CanvasBitmap` contract.
 */
fun floodFill(bitmap: CanvasBitmap, seedX: Int, seedY: Int, colour: Int) {
    if (seedX !in 0 until bitmap.width || seedY !in 0 until bitmap.height) return
    val target = bitmap[seedX, seedY]
    if (target == colour) return

    val width = bitmap.width
    val height = bitmap.height
    val pending = ArrayDeque<Int>() // packed y * width + x
    bitmap[seedX, seedY] = colour // paint-on-push: colour doubles as visited marker
    pending.addLast(seedY * width + seedX)

    while (pending.isNotEmpty()) {
        val packed = pending.removeLast()
        val x = packed % width
        val y = packed / width
        for (i in DX.indices) {
            val nx = x + DX[i]
            val ny = y + DY[i]
            if (nx in 0 until width && ny in 0 until height && bitmap[nx, ny] == target) {
                bitmap[nx, ny] = colour
                pending.addLast(ny * width + nx)
            }
        }
    }
}

/** 4-connected (N/S/E/W) neighbour offsets; diagonal contact stays a sealed edge. */
private val DX = intArrayOf(-1, 1, 0, 0)
private val DY = intArrayOf(0, 0, -1, 1)
