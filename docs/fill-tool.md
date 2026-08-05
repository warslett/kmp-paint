# Fill Tool — Implementation Plan

## Overview

Add a Fill (flood fill / paint bucket) tool: clicking a pixel recolours the
entire contiguous region of that pixel's colour, stopping at any pixel of a
different colour. The acceptance scenario is the classic Paint one: draw a
closed shape with the Pencil, pick a palette colour, click inside the shape
with Fill, and only the interior is recoloured.

The architecture already anticipates this: `Tool.kt` says "Steps 5–6 add
Brush and Fill without touching UI code" and "Step 6 appends Fill", and
`CanvasBitmap`'s contract notes that "callers (e.g. flood fill) must check
bounds themselves".

## Design decisions

### 4-connectivity (not 8-connectivity)

The fill expands to the **4-adjacent** neighbours (up/down/left/right) of each
pixel, matching classic Paint. This is required for correctness against
pencil-drawn boundaries:

- `forEachPixelOnLine` (Bresenham) produces **8-connected** strokes: on
  diagonal segments consecutive pixels touch only at corners.
- A 4-connected fill cannot pass between two corner-touching boundary pixels
  (any 4-path between them goes *through* one of them), so 8-connected
  pencil outlines always contain a 4-connected fill.
- An 8-connected fill would leak through every diagonal section of a pencil
  outline. Rejected.

Consequence (accepted, matches classic Paint): clicking *on* a 1-px diagonal
boundary recolours only the 4-connected component of that boundary, not
necessarily the whole outline.

### Exact colour match, no tolerance

Region membership is `pixel == targetColour` with exact `Int` equality. Safe
here: all palette colours are opaque, rendering is unfiltered
(`FilterQuality.None`, 1 bitmap pixel = 1 dp), and pointer mapping floors to
exact pixel coordinates — there is no antialiasing to fuzzy-match.

### Iterative flood fill, mark-on-push

- **No recursion**: a full-canvas region is 800×600 = 480,000 pixels; a
  recursive fill would overflow the stack.
- Use an explicit stack (`ArrayDeque<Int>` of packed `y * width + x`
  indices), DFS order.
- **Mark on push**: a pixel is recoloured when it is *pushed*, not when
  popped. Because `target != colour` (guarded up front), a recoloured pixel
  can never match `target` again, so no pixel is pushed twice and no separate
  visited set is needed.
- Complexity: O(w×h) time, O(region) memory worst case (~2 MB of `Int`s for
  a full-canvas fill — fine). A scanline variant is a possible later
  optimisation, not needed now.

### No-op cases

- Clicked pixel already has the active colour → return immediately (nothing
  to do; also prevents the mark-on-push invariant from degenerating).
- Clicked coordinate out of bounds → return immediately. `CanvasInput` can
  deliver out-of-bounds coordinates, and `CanvasBitmap.get` throws on them,
  so Fill must guard bounds itself (per the `CanvasBitmap` contract).

### Fill is a click tool

Fill happens in `onDown`. `onMove` is a no-op: holding and dragging after the
click must not re-fill under the moving pointer.

## Code changes

### 1. New: `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`

Pure algorithm, in its own file following the `Lines.kt` / `Discs.kt`
helper pattern:

```kotlin
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, colour: Int) {
    if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return
    val target = bitmap[startX, startY]
    if (target == colour) return
    val pending = ArrayDeque<Int>()
    bitmap[startX, startY] = colour
    pending.addLast(startY * bitmap.width + startX)
    while (pending.isNotEmpty()) {
        val index = pending.removeLast()
        val x = index % bitmap.width
        val y = index / bitmap.width
        pushIfTarget(bitmap, pending, x - 1, y, target, colour)
        pushIfTarget(bitmap, pending, x + 1, y, target, colour)
        pushIfTarget(bitmap, pending, x, y - 1, target, colour)
        pushIfTarget(bitmap, pending, x, y + 1, target, colour)
    }
}

private fun pushIfTarget(
    bitmap: CanvasBitmap, pending: ArrayDeque<Int>,
    x: Int, y: Int, target: Int, colour: Int,
) {
    if (x in 0 until bitmap.width && y in 0 until bitmap.height &&
        bitmap[x, y] == target
    ) {
        bitmap[x, y] = colour
        pending.addLast(y * bitmap.width + x)
    }
}
```

### 2. Edit: `src/commonMain/kotlin/kmppaint/tools/Tool.kt`

- Add `object Fill : Tool` alongside `Pencil` and `Brush`:
  - `label = "Fill"`
  - `onDown` → `floodFill(bitmap, x, y, colour)`
  - `onMove` → empty body (documented no-op)
- Append to the registry: `val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)`
- Update the now-stale comments: "Steps 5–6 add Brush and Fill…" on the
  `Tool` interface and "Step 6 appends Fill" on `TOOLS`.

### 3. UI: no changes

`ToolBar` renders one button per `TOOLS` entry, so the Fill button appears
automatically with the active-border idiom. `AppState` already forwards
`onCanvasDown` to the active tool. This fulfils the "without touching UI
code" promise in `Tool.kt`.

## Tests

Common tests with `kotlin.test`, following the exhaustive pixel-assertion
style of `PencilTest` / `AppStateTest`.

### New: `src/commonTest/kotlin/kmppaint/tools/FillTest.kt`

1. **Blank canvas**: fill the centre of a white bitmap → every pixel
   recoloured.
2. **Closed shape (acceptance scenario)**: draw a square outline with
   `Pencil.onMove` segments, fill an interior pixel → every interior pixel
   has the fill colour; boundary and exterior pixels unchanged.
3. **Complement**: fill *outside* the same square → everything except the
   interior recoloured; interior untouched.
4. **4-connectivity pin**: two diagonally corner-touching barrier pixels
   (e.g. barrier at (1,0) and (0,1), fill from (0,0)) → only (0,0)
   recoloured; fill must not leak through the diagonal gap.
5. **Diagonal pencil boundary**: draw a diamond with diagonal `Pencil.onMove`
   strokes, fill inside → contained, proving 4-connectivity holds for real
   Bresenham boundaries.
6. **Target == fill colour is a no-op**: bitmap identical afterwards.
7. **Out-of-bounds clicks** (`(-1, 0)`, `(0, -1)`, `(width, 0)`,
   `(width - 1, height)`) → no throw, no change.
8. **Canvas edge bounds the fill**: a region open to the edge fills up to the
   edge and stops.
9. **`Fill.onMove` is a no-op**: bitmap identical after an `onMove` call.
10. **Registry**: `TOOLS` is `[Pencil, Brush, Fill]`.

### Extend: `src/commonTest/kotlin/kmppaint/AppStateTest.kt`

1. `selectTool(Fill)`; `onCanvasDown` inside a pencilled square fills the
   interior in the active colour and bumps `version` once.
2. Fill then drag (`onCanvasMove` to a different pixel) → no further pixel
   changes.
3. Switching Pencil → Fill → Pencil works (tool round-trip).

Known quirk (accepted): `AppState.onCanvasMove` bumps `version` even when the
tool's `onMove` changed nothing, so dragging with Fill recomposes with an
identical bitmap. Harmless; guarding it is out of scope.

## Verification

1. `./gradlew jvmTest` — all common tests run on the JVM target; must be
   green, including the pre-existing suites.
2. Headless smoke test per `AGENTS.md` (Xvfb + python-xlib + ImageMagick):
   1. Start `Xvfb :99` and the app (`JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE"`),
      install `python-xlib` into `/tmp/pylib`.
   2. Baseline screenshot; measure the canvas origin and the third tool-bar
      button centre (Fill, below Pencil and Brush; buttons are 64×40 dp with
      4 dp spacing/padding) — pointer coordinates map 1:1 to canvas pixels.
   3. With Pencil active, drag a closed rectangle, e.g.
      (300,200)→(500,200)→(500,400)→(300,400)→(300,200) in ~6 px steps.
   4. Click the blue swatch, click the Fill button, sleep ~0.8 s, capture.
   5. Click inside the rectangle (e.g. bitmap (400,300) + canvas origin),
      sleep, capture `shot.png`.
   6. Assert:
      - `convert shot.png -format "%[pixel:p{<inside>}]" info:` → `#0000FF`
      - an exterior pixel → white; a boundary pixel → black
      - clicking a second time inside (now target == colour) leaves the
        screenshot identical: `compare -metric AE` prints `0`.
   7. Cleanup: `pkill -f kmppaint`, then kill `Xvfb` (one process at a time).

## Edge cases

| Case | Behaviour |
|---|---|
| Click pixel already in active colour | No-op (early return) |
| Click out of bounds | No-op (bounds guard before `get`) |
| Diagonal (corner-touching) boundary | Contains the fill (4-connectivity) |
| Click on a 1-px boundary | Recolours that 4-connected component of the boundary |
| Region open to canvas edge | Fills to the edge, bounded by bitmap dimensions |
| Click-and-drag | Single fill on down; drags ignored |
| Full blank canvas (480k px) | Iterative; no stack overflow, ~O(w×h) |

## Out of scope

- Colour tolerance / fuzzy matching (no antialiasing exists to match).
- 8-connected fill.
- Undo/redo, fill preview, adjustable fill connectivity or contiguity
  options.
- Scanline optimisation (revisit only if the stack variant proves slow).
