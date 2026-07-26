package kmppaint.palette

/**
 * The fixed colour set (FR-5), stored as packed ARGB `Int`s to match the
 * `CanvasBitmap` pixel format. Pure Kotlin, no Compose imports, so it is
 * unit-testable in `commonTest`; the UI converts `Int ↔ Color` at the
 * boundary.
 *
 * All colours are opaque (alpha `0xFF`) — the bitmap and PNG export assume
 * opaque pixels; `PaletteTest` pins this invariant.
 */

/** Opaque black, packed ARGB. */
val BLACK: Int = 0xFF000000.toInt()

/** Opaque white — reuses the canvas default fill. */
val WHITE: Int = kmppaint.canvas.WHITE

/** Opaque dark grey, packed ARGB. */
val DARK_GREY: Int = 0xFF404040.toInt()

/** Opaque light grey, packed ARGB. */
val LIGHT_GREY: Int = 0xFFC0C0C0.toInt()

/** Opaque red, packed ARGB. */
val RED: Int = 0xFFFF0000.toInt()

/** Opaque orange, packed ARGB. */
val ORANGE: Int = 0xFFFF8000.toInt()

/** Opaque yellow, packed ARGB. */
val YELLOW: Int = 0xFFFFFF00.toInt()

/** Opaque green, packed ARGB. */
val GREEN: Int = 0xFF008000.toInt()

/** Opaque cyan, packed ARGB. */
val CYAN: Int = 0xFF00FFFF.toInt()

/** Opaque blue, packed ARGB. */
val BLUE: Int = 0xFF0000FF.toInt()

/** Opaque purple, packed ARGB. */
val PURPLE: Int = 0xFF800080.toInt()

/** Opaque magenta, packed ARGB. */
val MAGENTA: Int = 0xFFFF00FF.toInt()

/** The fixed colour set (FR-5), in display order. */
val PALETTE: List<Int> = listOf(
    BLACK,
    WHITE,
    DARK_GREY,
    LIGHT_GREY,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    CYAN,
    BLUE,
    PURPLE,
    MAGENTA,
)

/** Active colour at launch. */
val DEFAULT_COLOUR: Int = BLACK
