package kmppaint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kmppaint.canvas.WHITE
import kmppaint.palette.BLUE
import kmppaint.palette.DEFAULT_COLOUR
import kmppaint.palette.PALETTE
import kmppaint.palette.RED
import kmppaint.tools.BRUSH_RADIUS
import kmppaint.tools.Brush
import kmppaint.tools.Pencil

class AppStateTest {
    @Test
    fun freshStateHasDefaultColourActive() {
        val state = AppState(4, 3)
        assertEquals(DEFAULT_COLOUR, state.activeColour)
    }

    @Test
    fun selectColourUpdatesActiveColourForEveryPaletteColour() {
        val state = AppState(4, 3)
        for (argb in PALETTE) {
            state.selectColour(argb)
            assertEquals(argb, state.activeColour)
        }
    }

    @Test
    fun reselectingTheActiveColourIsANoOpAndDoesNotThrow() {
        val state = AppState(4, 3)
        state.selectColour(RED)
        state.selectColour(RED)
        assertEquals(RED, state.activeColour)
    }

    @Test
    fun selectColourRejectsNonPaletteColourAndLeavesActiveColourUnchanged() {
        val state = AppState(4, 3)
        val foreign = 0xFF123456.toInt()
        assertTrue(foreign !in PALETTE)
        assertFailsWith<IllegalArgumentException> { state.selectColour(foreign) }
        assertEquals(DEFAULT_COLOUR, state.activeColour)
    }

    @Test
    fun selectColourDoesNotTouchBitmapOrVersion() {
        val state = AppState(4, 3)
        state.selectColour(RED)
        assertEquals(0, state.version)
        assertTrue(state.bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun freshStateHasPencilActive() {
        val state = AppState(4, 3)
        assertEquals(Pencil, state.activeTool)
    }

    @Test
    fun selectToolDoesNotThrowAndLeavesPencilActive() {
        val state = AppState(4, 3)
        state.selectTool(Pencil)
        assertEquals(Pencil, state.activeTool)
    }

    @Test
    fun onCanvasDownPaintsOnePixelInTheActiveColourAndBumpsVersion() {
        val state = AppState(8, 8)
        state.onCanvasDown(3, 4)
        assertEquals(DEFAULT_COLOUR, state.bitmap[3, 4])
        assertEquals(1, state.version)
        for (y in 0 until state.bitmap.height) {
            for (x in 0 until state.bitmap.width) {
                if (x != 3 || y != 4) {
                    assertEquals(WHITE, state.bitmap[x, y], "neighbour ($x, $y)")
                }
            }
        }
    }

    @Test
    fun fullStrokeDrawsAContinuousLineThroughEveryPointInTheActiveColour() {
        val state = AppState(8, 8)
        // An L-shaped stroke: down at (0, 0), right to (4, 0), down to (4, 4).
        state.onCanvasDown(0, 0)
        state.onCanvasMove(4, 0)
        state.onCanvasMove(4, 4)
        state.onCanvasUp()
        assertEquals(3, state.version, "one version bump per drawing event")
        for (x in 0..4) {
            assertEquals(DEFAULT_COLOUR, state.bitmap[x, 0], "horizontal leg pixel ($x, 0)")
        }
        for (y in 0..4) {
            assertEquals(DEFAULT_COLOUR, state.bitmap[4, y], "vertical leg pixel (4, $y)")
        }
        assertEquals(WHITE, state.bitmap[0, 1], "below the horizontal leg")
        assertEquals(WHITE, state.bitmap[3, 3], "inside the L")
        assertEquals(WHITE, state.bitmap[7, 7], "far corner")
    }

    @Test
    fun onCanvasMoveWithoutAnActiveStrokeIsANoOp() {
        val state = AppState(8, 8)
        state.onCanvasMove(3, 3)
        assertEquals(0, state.version)
        assertTrue(state.bitmap.copyPixels().all { it == WHITE })
    }

    @Test
    fun onCanvasMoveOntoTheAlreadyCurrentPixelIsANoOp() {
        val state = AppState(8, 8)
        state.onCanvasDown(3, 3)
        state.onCanvasMove(3, 3)
        assertEquals(1, state.version)
        assertEquals(DEFAULT_COLOUR, state.bitmap[3, 3])
        assertTrue(state.bitmap.copyPixels().count { it != WHITE } == 1)
    }

    @Test
    fun onCanvasMoveAfterOnCanvasUpIsANoOp() {
        val state = AppState(8, 8)
        state.onCanvasDown(3, 3)
        state.onCanvasUp()
        state.onCanvasMove(5, 5)
        assertEquals(1, state.version)
        assertEquals(WHITE, state.bitmap[5, 5])
    }

    @Test
    fun changingTheActiveColourBetweenStrokesRecoloursOnlyNewStrokes() {
        val state = AppState(8, 8)
        state.onCanvasDown(1, 1)
        state.onCanvasUp()
        state.selectColour(RED)
        state.onCanvasDown(3, 3)
        state.onCanvasUp()
        assertEquals(DEFAULT_COLOUR, state.bitmap[1, 1], "first stroke keeps its colour")
        assertEquals(RED, state.bitmap[3, 3], "second stroke uses the new colour")
    }

    @Test
    fun drawingEventsDoNotChangeActiveColour() {
        val state = AppState(8, 8)
        state.selectColour(BLUE)
        state.onCanvasDown(1, 1)
        state.onCanvasMove(4, 4)
        state.onCanvasUp()
        assertEquals(BLUE, state.activeColour)
    }

    @Test
    fun selectToolSwitchesToBrushAndBackToPencil() {
        val state = AppState(4, 3)
        state.selectTool(Brush)
        assertEquals(Brush, state.activeTool)
        state.selectTool(Pencil)
        assertEquals(Pencil, state.activeTool)
    }

    @Test
    fun brushOnCanvasDownPaintsTheFullDiscInTheActiveColourAndBumpsVersion() {
        val state = AppState(9, 9)
        state.selectTool(Brush)
        state.onCanvasDown(4, 4)
        assertEquals(1, state.version)
        for (y in 0 until state.bitmap.height) {
            for (x in 0 until state.bitmap.width) {
                val dx = x - 4
                val dy = y - 4
                val want = if (dx * dx + dy * dy <= BRUSH_RADIUS * BRUSH_RADIUS) DEFAULT_COLOUR else WHITE
                assertEquals(want, state.bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun fullBrushStrokePaintsAContinuousThickLineInTheActiveColour() {
        val state = AppState(12, 12)
        state.selectColour(RED)
        state.selectTool(Brush)
        state.onCanvasDown(3, 6)
        state.onCanvasMove(7, 6)
        state.onCanvasUp()
        assertEquals(2, state.version, "one version bump per drawing event")
        // Stamps centred at (3..7, 6): a 7 px tall band with rounded caps.
        val expectedRows = mapOf(
            3 to (3..7),
            4 to (1..9),
            5 to (1..9),
            6 to (0..10),
            7 to (1..9),
            8 to (1..9),
            9 to (3..7),
        )
        for (y in 0 until state.bitmap.height) {
            val range = expectedRows[y]
            for (x in 0 until state.bitmap.width) {
                val want = if (range != null && x in range) RED else WHITE
                assertEquals(want, state.bitmap[x, y], "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun eachStrokeUsesTheToolActiveAtItsOwnOnCanvasDown() {
        val state = AppState(12, 12)
        // A pencil stroke: one pixel wide.
        state.onCanvasDown(1, 1)
        state.onCanvasMove(5, 1)
        state.onCanvasUp()
        // A later brush stroke: a thick dab.
        state.selectTool(Brush)
        state.onCanvasDown(6, 6)
        state.onCanvasUp()
        for (x in 1..5) {
            assertEquals(DEFAULT_COLOUR, state.bitmap[x, 1], "pencil line pixel ($x, 1)")
        }
        assertEquals(WHITE, state.bitmap[3, 0], "above the pencil line")
        assertEquals(WHITE, state.bitmap[3, 2], "below the pencil line")
        assertEquals(DEFAULT_COLOUR, state.bitmap[6, 3], "top of the brush dab")
        assertEquals(DEFAULT_COLOUR, state.bitmap[9, 6], "right edge of the brush dab")
        assertEquals(5 + 29, state.bitmap.copyPixels().count { it != WHITE })
    }

    @Test
    fun drawingWithTheBrushDoesNotChangeActiveColourOrActiveTool() {
        val state = AppState(12, 12)
        state.selectColour(BLUE)
        state.selectTool(Brush)
        state.onCanvasDown(6, 6)
        state.onCanvasMove(8, 6)
        state.onCanvasUp()
        assertEquals(BLUE, state.activeColour)
        assertEquals(Brush, state.activeTool)
    }
}
