package kmppaint.canvas

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Converts this bitmap to a Compose [ImageBitmap] for drawing.
 * Platform-specific; each target provides its own `actual`.
 */
expect fun CanvasBitmap.toImageBitmap(): ImageBitmap
