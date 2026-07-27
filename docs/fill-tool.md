# Fill Tool Implementation Plan (Step 6)

## Overview

Add a "Fill" tool (bucket fill / flood fill) that fills a contiguous region of the canvas with the active palette colour. When the user clicks a pixel, the tool replaces every connected pixel of the same colour with the active colour, treating pixels of any other colour as boundaries.

## Algorithm: Scanline Fill

Use a **scanline flood fill** (span-filling algorithm) instead of a simple 4-directional BFS. The scanline approach is the standard for paint programs because it is dramatically faster and uses far less stack memory for large areas.

### How it works

1. Read the colour at the clicked pixel — this is the **target colour**.
2. If target colour == fill colour, return immediately (nothing to do).
3. Maintain a mutable stack (`ArrayDeque<Int>` packing `(x, y)` into a single `Int`).
4. Push the clicked pixel.
5. While the stack is not empty:
   a. Pop a pixel `(x, y)`.
   b. Scan leftwards from `(x, y)` to find the leftmost contiguous pixel of target colour.
   c. Scan rightwards from `(x, y)` to find the rightmost contiguous pixel of target colour.
   d. Fill the entire span from left to right with the fill colour.
   e. For the rows above (`y - 1`) and below (`y + 1`), examine each pixel in the span `[left, right]`:
      - If a pixel above/below is of target colour, find the full span of target-colour pixels on that row (scan left and right from that pixel) and push the centre of that span.
      - Skip ahead past the span to avoid redundant pushes.

### Why scanline over simple BFS

| Aspect | Simple BFS | Scanline fill |
|--------|-----------|---------------|
| Stack entries per fill | ~number of pixels in region | ~number of horizontal segments (far fewer) |
| Canvas reads per fill | 4 × pixel count | ~2 × pixel count |
| Cache locality | Poor (random neighbour access) | Excellent (sequential row access) |

For a canvas that may be 1024×768+, the scanline algorithm is the right choice.

## Implementation

### New file: `src/commonMain/kotlin/kmppaint/tools/Fill.kt`

```kotlin
package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return
        val targetColour = bitmap[x, y]
        if (targetColour == colour) return
        scanlineFill(bitmap, x, y, targetColour, colour)
    }

    override fun onMove(...) = Unit  // fill is a single-click operation
}
```

### Scanline fill implementation

The `scanlineFill` function is a private top-level function in `Fill.kt`:

```
fun scanlineFill(bitmap: CanvasBitmap, startX: Int, startY: Int, targetColour: Int, fillColour: Int)
```

- Uses `bitmap.get(x, y)` for reads — but must **check bounds manually** since `CanvasBitmap.get()` throws on OOB reads. The scanline helper must use a private accessor that catches OOB or checks `inBounds` before reading.
- Uses `bitmap[x, y] = fillColour` for writes — this silently ignores OOB writes, which is correct.
- The stack is an `ArrayDeque<Long>` packing `(x, y)` into a single `Long` via `(x.toLong() shl 32) or y.toLong()` (or `Int` packing if coordinates fit in 16 bits each). Since canvas dimensions fit in int, a simple pair data class or two parallel stacks would also work.

#### Bounds-safe read helper

Since `CanvasBitmap.get()` throws on out-of-bounds reads, and the scanline algorithm must read neighbour pixels that may be off-canvas, create a private helper:

```kotlin
private fun CanvasBitmap.readOrNull(x: Int, y: Int): Int? =
    if (x in 0 until width && y in 0 until height) this[x, y] else null
```

### Registration in `Tool.kt`

Append `Fill` to the `TOOLS` list:

```kotlin
val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
```

The `ToolBar` automatically picks it up — no UI code changes needed.

## Edge Cases

| Case | Behaviour |
|------|-----------|
| Clicked pixel already has the active colour | No-op (early return) |
| Clicked pixel is isolated (all neighbours different) | Fills exactly 1 pixel |
| Entire canvas is one colour | Fills the whole canvas |
| Click outside canvas bounds | `onDown` is never called (event coordinates are clamped by `toBitmapPixel`) but guard at top of `onDown` anyway |
| Fill reaches canvas edge | Bounds-safe reads return `null` (treated as boundary) |
| Very large contiguous region | Scanline algorithm handles efficiently; no stack overflow |

## Testing

### New file: `src/commonTest/kotlin/kmppaint/tools/FillTest.kt`

Tests follow the same conventions as `PencilTest.kt` and `BrushTest.kt` — use `CanvasBitmap`, `WHITE`, manual iteration, `assertEquals`/`assertTrue`:

1. **`onDown fills a bounded rectangle`** — Draw a rectangular border with `Pencil`, then fill inside it; verify interior pixels are fill colour and border pixels are unaffected.

2. **`onDown fill reaches edges`** — Call fill on the centre of an all-white canvas; verify every pixel is the fill colour.

3. **`onDown is a no-op when target colour equals fill colour`** — Fill with `WHITE` on an all-white canvas; verify the buffer is unchanged (compare `bitmap.copyPixels()` before and after, or check version in AppState context).

4. **`onDown fills a single isolated pixel`** — Place one pixel of colour A in a field of colour B; fill that pixel with colour C; verify only that one pixel changed.

5. **`onMove is a no-op`** — Call `Fill.onMove` and verify no pixels change.

6. **`onDown does not bleed through different-colour boundaries`** — Draw two adjacent rectangles of different border colours, fill inside one; verify the other rectangle's interior is untouched.

7. **`fill works at the canvas origin`** — Fill at `(0, 0)` when that pixel matches target colour; verify fill proceeds correctly from the corner.

8. **`fill propagates around obstacles`** — Create a U-shaped barrier, fill inside the cup; verify fill does not leak around the open end.

## Integration Tests (Smoke Tests)

In `AGENTS.md`, update the smoke test procedure to include:

- Draw an unfilled rectangle outline with the Pencil tool.
- Select a fill colour from the palette.
- Click inside the rectangle with the Fill tool active.
- Capture a screenshot and verify the interior is filled with the selected colour while the border remains unchanged.

## Files Changed

| File | Change |
|------|--------|
| `src/commonMain/kotlin/kmppaint/tools/Fill.kt` | **New** — Fill tool implementation |
| `src/commonMain/kotlin/kmppaint/tools/Tool.kt` | Append `Fill` to `TOOLS` list |
| `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` | **New** — unit tests |
| `AGENTS.md` | Update smoke test procedure |

## Future Considerations (Out of Scope)

- **Threshold/tolerance fill** — Fill with a colour tolerance range (useful for anti-aliased edges). Not needed since the canvas uses solid colours.
- **Animated preview** — Showing the fill spreading. Not needed for a desktop paint app.
- **Undo** — Will be handled by a future undo system (Step 7+); the fill tool's bitmap mutations are compatible with any snapshot-based undo.
