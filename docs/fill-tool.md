# Fill tool implementation plan

## Goal

Add a "Fill" (paint bucket) tool: the user clicks a pixel on the canvas, and
every pixel reachable from that pixel by travelling only through pixels of
the *same* colour as the clicked pixel gets repainted to the active palette
colour. Pixels of any other colour act as a boundary and stop the fill. This
lets a shape drawn as a closed outline with the Pencil tool be filled from
the inside without leaking outside the outline.

This plan follows the existing codebase's conventions exactly (see
`src/commonMain/kotlin/kmppaint/tools/Tool.kt`, `Discs.kt`, `Lines.kt`), so
that `Fill` slots in the same way `Brush` did, per the comment already in
`Tool.kt:7-8`: *"Steps 5–6 add Brush and Fill without touching UI code."*

## Non-goals

- Tolerance/anti-aliasing-aware fill (e.g. "fill similar colours"). The
  canvas only ever contains flat, opaque palette colours (`CanvasBitmap`
  writes are always exact ARGB `Int`s from `PALETTE`), so exact-match
  flood fill is sufficient — there are no soft edges to tolerate.
- Undo/redo. No tool in this codebase currently has undo support (confirmed:
  no `undo`/`redo`/`history` code anywhere in `src/`), so Fill won't add any
  either. If undo is wanted later, it's a cross-cutting change to `AppState`,
  not specific to Fill.
- Fill-while-dragging. Bucket fill is conventionally a single-click action;
  see "Behaviour on drag" below for why `onMove` is a no-op.
- Diagonal (8-connected) fill. Flood fill conventionally uses 4-connected
  (N/E/S/W) adjacency; see "Connectivity" below.

## 1. New file: `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`

Mirrors `Lines.kt` (`forEachPixelOnLine`) and `Discs.kt`
(`forEachPixelInDisc`): a pure, stateless helper that computes *which*
pixels are affected, and hands each one to a `plot` callback — it does not
write to the bitmap itself. This keeps `Tool.kt` a thin dispatcher and keeps
the algorithm independently unit-testable, exactly like the other two
helpers.

```kotlin
package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Invokes [plot] for every pixel in the 4-connected region of pixels equal
 * in colour to the pixel at (startX, startY), starting from that pixel
 * itself (a flood fill / "paint bucket" region). If (startX, startY) is out
 * of bounds, does nothing.
 *
 * Uses a scanline (span-based) flood fill: for the current pixel, extends
 * left and right along the row while the colour matches, marking the whole
 * span as visited/plotted in one pass, then queues one seed above and below
 * the span for each maximal run of matching pixels. This is equivalent to
 * (and much faster than) a naive 4-connected BFS/DFS that queues every
 * individual pixel, while visiting each matching pixel exactly once.
 */
fun forEachPixelInFloodFillRegion(
    bitmap: CanvasBitmap,
    startX: Int,
    startY: Int,
    plot: (x: Int, y: Int) -> Unit,
) {
    fun inBounds(x: Int, y: Int) = x in 0 until bitmap.width && y in 0 until bitmap.height
    if (!inBounds(startX, startY)) return

    val targetColour = bitmap[startX, startY]
    val visited = BooleanArray(bitmap.width * bitmap.height)
    fun isVisited(x: Int, y: Int) = visited[y * bitmap.width + x]
    fun markVisited(x: Int, y: Int) { visited[y * bitmap.width + x] = true }
    fun matches(x: Int, y: Int) = inBounds(x, y) && !isVisited(x, y) && bitmap[x, y] == targetColour

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(startX to startY)
    markVisited(startX, startY)

    while (stack.isNotEmpty()) {
        val (x, y) = stack.removeLast()

        var left = x
        while (matches(left - 1, y)) { left--; markVisited(left, y) }
        var right = x
        while (matches(right + 1, y)) { right++; markVisited(right, y) }

        for (px in left..right) {
            plot(px, y)
            for (ny in intArrayOf(y - 1, y + 1)) {
                if (matches(px, ny)) {
                    markVisited(px, ny)
                    stack.addLast(px to ny)
                }
            }
        }
    }
}
```

Notes on this design, to record the reasoning for reviewers/tests:

- **Bounds checking is manual**, per `CanvasBitmap`'s documented contract
  (`CanvasBitmap.kt:10-14`: *"Reads from out-of-bounds coordinates throw
  IllegalArgumentException; callers (e.g. flood fill) must check bounds
  themselves."* — this is a direct, pre-existing hint for this exact
  helper). `inBounds`/`matches` never call `bitmap[x, y]` without checking
  first.
- **Connectivity**: 4-connected (only N/E/S/W neighbours), not 8-connected.
  This matches the universal convention for bucket-fill tools (MS Paint
  included) and is what makes "draw a closed outline, click inside it, and
  the fill stays inside" work correctly for shapes with only
  straight/orthogonal or gently-diagonal Pencil-drawn edges. It also avoids
  a known flood-fill pitfall: with 8-connectivity, fill can leak through a
  diagonal "staircase" one-pixel gap in an outline; 4-connectivity is the
  safer, more conservative default and is what a 1px-wide Pencil outline is
  drawn assuming (a diagonal pencil line is a staircase of individually
  4-adjacent pixels, so a 4-connected boundary check correctly treats it as
  solid).
- **No-op if the clicked pixel is already outside the canvas**: guards
  `startX`/`startY` before reading `bitmap[startX, startY]`, since a fill
  can currently only be triggered from a canvas click, which `CanvasInput.kt`
  already clips to canvas coordinates — but the guard keeps the helper safe
  to call directly (e.g. from tests) with arbitrary coordinates, matching
  how `forEachPixelInDisc` doesn't crash near edges.
- **Filling a single already-target-colour pixel with itself** (clicking a
  region that's already the active colour) is *not specially skipped* here;
  it still visits and re-plots the whole matching region. This mirrors the
  existing codebase's stance that redundant same-colour writes are harmless
  and not worth special-casing (see `AppState.onCanvasMove`, which only
  special-cases the "hasn't moved a whole pixel" case, not "colour
  unchanged"). The visible bitmap is unchanged either way, and `version`
  still bumps — consistent with e.g. clicking the Pencil tool twice on the
  same white pixel with white already active.
- Uses an explicit `ArrayDeque` as a stack (DFS order) rather than recursion,
  to avoid stack-overflow risk on large fill regions (a naive recursive
  flood fill can blow the JVM call stack on canvases with big same-colour
  areas — worth avoiding given canvases here can be arbitrarily sized).

## 2. `Tool.kt` changes

Append a `Fill` tool object, following the exact shape of `Pencil`/`Brush`,
and add it to `TOOLS`:

```kotlin
/**
 * Fill: flood-fills the 4-connected region of same-coloured pixels starting
 * at the clicked pixel with the active colour (FR-? / bucket fill). Unlike
 * Pencil/Brush, dragging does not repeatedly re-fill — see onMove.
 */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        forEachPixelInFloodFillRegion(bitmap, x, y) { px, py -> bitmap[px, py] = colour }
    }

    /**
     * No-op: a flood fill is a single discrete action triggered by the
     * initial click, not a continuous stroke. Re-running the flood fill on
     * every dragged-over pixel would be wasteful (re-scanning the whole
     * region on every pixel of movement) and semantically wrong (a bucket
     * tool "dispenses" once per click in every paint program, it does not
     * paint a smear like Pencil/Brush).
     */
    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
    }
}
```

```kotlin
/** Every tool, in selector display order. */
val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
```

No other production code changes are required:

- `ToolBar.kt` iterates `TOOLS` generically and needs no changes to show a
  "Fill" button.
- `AppState.onCanvasDown`/`onCanvasMove` already dispatch generically to
  `activeTool.onDown`/`onMove` with the correct `colour` (the current
  `activeColour`) — Fill receives the palette colour the same way every
  other tool does, no special wiring needed.
- `CanvasInput.kt` already converts pointer position to bitmap pixel
  coordinates and calls `onCanvasDown`/`onCanvasMove`/`onCanvasUp`
  generically for any tool.

## 3. Tests

### 3.1 `src/commonTest/kotlin/kmppaint/tools/FloodFillTest.kt` (new)

Unit-test `forEachPixelInFloodFillRegion` in isolation against `CanvasBitmap`
fixtures, following the style of `DiscsTest.kt`/`LinesTest.kt` (exact
plotted-pixel-set assertions via full-canvas iteration, not spot checks).
Cases to cover:

- **Whole-canvas single colour**: a freshly-constructed all-white bitmap,
  fill from any interior point plots every pixel exactly once.
- **Closed rectangle outline** (the core "Pencil-drawn shape" scenario):
  draw a 1px-thick rectangular border of colour A on a colour-B background,
  fill from a point strictly inside the border. Assert the plotted set is
  exactly the interior pixels (not the border, not anything outside).
- **Fill stays confined by outline touching canvas edges**: e.g. an outline
  that touches the canvas boundary on one side, confirming out-of-bounds
  checks don't leak the fill around the "open" edge.
- **Non-rectangular / irregular region**: an L-shaped or diagonal-staircase
  boundary, to exercise the 4-connectivity boundary-following logic and
  confirm a staircase-adjacent diagonal outline still fully contains the
  fill (no leak through the diagonal gap).
- **Disjoint regions of the same colour are not both filled**: two
  same-coloured blobs separated by a different colour; fill from inside one
  blob must not affect the other.
- **Clicking a single isolated pixel**: region of size 1 (target pixel
  surrounded on all 4 sides by a different colour) plots exactly that one
  pixel.
- **Clicking out-of-bounds coordinates**: `plot` is never invoked.
- **No stack overflow / correctness on a large uniform region**: fill an
  entire large bitmap (e.g. 200×200) from a corner, assert it completes and
  plots width×height pixels — a basic regression guard against recursion-
  depth issues if the algorithm is ever changed to naive recursion.
- **Each pixel plotted exactly once**: track a call count per pixel via the
  `plot` callback and assert no pixel is plotted twice, since the scanline
  algorithm's correctness depends on the `visited` bookkeeping being right.

### 3.2 `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` (new)

Test the `Fill` tool object itself (thin wrapper), mirroring
`PencilTest.kt`/`BrushTest.kt`:

- `onDown` at a point inside a closed same-coloured region repaints exactly
  that region to the given colour and leaves everything else untouched.
- `onMove` is a no-op: calling it with any arguments leaves the bitmap
  byte-for-byte unchanged (`copyPixels()` before/after are equal).

### 3.3 `src/commonTest/kotlin/kmppaint/AppStateTest.kt` (extend)

Add integration cases mirroring the existing Brush sections (e.g.
`fillOnCanvasDownFillsTheEnclosedRegionInTheActiveColourAndBumpsVersion`,
`selectToolSwitchesToFill`, `fillDoesNotChangeActiveColourOrActiveTool`,
`draggingWithFillActiveDoesNotRepeatTheFillPerPixelOfMovement`) to verify:

- Selecting `Fill` via `selectTool(Fill)` makes it the `activeTool`.
- `onCanvasDown` with `Fill` active flood-fills using `activeColour` and
  bumps `version`.
- `onCanvasMove` while dragging with `Fill` active does not perform
  additional fills or bump `version` beyond the initial down (consistent
  with `onMove` being a no-op) — actually note: `onCanvasMove` in
  `AppState` unconditionally calls `onBitmapChanged()` after `activeTool.onMove`
  even if that call did nothing (see `AppState.kt:80-83`), so `version` will
  still increment even though pixels don't change. This is existing,
  pre-existing `AppState` behaviour (not new to Fill) and should be asserted
  as-is rather than "fixed", to avoid scope creep — but call it out
  explicitly in the test name/comment so it's not mistaken for a bug in the
  new tool.

## 4. Manual / smoke-test verification (headless VM, per `AGENTS.md`)

After implementation, verify end-to-end using the existing Xvfb + XTEST +
ImageMagick workflow described in `AGENTS.md`:

1. Draw a closed shape (e.g. a rectangle) with the Pencil tool by dragging
   along its four edges so the outline is fully connected.
2. Select the Fill tool from the tool bar and a palette colour different
   from both the outline and the background.
3. Click once on a pixel inside the shape.
4. Capture a screenshot (`import -window root shot.png`) and verify with
   `convert shot.png -format "%[pixel:p{x,y}]" info:` that:
   - A pixel well inside the shape now matches the selected palette colour
     exactly.
   - A pixel outside the shape (but inside the canvas) is unchanged
     (still background colour).
   - A pixel on the outline itself is unchanged (still outline colour, not
     overwritten by fill).
5. As a negative/no-op check, click Fill on a pixel outside any shape, on
   the plain background: the whole background should turn the fill colour
   (since it's one connected region) — capture and check a corner pixel
   confirms full-canvas fill in that case.

## 5. Summary of file changes

| File | Change |
|---|---|
| `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt` | **New.** `forEachPixelInFloodFillRegion` scanline flood-fill helper. |
| `src/commonMain/kotlin/kmppaint/tools/Tool.kt` | Add `object Fill : Tool`; append `Fill` to `TOOLS`. |
| `src/commonTest/kotlin/kmppaint/tools/FloodFillTest.kt` | **New.** Unit tests for the flood-fill helper. |
| `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` | **New.** Unit tests for the `Fill` tool wrapper. |
| `src/commonTest/kotlin/kmppaint/AppStateTest.kt` | Extend with Fill integration cases. |
| `src/commonMain/kotlin/kmppaint/tools/ToolBar.kt` | **No change** (generic over `TOOLS`). |
| `src/commonMain/kotlin/kmppaint/AppState.kt` | **No change** (generic over `Tool`). |
| `src/commonMain/kotlin/kmppaint/CanvasInput.kt` | **No change** (generic pointer dispatch). |
