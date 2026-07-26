package kmppaint.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

actual fun CanvasBitmap.toImageBitmap(): ImageBitmap =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        .apply { setRGB(0, 0, width, height, copyPixels(), 0, width) }
        .toComposeImageBitmap()
