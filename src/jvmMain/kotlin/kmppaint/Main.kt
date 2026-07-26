package kmppaint

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "kmp-paint",
            state = rememberWindowState(size = DpSize(960.dp, 720.dp)),
        ) {
            App()
        }
    }
