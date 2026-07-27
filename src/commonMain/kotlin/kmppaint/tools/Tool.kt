package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * A drawing tool. Tools mutate the bitmap directly; AppState signals the
 * change after each event.
 */
sealed interface Tool {
    /** Human-readable label shown on the tool selector button. */
    val label: String

    /** Pointer pressed at (x, y). */
    fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int)

    /** Pointer dragged from (fromX, fromY) to (toX, toY); only called while a stroke is active. */
    fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int)
}

/** Pencil: 1-pixel-wide continuous line in the active colour (FR-2). */
object Pencil : Tool {
    override val label = "Pencil"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        bitmap[x, y] = colour
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
        forEachPixelOnLine(fromX, fromY, toX, toY) { px, py -> bitmap[px, py] = colour }
    }
}

/** Fixed brush stamp radius (US-3); adjustable sizes are a PRD §2 non-goal. */
const val BRUSH_RADIUS = 3

/**
 * Brush: a thick, soft-edged stroke — a radius-[BRUSH_RADIUS] disc stamped
 * along the dragged path in the active colour (FR-3). Stroke continuity is
 * inherited from `forEachPixelOnLine`: its 8-adjacent stamp centres are well
 * inside the disc's reach, so consecutive stamps overlap with no gaps.
 */
object Brush : Tool {
    override val label = "Brush"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        forEachPixelInDisc(x, y, BRUSH_RADIUS) { px, py -> bitmap[px, py] = colour }
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
        forEachPixelOnLine(fromX, fromY, toX, toY) { px, py ->
            forEachPixelInDisc(px, py, BRUSH_RADIUS) { sx, sy -> bitmap[sx, sy] = colour }
        }
    }
}

/** Flood fill: recolours the four-connected region under the pointer. */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return

        val targetColour = bitmap[x, y]
        if (targetColour == colour) return

        val pending = IntArray(bitmap.width * bitmap.height)
        var pendingCount = 0

        bitmap[x, y] = colour
        pending[pendingCount++] = y * bitmap.width + x

        while (pendingCount > 0) {
            val index = pending[--pendingCount]
            val pixelX = index % bitmap.width
            val pixelY = index / bitmap.width

            if (pixelX > 0 && bitmap[pixelX - 1, pixelY] == targetColour) {
                bitmap[pixelX - 1, pixelY] = colour
                pending[pendingCount++] = index - 1
            }
            if (pixelX + 1 < bitmap.width && bitmap[pixelX + 1, pixelY] == targetColour) {
                bitmap[pixelX + 1, pixelY] = colour
                pending[pendingCount++] = index + 1
            }
            if (pixelY > 0 && bitmap[pixelX, pixelY - 1] == targetColour) {
                bitmap[pixelX, pixelY - 1] = colour
                pending[pendingCount++] = index - bitmap.width
            }
            if (pixelY + 1 < bitmap.height && bitmap[pixelX, pixelY + 1] == targetColour) {
                bitmap[pixelX, pixelY + 1] = colour
                pending[pendingCount++] = index + bitmap.width
            }
        }
    }

    override fun onMove(
        bitmap: CanvasBitmap,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        colour: Int,
    ) = Unit
}

/** Every tool, in selector display order. */
val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
