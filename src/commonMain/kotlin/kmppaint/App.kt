package kmppaint

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.dp
import kmppaint.canvas.toImageBitmap
import kmppaint.palette.PaletteBar
import kmppaint.tools.ToolBar

const val CANVAS_WIDTH = 800
const val CANVAS_HEIGHT = 600

@Composable
fun App() {
    val state = remember { AppState(CANVAS_WIDTH, CANVAS_HEIGHT) }
    // Re-convert the bitmap whenever the model signals a change.
    val image = remember(state.version) { state.bitmap.toImageBitmap() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF9E9E9E))) {
        PaletteBar(state)
        Row(modifier = Modifier.fillMaxSize()) {
            ToolBar(state)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Canvas",
                    modifier = Modifier
                        .size(CANVAS_WIDTH.dp, CANVAS_HEIGHT.dp)
                        .border(1.dp, Color(0xFF616161))
                        .canvasInput(state),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}
