package kmppaint.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kmppaint.AppState

private val BAR_HEIGHT = 48.dp
private val SWATCH_SIZE = 32.dp
private val ACTIVE_BORDER = Color.Black

/** Same grey as the canvas border; keeps white/light swatches visible. */
private val INACTIVE_BORDER = Color(0xFF616161)

/**
 * The fixed colour strip above the canvas (FR-5): one swatch per [PALETTE]
 * colour. The active swatch is marked with a thick black border (FR-6);
 * clicking a swatch dispatches [AppState.selectColour]. Reads
 * [AppState.activeColour] and holds no state of its own (PRD §6.4).
 */
@Composable
fun PaletteBar(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (argb in PALETTE) {
            val active = argb == state.activeColour
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .background(Color(argb))
                    .border(
                        width = if (active) 3.dp else 1.dp,
                        color = if (active) ACTIVE_BORDER else INACTIVE_BORDER,
                    )
                    .clickable { state.selectColour(argb) },
            )
        }
    }
}
