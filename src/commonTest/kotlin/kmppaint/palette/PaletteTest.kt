package kmppaint.palette

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteTest {
    @Test
    fun paletteContainsExactlyTheTwelveDocumentedColoursInOrder() {
        assertEquals(
            listOf(
                0xFF000000.toInt(), // black
                0xFFFFFFFF.toInt(), // white
                0xFF404040.toInt(), // dark grey
                0xFFC0C0C0.toInt(), // light grey
                0xFFFF0000.toInt(), // red
                0xFFFF8000.toInt(), // orange
                0xFFFFFF00.toInt(), // yellow
                0xFF008000.toInt(), // green
                0xFF00FFFF.toInt(), // cyan
                0xFF0000FF.toInt(), // blue
                0xFF800080.toInt(), // purple
                0xFFFF00FF.toInt(), // magenta
            ),
            PALETTE,
        )
    }

    @Test
    fun everyPaletteColourIsOpaque() {
        for (argb in PALETTE) {
            assertEquals(0xFF, argb ushr 24, "alpha of #${argb.toUInt().toString(16)}")
        }
    }

    @Test
    fun paletteHasNoDuplicateColours() {
        assertEquals(PALETTE.size, PALETTE.toSet().size)
    }

    @Test
    fun defaultColourIsInPalette() {
        assertTrue(DEFAULT_COLOUR in PALETTE)
    }
}
