package kmppaint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import kotlin.math.floor

/**
 * Pointer handling for the drawing canvas: a press paints immediately (a
 * single click leaves a dot) and drags paint continuous segments, dispatched
 * to [AppState.onCanvasDown] / [AppState.onCanvasMove] / [AppState.onCanvasUp].
 *
 * Raw pointer events are used instead of `detectDragGestures`, which would
 * wait for the touch slop before reporting and so delay the first pixels of a
 * stroke. Positions arrive in raw screen pixels, while the canvas is shown at
 * 1 bitmap pixel = 1 dp, so the px→dp conversion in [toBitmapPixel] is what
 * maps the pointer to the pixel under it at any screen density. Drags may
 * leave the canvas mid-stroke: out-of-bounds writes are ignored by the bitmap
 * and the stroke resumes seamlessly when the pointer re-enters. Desktop mouse
 * only: the first pointer change is tracked.
 */
fun Modifier.canvasInput(state: AppState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        state.onCanvasDown(toBitmapPixel(down.position.x), toBitmapPixel(down.position.y))
        while (true) {
            val change = awaitPointerEvent().changes.first()
            if (!change.pressed) break
            state.onCanvasMove(toBitmapPixel(change.position.x), toBitmapPixel(change.position.y))
            change.consume()
        }
        state.onCanvasUp()
    }
}

/**
 * Converts a raw pointer offset in screen pixels to a bitmap pixel
 * coordinate: px → dp (the canvas shows 1 bitmap pixel = 1 dp), then floor —
 * pixel (x, y) occupies [x, x+1) × [y, y+1) dp.
 */
private fun Density.toBitmapPixel(offsetPx: Float): Int = floor(offsetPx.toDp().value).toInt()
