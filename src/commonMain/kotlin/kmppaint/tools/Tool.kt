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

/**
 * Fill: flood-fills the 4-connected region of same-coloured pixels starting
 * at the clicked pixel with the active colour (bucket fill). Unlike
 * Pencil/Brush, dragging does not repeatedly re-fill — see [onMove].
 */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        forEachPixelInFloodFillRegion(bitmap, x, y) { px, py -> bitmap[px, py] = colour }
    }

    /**
     * No-op: a flood fill is a single discrete action triggered by the
     * initial click, not a continuous stroke. Re-running the flood fill on
     * every dragged-over pixel would be wasteful (re-scanning the whole
     * region on every pixel of movement) and semantically wrong (a bucket
     * tool dispenses once per click, it does not paint a smear like
     * Pencil/Brush).
     */
    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
    }
}

/** Every tool, in selector display order. */
val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
