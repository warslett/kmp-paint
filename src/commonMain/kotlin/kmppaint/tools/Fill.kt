package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return
        val targetColour = bitmap[x, y]
        if (targetColour == colour) return
        scanlineFill(bitmap, x, y, targetColour, colour)
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) = Unit
}

private fun scanlineFill(bitmap: CanvasBitmap, startX: Int, startY: Int, targetColour: Int, fillColour: Int) {
    val width = bitmap.width
    val height = bitmap.height
    val stack = ArrayDeque<Long>()

    stack.addLast(pack(startX, startY))

    while (stack.isNotEmpty()) {
        val packed = stack.removeLast()
        val x = unpackX(packed)
        val y = unpackY(packed)

        if (x !in 0 until width || y !in 0 until height) continue
        if (bitmap[x, y] != targetColour) continue

        var left = x
        while (left > 0 && bitmap[left - 1, y] == targetColour) left--

        var right = x
        while (right < width - 1 && bitmap[right + 1, y] == targetColour) right++

        for (px in left..right) {
            bitmap[px, y] = fillColour
        }

        if (y > 0) checkRow(bitmap, left, right, y - 1, targetColour, width, stack)
        if (y < height - 1) checkRow(bitmap, left, right, y + 1, targetColour, width, stack)
    }
}

private fun checkRow(
    bitmap: CanvasBitmap,
    left: Int, right: Int,
    rowY: Int,
    targetColour: Int,
    width: Int,
    stack: ArrayDeque<Long>,
) {
    var px = left
    while (px <= right) {
        if (bitmap[px, rowY] == targetColour) {
            var spanLeft = px
            while (spanLeft > 0 && bitmap[spanLeft - 1, rowY] == targetColour) spanLeft--
            var spanRight = px
            while (spanRight < width - 1 && bitmap[spanRight + 1, rowY] == targetColour) spanRight++
            stack.addLast(pack((spanLeft + spanRight) / 2, rowY))
            px = spanRight + 1
        } else {
            px++
        }
    }
}

private fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)

private fun unpackX(packed: Long): Int = (packed shr 32).toInt()

private fun unpackY(packed: Long): Int = (packed and 0xFFFF_FFFFL).toInt()
