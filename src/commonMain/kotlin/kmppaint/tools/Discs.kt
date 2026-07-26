package kmppaint.tools

/**
 * Invokes [plot] for every pixel of the filled disc of [radius] centred at
 * (cx, cy): all (cx + dx, cy + dy) with dx² + dy² ≤ radius², plotted row by
 * row (ascending dy, then ascending dx). No coordinate clamping:
 * `CanvasBitmap.set` already ignores out-of-bounds writes, so stamps centred
 * near or beyond the canvas edge clip harmlessly.
 */
fun forEachPixelInDisc(cx: Int, cy: Int, radius: Int, plot: (x: Int, y: Int) -> Unit) {
    val radiusSquared = radius * radius
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            if (dx * dx + dy * dy <= radiusSquared) {
                plot(cx + dx, cy + dy)
            }
        }
    }
}
