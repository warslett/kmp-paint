package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * A drawing tool. Tools mutate the bitmap directly; AppState signals the
 * change after each event. Steps 5–6 add Brush and Fill without touching UI
 * code.
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

/** Every tool, in selector display order. Step 6 appends Fill. */
val TOOLS: List<Tool> = listOf(Pencil, Brush)
