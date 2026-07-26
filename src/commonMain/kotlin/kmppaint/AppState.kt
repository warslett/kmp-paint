package kmppaint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kmppaint.canvas.CanvasBitmap
import kmppaint.palette.DEFAULT_COLOUR
import kmppaint.palette.PALETTE
import kmppaint.tools.Pencil
import kmppaint.tools.Tool

/**
 * The single observable app state (PRD §6.4): [bitmap], [activeColour], and
 * [activeTool].
 *
 * Tools mutate pixels on [bitmap]; [onBitmapChanged] then bumps [version],
 * which the UI reads as a `remember` key so the bitmap is re-converted and
 * the canvas recomposes. The palette bar observes [activeColour] and
 * dispatches [selectColour]; the tool bar observes [activeTool] and
 * dispatches [selectTool]; the canvas dispatches pointer events to
 * [onCanvasDown] / [onCanvasMove] / [onCanvasUp], which this holder forwards
 * to the active tool — the same unidirectional flow everywhere: UI event →
 * state mutation → recomposition.
 *
 * The in-progress stroke's last pixel is tracked here, in the state holder,
 * because tools are stateless and the UI only delivers raw positions.
 */
class AppState(width: Int, height: Int) {
    val bitmap = CanvasBitmap(width, height)

    var version by mutableStateOf(0)
        private set

    fun onBitmapChanged() {
        version++
    }

    var activeColour by mutableStateOf(DEFAULT_COLOUR)
        private set

    /**
     * Makes [argb] the active colour. Rejects non-palette colours with
     * [IllegalArgumentException]: the UI only ever offers palette colours, so
     * a foreign value is a programming error.
     */
    fun selectColour(argb: Int) {
        require(argb in PALETTE) { "not a palette colour: ${argb.toUInt().toString(16)}" }
        activeColour = argb
    }

    /** Pencil is the default tool, so drawing works immediately on launch (US-2). */
    var activeTool by mutableStateOf<Tool>(Pencil)
        private set

    /**
     * Makes [tool] the active tool. No validation needed: the tool set is
     * sealed, so a foreign value is unrepresentable.
     */
    fun selectTool(tool: Tool) {
        activeTool = tool
    }

    private var strokeActive = false
    private var strokeX = 0
    private var strokeY = 0

    /** Pointer pressed on the canvas: the active tool paints immediately, so a click leaves a dot. */
    fun onCanvasDown(x: Int, y: Int) {
        activeTool.onDown(bitmap, x, y, activeColour)
        strokeActive = true
        strokeX = x
        strokeY = y
        onBitmapChanged()
    }

    /** Pointer dragged on the canvas; a no-op without an active stroke or while still on the same pixel. */
    fun onCanvasMove(x: Int, y: Int) {
        if (!strokeActive) return
        if (x == strokeX && y == strokeY) return
        activeTool.onMove(bitmap, strokeX, strokeY, x, y, activeColour)
        strokeX = x
        strokeY = y
        onBitmapChanged()
    }

    /** Pointer released: ends the stroke. */
    fun onCanvasUp() {
        strokeActive = false
    }
}
