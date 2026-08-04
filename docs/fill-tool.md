# Fill tool — implementation plan

## Goal

Add a Fill tool (FR: "bucket" / flood fill) so the user can click anywhere on the
canvas and flood-fill the contiguous region of the clicked pixel's colour up to
the nearest pixels of any other colour, which act as the region's boundary. In
particular, a closed shape drawn with the Pencil tool must be fillable from any
pixel inside it without leaking through or past the outline.

## Current state

- Tools are stateless `object`s implementing the `Tool` sealed interface
  (`src/commonMain/kotlin/kmppaint/tools/Tool.kt`), with `Pencil` and `Brush`
  today. Each has `onDown(bitmap, x, y, colour)` and
  `onMove(bitmap, fromX, fromY, toX, toY, colour)`.
- `TOOLS` is the selector list in display order (`Tool.kt:58`); `ToolBar`
  renders one button per entry.
- `AppState` (`src/commonMain/kotlin/kmppaint/AppState.kt`) forwards every
  `onCanvasDown` to `activeTool.onDown`, then bumps `version` to trigger a
  recomposition. `onCanvasMove` is a no-op without an active stroke.
- `CanvasBitmap` (`src/commonMain/kotlin/kmppaint/canvas/CanvasBitmap.kt`):
  `get(x, y)` **throws** on out-of-bounds reads (callers must bounds-check);
  `set(x, y, argb)` silently ignores out-of-bounds writes. Its doc comment
  already anticipates a flood-fill caller.
- The canvas is 800×600 (`App.kt:22`), i.e. 480,000 pixels, so the fill must be
  iterative — a recursive DFS can overflow the stack.
- `BrushTest.toolsListIsExactlyPencilThenBrushInSelectorOrder` pins the `TOOLS`
  list and must be extended.

## Design

### Connectivity: 4-connected

Only 4-adjacent neighbours (`(x±1, y)`, `(x, y±1)`) are considered part of the
same region. This is required for the Pencil-outline use case: a 1-pixel Pencil
line is 8-connected, so consecutive outline pixels touch only at a corner. An
8-connected fill could slip diagonally between two corner-touching outline
pixels and leak out; a 4-connected fill cannot. This also matches the reference
MS Paint behaviour.

### Algorithm: iterative scanline flood fill

Use a classic scanline fill with an explicit stack of seeds instead of a
recursive DFS:

1. Read the target colour `target = bitmap[startX, startY]`. If the click is
   out of bounds, or `target == newColour`, do nothing.
2. Push the click point as the first seed.
3. Pop a seed `(x, y)`. On its row, walk left to the start of the contiguous
   run of `target`-coloured pixels, then recolour that whole run to `newColour`
   while walking right, remembering the run's horizontal extent `[spanLeft,
   spanRight]`.
4. For the rows `y-1` and `y+1`, scan the horizontal range of the run for
   contiguous `target`-coloured runs; push one seed per run found. Because the
   region is recoloured in place to `newColour` as it is processed, a pixel is
   never visited twice and no separate visited array is needed.
5. Repeat until the stack is empty.

Every `bitmap[x, y]` read is guarded by an explicit bounds check first, because
`CanvasBitmap.get` throws on out-of-bounds reads. The canvas boundary is simply
another edge: a region touching the canvas edge fills up to it and stops there,
which is the desired behaviour (no wrap-around).

### Tool integration

Fill is a click-to-act tool: it acts entirely on `onDown` and ignores moves.

- New file `src/commonMain/kotlin/kmppaint/tools/Fill.kt`:
  - `object Fill : Tool` with `label = "Fill"`.
  - `onDown(bitmap, x, y, colour)` delegates to a top-level
    `floodFill(bitmap, x, y, colour)` in the same file.
  - `onMove(...)` is an empty no-op (mirrors the code style of the other tools).
- `Tool.kt:58`: `TOOLS = listOf(Pencil, Brush, Fill)` so the selector shows it
  third. The `ToolBar` and `AppState` need **no changes**: the toolbar iterates
  `TOOLS` and `AppState` forwards events generically.

This mirrors the existing pattern of helper geometry functions living outside
`Tool.kt` (`Discs.kt`, `Lines.kt`) while the tool `object`s live in `Tool.kt`.

### Behaviour on `AppState`

- A click with Fill active calls `onCanvasDown` → `Fill.onDown` → full-region
  flood fill, then bumps `version` once. Dragging while holding the button is
  harmless: `onCanvasMove` is a no-op (same as a plain Pencil click).
- A no-op fill (clicked colour already equals the active colour) still bumps
  `version` once and repaints the identical frame. This keeps the `Tool`
  interface and `AppState` unchanged; it is cosmetically irrelevant and not
  worth an interface change. (Alternative, rejected: making `onDown` return a
  "did anything change" Boolean — churns every tool and `AppState` for no user
  benefit.)

### Reference scanline sketch

```kotlin
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, newColour: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (startX !in 0 until w || startY !in 0 until h) return
    val target = bitmap[startX, startY]
    if (target == newColour) return

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(startX to startY)

    while (stack.isNotEmpty()) {
        val (seedX, seedY) = stack.removeLast()

        var x = seedX
        while (x >= 0 && bitmap[x, seedY] == target) x--
        var spanLeft = x + 1
        x = spanLeft
        var spanRight = spanLeft
        while (x < w && bitmap[x, seedY] == target) {
            bitmap[x, seedY] = newColour
            spanRight = x
            x++
        }

        for (row in intArrayOf(seedY - 1, seedY + 1)) {
            if (row !in 0 until h) continue
            var scan = spanLeft
            while (scan <= spanRight) {
                if (bitmap[scan, row] == target) {
                    stack.addLast(scan to row)
                    while (scan <= spanRight && bitmap[scan, row] == target) scan++
                } else {
                    scan++
                }
            }
        }
    }
}
```

Notes on the sketch:

- The seed's own run never needs the bounds guard beyond the initial click
  check: seeds are always in-bounds target pixels.
- Runs in the rows above/below that extend past the current span are found via
  the seed's own left/right expansion, so scanning only `spanLeft..spanRight`
  per row is sufficient (a neighbour row's run can only connect to the current
  run if it overlaps it horizontally).
- `ArrayDeque` is available in Kotlin common code. Worst-case stack growth is
  O(region size); for 800×600 that is a few megabytes at most, acceptable.
  A scanline variant with an `IntArray`-backed stack would be a later
  optimisation if ever needed.

## Files to change

| File | Change |
| --- | --- |
| `src/commonMain/kotlin/kmppaint/tools/Fill.kt` | **New**: `object Fill : Tool` + `floodFill` helper |
| `src/commonMain/kotlin/kmppaint/tools/Tool.kt` | Append `Fill` to `TOOLS` (`Tool.kt:58`) |
| `src/commonMain/kotlin/kmppaint/tools/BrushTest.kt` | Update `toolsListIsExactlyPencilThenBrushInSelectorOrder` (`BrushTest.kt:139`) to `listOf(Pencil, Brush, Fill)` |
| `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` | **New**: unit tests below |

No changes to `AppState`, `CanvasInput`, `ToolBar`, `App`, or `CanvasBitmap` are
needed.

## Tests

New `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` (mirroring the exhaustive
assert-bitmap style of `PencilTest`/`BrushTest`), using a helper that draws a
Pencil-closed square on a small canvas:

1. **Click inside a closed Pencil square fills the interior only** — the
   interior becomes the fill colour, while the outline pixels and everything
   outside the outline stay unchanged.
2. **Clicking the outline recolours the outline region only** — the target
   colour is read from the clicked pixel, so clicking a boundary pixel fills the
   connected run of that same colour (the loop), not the interior.
3. **Clicking an open canvas fills edge-to-edge up to any drawn line** — a
   straight Pencil line splits the canvas; clicking one side fills that half and
   stops exactly at the line.
4. **Fill respects 4-connectivity** — two same-colour blobs touching only at a
   corner are NOT merged by a fill of one of them.
5. **Filling with the same colour already present is a no-op** — clicking a
   region whose colour equals the fill colour changes nothing.
6. **A region touching the canvas edge fills up to the edge** — canvas bounds
   act as an edge; no wrap-around and no throw.
7. **Out-of-bounds click does not throw** — `Fill.onDown(bitmap, -1, 0, colour)`
   and `Fill.onDown(bitmap, w, 0, colour)` are silent no-ops.
8. **Flood fill terminates on a large canvas** — filling a 800×600 blank canvas
   from the centre recolours every pixel and completes quickly (guards against
   the recursion/stack-overflow failure mode).

Existing tests to extend:

- `BrushTest.kt:139` — update the pinned `TOOLS` list to `listOf(Pencil, Brush, Fill)`.
- Optionally `AppStateTest`: `selectTool(Fill)` then `onCanvasDown` inside a
  shape bumps `version` once and fills the region; a drag after the click leaves
  the fill unchanged; drawing/filling does not change `activeTool` or
  `activeColour`.

## Acceptance criteria

- With Pencil, draw a closed shape on a blank canvas. Switching to Fill and
  clicking any interior pixel recolours exactly the interior to the active
  palette colour; the outline and the outside stay untouched.
- Clicking outside the shape (but still on canvas) fills the outside up to the
  outline and up to the canvas edge.
- Clicking a pixel whose colour already equals the active colour is a no-op.
- A click only ever bumps the canvas version once (one recomposition).
- The Fill button appears third in the tool selector and is selectable like the
  others.

### Headless smoke test (per AGENTS.md)

Run the app under Xvfb with `-Dskiko.renderApi=SOFTWARE`, then with python-xlib:
pencil a closed rectangle, switch to Fill, click the rectangle's interior, and
capture `import -window root`. Verify with `compare -metric AE` that the
interior block changed to the selected swatch colour and the outline/outside
pixels are unchanged; a second `compare` against the pre-fill shot must report a
nonzero (and predictable) number of differing pixels equal to the interior area.
