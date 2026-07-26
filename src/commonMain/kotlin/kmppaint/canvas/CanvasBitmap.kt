package kmppaint.canvas

/** Opaque white, packed ARGB (`0xAARRGGBB`). Default fill per FR-1. */
val WHITE: Int = 0xFFFFFFFF.toInt()

/**
 * Platform-agnostic raster bitmap: a flat [IntArray] of packed ARGB colours,
 * row-major, index = `y * width + x`.
 *
 * Semantics (see docs/plans/step2.md §2.2):
 * - Writes to out-of-bounds coordinates are silently ignored, so strokes
 *   crossing the canvas edge neither crash nor wrap around.
 * - Reads from out-of-bounds coordinates throw [IllegalArgumentException];
 *   callers (e.g. flood fill) must check bounds themselves.
 */
class CanvasBitmap(val width: Int, val height: Int, fill: Int = WHITE) {
    init {
        require(width > 0 && height > 0) { "width and height must be positive, got ${width}x$height" }
    }

    private val pixels: IntArray = IntArray(width * height) { fill }

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Int {
        require(inBounds(x, y)) { "($x, $y) out of bounds for ${width}x$height" }
        return pixels[y * width + x]
    }

    operator fun set(x: Int, y: Int, argb: Int) {
        if (inBounds(x, y)) {
            pixels[y * width + x] = argb
        }
    }

    /** Recolours the whole buffer. */
    fun fill(argb: Int) {
        pixels.fill(argb)
    }

    /** Defensive copy of the buffer for the rendering pipeline. */
    fun copyPixels(): IntArray = pixels.copyOf()
}
