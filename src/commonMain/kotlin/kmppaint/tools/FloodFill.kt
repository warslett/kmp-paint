package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/** Flat (dx, dy) offsets of the 4-connected neighbourhood, iterated per pop without allocating. */
private val NEIGHBOUR_DX = intArrayOf(-1, 1, 0, 0)
private val NEIGHBOUR_DY = intArrayOf(0, 0, -1, 1)

/**
 * Recolours every pixel of the region 4-connected to (seedX, seedY) that
 * shares the seed pixel's colour, replacing it with [colour]; pixels of any
 * other colour bound the region and are left untouched (FR-4).
 *
 * Connectivity is 4-way (N/S/E/W), not 8-way, on purpose: pencil strokes are
 * Bresenham lines, continuous only under 8-adjacency, so a hand-drawn closed
 * shape routinely has corners where boundary pixels touch diagonally only.
 * An 8-connected fill would leak through those joints; a 4-connected fill
 * treats diagonal contact as a sealed edge, which is what lets a pencilled
 * shape be filled by clicking inside it.
 *
 * Iterative with an explicit stack, never recursive: a worst-case fill of the
 * 800×600 canvas would nest hundreds of thousands of frames and overflow the
 * JVM stack. Each pixel is painted as it is pushed, so the bitmap itself
 * doubles as the visited set and each pixel is pushed at most once — O(filled
 * pixels) time and stack heap. That makes the [target] == [colour] early
 * return a correctness requirement: if the fill colour equalled the target,
 * painting would not register the visited marker and neighbours would be
 * re-pushed forever (it also makes clicking an already-correct region a free
 * no-op).
 *
 * Out-of-bounds seeds are ignored, honouring the `CanvasBitmap` contract that
 * callers check bounds before reading. The canvas edge bounds the region
 * exactly: border pixels are filled, nothing wraps around.
 */
fun floodFill(bitmap: CanvasBitmap, seedX: Int, seedY: Int, colour: Int) {
    if (seedX !in 0 until bitmap.width || seedY !in 0 until bitmap.height) return
    val target = bitmap[seedX, seedY]
    if (target == colour) return

    val pending = ArrayDeque<Int>() // packed y * width + x
    bitmap[seedX, seedY] = colour // paint-on-push: colour doubles as the visited marker
    pending.addLast(seedY * bitmap.width + seedX)

    while (pending.isNotEmpty()) {
        val packed = pending.removeLast()
        val x = packed % bitmap.width
        val y = packed / bitmap.width
        for (i in NEIGHBOUR_DX.indices) {
            val nx = x + NEIGHBOUR_DX[i]
            val ny = y + NEIGHBOUR_DY[i]
            if (nx in 0 until bitmap.width && ny in 0 until bitmap.height && bitmap[nx, ny] == target) {
                bitmap[nx, ny] = colour
                pending.addLast(ny * bitmap.width + nx)
            }
        }
    }
}
