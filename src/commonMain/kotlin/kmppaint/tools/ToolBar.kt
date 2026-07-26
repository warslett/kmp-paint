package kmppaint.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kmppaint.AppState

private val BUTTON_WIDTH = 64.dp
private val BUTTON_HEIGHT = 40.dp
private val ACTIVE_BORDER = Color.Black

/** Same grey as the canvas border and the inactive palette swatches. */
private val INACTIVE_BORDER = Color(0xFF616161)

/**
 * The tool selector strip left of the canvas (US-7): one button per [TOOLS]
 * entry. The active tool is marked with a thick black border (FR-6) — the
 * same idiom as the palette bar. Reads [AppState.activeTool] and dispatches
 * [AppState.selectTool]; holds no state of its own (PRD §6.4).
 */
@Composable
fun ToolBar(state: AppState) {
    Column(
        modifier = Modifier.fillMaxHeight().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tool in TOOLS) {
            val active = tool == state.activeTool
            Box(
                modifier = Modifier
                    .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                    .border(
                        width = if (active) 3.dp else 1.dp,
                        color = if (active) ACTIVE_BORDER else INACTIVE_BORDER,
                    )
                    .clickable { state.selectTool(tool) },
                contentAlignment = Alignment.Center,
            ) {
                Text(tool.label)
            }
        }
    }
}
