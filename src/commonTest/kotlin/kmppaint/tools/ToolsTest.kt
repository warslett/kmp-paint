package kmppaint.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tool registry itself, rather than any one tool's drawing behaviour.
 * `ToolBar` renders one button per [TOOLS] entry in list order, so this file
 * pins the selector's contents and ordering — it lived in `BrushTest` until
 * two consecutive tool additions showed it belongs somewhere neutral.
 */
class ToolsTest {
    @Test
    fun toolsListIsExactlyPencilBrushFillInSelectorOrder() {
        assertEquals(listOf(Pencil, Brush, Fill), TOOLS)
    }

    @Test
    fun everyToolHasADistinctNonBlankLabel() {
        val labels = TOOLS.map { it.label }
        assertTrue(labels.none { it.isBlank() }, "labels: $labels")
        assertEquals(labels.size, labels.toSet().size, "labels must be unique: $labels")
    }
}
