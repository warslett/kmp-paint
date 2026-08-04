package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Fill (flood fill / bucket): recolours every 4-connected pixel that shares
 * the clicked pixel's colour, stopping at any pixel of a different colour
 * (FR-4). The clicked pixel's colour is read as the target; the fill then
 * expands to all 4-adjacent pixels of the same colour.
 *
 * 4-connectivity (not 8) is deliberate: a Pencil outline is 8-connected, so an
 * 8-connected fill could slip diagonally between two corner-touching outline
 * pixels and leak out of a closed shape. The canvas boundary acts as an edge
 * like any other colour.
 *
 * Fill acts entirely on press: dragging with it active does nothing beyond the
 * initial click, so `onMove` is a no-op. A click in a region already in the
 * active colour is likewise a no-op.
 */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        floodFill(bitmap, x, y, colour)
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
    }
}

/**
 * Recolours the 4-connected region of the clicked pixel's colour on [bitmap]
 * to [newColour] (classic iterative scanline flood fill).
 *
 * A seed's row is recoloured in place as it is processed, so a pixel is never
 * considered twice and no separate visited set is needed. Every read is
 * preceded by a bounds check — `CanvasBitmap.get` throws out of bounds. The
 * canvas edge is simply another boundary: a region touching it fills up to it
 * and stops.
 *
 * Stale seeds: several runs can push into the same child run, so a popped seed
 * may already have been recoloured by an earlier run in its row; such seeds
 * are skipped (recolouring the run twice would be harmless, but expanding it
 * again could cross into an adjacent, unrelated run of the same colour).
 */
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, newColour: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (startX !in 0 until w || startY !in 0 until h) return
    val target = bitmap[startX, startY]
    if (target == newColour) return

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(startX to startY)

    while (stack.isNotEmpty()) {
        val (seedX, seedY) = stack.removeLast()
        if (bitmap[seedX, seedY] != target) continue

        var x = seedX
        while (x > 0 && bitmap[x - 1, seedY] == target) x--
        val spanLeft = x
        var spanRight = x
        while (x < w && bitmap[x, seedY] == target) {
            bitmap[x, seedY] = newColour
            spanRight = x
            x++
        }

        for (row in intArrayOf(seedY - 1, seedY + 1)) {
            if (row !in 0 until h) continue
            var scan = spanLeft
            while (scan <= spanRight) {
                if (bitmap[scan, row] == target) {
                    stack.addLast(scan to row)
                    while (scan <= spanRight && bitmap[scan, row] == target) scan++
                } else {
                    scan++
                }
            }
        }
    }
}
