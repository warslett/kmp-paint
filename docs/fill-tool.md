# Plan: the Fill (paint bucket) tool

Step 6 of the tool set. Adds `Fill` to `TOOLS`, implemented as a flood fill
over `CanvasBitmap`. No UI code changes: `ToolBar` already renders one button
per entry in `TOOLS`, and `AppState` already routes pointer events to
`activeTool`.

## 1. Behaviour

Clicking the canvas with Fill selected:

1. Reads the colour of the clicked pixel — the **target colour**.
2. Replaces it, and every pixel reachable from the click through an unbroken
   run of that same target colour, with the active palette colour.
3. Pixels of any other colour are boundaries: the fill stops at them and never
   crosses them.

So a closed shape drawn with the pencil is filled inside-only when clicked
inside, and outside-only when clicked outside.

No-ops (bitmap untouched, ideally not even a version bump — see §5):

- Click outside the canvas bounds.
- Active colour already equals the target colour (otherwise the algorithm
  would never terminate, since filled pixels would still match the target).

Drag: Fill is a click tool. `onMove` does nothing — dragging after a fill must
not repeatedly re-fill along the drag path. (`AppState.onCanvasMove` will still
call it and bump `version`; that is harmless, but see §5 for the tidier option.)

## 2. Connectivity: 4-connected, deliberately

Use **4-connectivity** (up/down/left/right) for the fill spread.

This is not arbitrary. `forEachPixelOnLine` (`src/commonMain/kotlin/kmppaint/tools/Lines.kt:22`)
produces 8-adjacent Bresenham lines, so a diagonal pencil stroke is a chain of
diagonally-touching pixels with 1-pixel diagonal gaps between them. An
8-connected fill would leak diagonally through those gaps and flood the whole
canvas; a 4-connected fill treats the diagonal chain as a solid wall. The pair
"8-connected drawing / 4-connected filling" is the standard combination and is
what makes "draw a closed shape, click inside" work for shapes drawn at any
angle. This is also what MS Paint does.

## 3. Algorithm

New file `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`, in the same style
as `Lines.kt` / `Discs.kt`: a pure, bitmap-level function that is unit-testable
without any tool or Compose involvement.

```kotlin
fun floodFill(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int)
```

Contract:

- Returns immediately if `(x, y)` is out of bounds. `CanvasBitmap.get` *throws*
  on out-of-bounds reads (`CanvasBitmap.kt:25`) while `set` silently ignores
  out-of-bounds writes (`:30`), so this function must do its own bounds
  checking on every read — unlike the pencil and brush, which rely on the
  lenient `set`.
- Returns immediately if `bitmap[x, y] == colour`.
- Otherwise replaces the 4-connected region of `bitmap[x, y]`'s colour with
  `colour`.

Implementation: **iterative scanline flood fill** with an explicit stack.

Recursion is not acceptable here: on an 800×600 canvas a naive recursive fill
can nest 480,000 deep and blow the JVM stack. A per-pixel explicit stack is
safe but can hold ~480k boxed-free ints; the scanline variant pushes only one
entry per horizontal span, which is dramatically fewer and is the standard
production choice.

Sketch:

```
stack = IntArray-backed stack of seed (x, y) pairs   // or an ArrayDeque<Int> of packed y*width+x
target = bitmap[x, y]
if (target == colour) return
push(x, y)
while (stack not empty):
    (sx, sy) = pop()
    if (bitmap[sx, sy] != target) continue          // already filled by an earlier span
    // walk left and right to the extent of the run of `target` on row sy
    left = sx;  while (left - 1 >= 0        && bitmap[left - 1, sy] == target) left--
    right = sx; while (right + 1 < width    && bitmap[right + 1, sy] == target) right++
    for (px in left..right) bitmap[px, sy] = colour
    // seed the rows above and below: one seed per contiguous run of `target`
    for (row in listOf(sy - 1, sy + 1)) where row in 0 until height:
        px = left
        while (px <= right):
            if (bitmap[px, row] == target):
                // scan to the end of this run, seed once at its start
                push(px, row)
                while (px <= right && bitmap[px, row] == target) px++
            else px++
```

Notes:

- Seeding once per contiguous run (rather than per pixel) is what keeps the
  stack small; the `bitmap[sx, sy] != target` re-check at pop time handles
  seeds whose run was already consumed by another span.
- Store seeds packed as `y * bitmap.width + x` in a growable `IntArray` (or
  `ArrayDeque<Int>`) to avoid allocating a pair object per seed.
- Complexity: O(width × height) time, O(number of spans) space. Worst case for
  an entirely-uniform 800×600 canvas is 600 spans — trivial.
- The whole fill happens inside one `onDown`, so the canvas repaints once. No
  incremental/animated fill.

Consider also exposing the primitive in the `forEach…` idiom of its siblings
(`forEachPixelInRegion(bitmap, x, y, plot)`) for symmetry, but the mutating
`floodFill` is simpler and the visitor form has no second caller. Prefer the
direct `floodFill`; keep it in its own file so it stays independently testable.

## 4. The tool

In `src/commonMain/kotlin/kmppaint/tools/Tool.kt`:

```kotlin
/** Fill: replaces the 4-connected region of like-coloured pixels under the
 *  click with the active colour (FR-4). Click-only: dragging does nothing. */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        floodFill(bitmap, x, y, colour)
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
        // Fill is a click tool: a drag must not re-fill along the path.
    }
}

val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
```

Also update the stale comments at `Tool.kt:7-8` ("Steps 5–6 add Brush and
Fill") and `Tool.kt:57` ("Step 6 appends Fill") now that both exist.

## 5. Optional `AppState` tidy-up

`AppState.onCanvasDown` unconditionally calls `onBitmapChanged()`, so a
no-op fill (out of bounds, or same colour) still bumps `version` and forces a
pointless `toImageBitmap()` conversion of the full 800×600 bitmap. The
rendered result is identical, so this is a performance/wart issue, not a
correctness one.

Two options:

- **Do nothing** (recommended for this step). Keeps `Tool` returning `Unit`
  and the existing `AppStateTest` "one version bump per drawing event"
  assertions unchanged.
- Change `Tool.onDown`/`onMove` to return `Boolean` ("did I change pixels?")
  and have `AppState` bump `version` only on `true`. Cleaner, but it touches
  every tool and several existing tests. Defer this until there is a reason —
  e.g. undo/redo, which will need a real stroke-boundary hook anyway.

## 6. Unit tests

New `src/commonTest/kotlin/kmppaint/tools/FloodFillTest.kt`, matching the
existing style (small bitmaps, exhaustive whole-bitmap assertions with a `want`
computed inline and a `"pixel ($x, $y)"` message):

1. `fillsAUniformBitmapEntirely` — fresh white 5×5, fill at (2,2) with RED →
   all 25 pixels RED.
2. `fillsOnlyInsideAClosedRectangle` — draw a black rectangle outline on a
   9×9, click inside → interior RED, outline still black, exterior still white.
3. `fillsOnlyOutsideAClosedRectangle` — same bitmap, click at (0,0) → exterior
   RED, outline black, interior white. This is the leak-detector.
4. `doesNotLeakThroughADiagonalBoundary` — the key connectivity test. Draw a
   diagonal line with `forEachPixelOnLine` from corner to corner, fill one
   side, assert the other side is untouched.
5. `leaksThroughAOnePixelGapInTheBoundary` — a rectangle outline with one
   boundary pixel left white: filling inside must reach the outside. Documents
   flood fill's defining behaviour rather than treating it as a bug.
6. `fillingWithTheColourAlreadyThereIsANoOp` — must terminate and change
   nothing (guards the infinite-loop trap).
7. `outOfBoundsClickIsANoOp` — negative and `>= width/height` coordinates,
   asserting no exception is thrown (guards against the throwing `get`).
8. `fillsARegionThatRequiresUpwardAndDownwardSpanSeeding` — a U-shaped or
   spiral region whose parts are only reachable by going down then back up;
   catches a scanline implementation that only seeds one direction.
9. `fillsALargeCanvasWithoutStackOverflow` — full 800×600 fill; a smoke test
   for the iterative implementation.

Extend `src/commonTest/kotlin/kmppaint/tools/` and
`src/commonTest/kotlin/kmppaint/AppStateTest.kt` with:

10. `fillToolIsInTools` / tool-count assertion, if `ToolBar`-adjacent tests
    assert `TOOLS.size`.
11. `fillToolIgnoresDrag` — via `AppState`: select Fill, `onCanvasDown` inside
    a shape, then `onCanvasMove` across a boundary into another region, and
    assert the second region is untouched.
12. `fillToolUsesTheActiveColour` — select a palette colour, then fill.

## 7. Headless smoke test

Add `docs/smoke-tests/fill.md` following the procedure in `AGENTS.md` (Xvfb
`:99`, `-Dskiko.renderApi=SOFTWARE`, python-xlib XTEST injection, `import` /
`compare` / `convert` for verification). Steps:

1. Launch, capture a baseline screenshot, measure the canvas origin.
2. With the pencil, drag a closed rectangle on the canvas (four drags, each
   interpolated in ~6 px steps, ~0.8 s settle after each).
3. Click the BLUE swatch, then the Fill tool button (it is the third button in
   the tool bar column).
4. Click a point well inside the rectangle. Capture.
5. Verify: `convert shot.png -format "%[pixel:p{x,y}]" info:` returns
   `#0000FF` for several interior points, `#000000` for a point on the
   outline, and `#FFFFFF` for a point outside the rectangle. The palette is
   opaque so these are exact matches.
6. Click outside the rectangle with Fill. Verify the exterior becomes blue and
   the interior stays blue while the outline stays black.
7. No-op check: fill the same region again with the same colour and assert
   `compare -metric AE before.png after.png null:` prints `0`.
8. Cleanup: `pkill -f kmppaint`, then kill Xvfb (separate commands).

## 8. Order of work

1. `FloodFill.kt` + `FloodFillTest.kt` (red → green, no UI involved).
2. `Fill` object, append to `TOOLS`, refresh the stale step comments.
3. `AppState`-level tests (drag no-op, active colour).
4. `./gradlew build` — full test run.
5. Headless smoke test; write up `docs/smoke-tests/fill.md`.

## 9. Out of scope

Tolerance/fuzzy matching (the canvas has no anti-aliasing, so exact equality
is sufficient), fill-with-pattern, global "replace colour everywhere",
undo/redo, and any progressive rendering of the fill.
