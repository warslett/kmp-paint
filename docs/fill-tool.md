# Fill tool — implementation plan

Flood fill ("paint bucket"): clicking a pixel recolours the entire connected
region of that pixel's colour with the active palette colour. Pixels of any
other colour bound the region, so a closed shape drawn with the pencil can be
filled by clicking inside it.

## 1. Acceptance criteria

1. Clicking a pixel fills every pixel reachable from the seed through the
   seed's colour, stopping at pixels of any other colour.
2. A closed shape drawn with the pencil contains the fill: clicking inside it
   recolours exactly the interior; boundary and exterior pixels are untouched.
3. The fill uses the colour selected from the palette at the moment of the
   click.
4. "Fill" appears as a third button in the tool selector and behaves like the
   other tools (active-tool highlight, one click = one action).

## 2. How a tool plugs in (existing architecture)

- `tools/Tool.kt` — sealed interface `Tool` with `label`,
  `onDown(bitmap, x, y, colour)` and `onMove(bitmap, fromX, fromY, toX, toY,
  colour)`. Implementations are stateless singletons; `TOOLS` lists them in
  selector order. Both doc comments there already anticipate this work
  ("Step 6 appends Fill").
- `tools/ToolBar.kt` renders one button per `TOOLS` entry — **no UI change is
  needed**; appending to `TOOLS` is sufficient.
- `AppState` forwards `onCanvasDown` / `onCanvasMove` to the active tool and
  bumps `version` after each event, which is what re-renders the canvas. Fill
  rides this path unchanged.
- `canvas/CanvasBitmap.kt` — packed-ARGB `IntArray`. `get` **throws** on
  out-of-bounds reads (its doc comment explicitly names flood fill as the
  caller that "must check bounds themselves"); `set` silently ignores
  out-of-bounds writes.
- Palette colours are exact, opaque ARGB `Int`s, and the pencil/brush write
  exactly those values — so region matching is plain `==` on the packed
  colour. No tolerance/anti-aliasing handling (see §7, non-goals).

## 3. Design decisions

### 3.1 Connectivity: 4-way (N/S/E/W), not 8-way

Pencil strokes are Bresenham lines (`forEachPixelOnLine`): continuous only
under 8-adjacency, so a hand-drawn closed shape routinely has corners where
boundary pixels touch **diagonally only**. An 8-connected fill would leak
through those diagonal joints and spill outside the shape; a 4-connected fill
treats diagonal contact as a sealed edge. 4-way connectivity is therefore
what acceptance criterion 2 requires (and matches MS Paint). The
diagonal-boundary case gets a dedicated test (§6).

### 3.2 Iterative algorithm with an explicit stack — never recursion

The canvas is 800×600 = 480,000 px (`App.kt`). A recursive flood fill nests
one stack frame per pixel along its longest path; a worst-case fill (empty
canvas) overflows the JVM stack. Use an explicit work stack:
`kotlin.collections.ArrayDeque<Int>` holding packed coordinates
(`y * width + x`), available in commonMain since Kotlin 1.4. Depth-first
(`removeLast`) keeps the pending set small on typical fills.

### 3.3 Visited marking by painting, plus the same-colour guard

Paint each pixel **as it is pushed**, not as it is popped. A painted pixel no
longer equals the target colour, so the bitmap itself doubles as the visited
set — no separate `BooleanArray`/set, and each pixel is pushed at most once.

This makes the early return `if (target == colour) return` a correctness
requirement, not an optimisation: if the fill colour equalled the target
colour, painting would not change the pixel, the visited marker would never
register, and the loop would re-push neighbours forever. It also makes
clicking a region that already has the active colour a free no-op.

### 3.4 Fill acts on `onDown`; `onMove` is a no-op

Fill is a single-click action (MS Paint behaviour): `onDown` runs the whole
flood fill, `onMove` does nothing, so dragging after the click paints
nothing. `AppState.onCanvasDown` still bumps `version` once — harmless: a
no-op fill (e.g. same-colour guard) just re-renders an unchanged bitmap,
same as the existing tools' edge-case no-ops.

### 3.5 Seed bounds check

`CanvasInput` should only ever deliver in-bounds presses (the `Image` is
exactly the canvas size), but `CanvasBitmap.get` throws out of bounds and a
defensive `x in 0 until width && y in 0 until height` check on the seed is
one line — take it, per the `CanvasBitmap` contract.

### 3.6 Complexity

O(filled pixels) time (each pixel pushed at most once, 4 neighbour reads per
pop) and O(filled pixels) worst-case heap for the stack — ~2 MB packed ints
for a full-canvas fill. A full-canvas fill is a few ms on the JVM; the
fancier scanline variant is a documented fallback if profiling ever says
otherwise, not the starting point (KISS).

## 4. Reference implementation sketch

New file `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`, mirroring the
style of `Lines.kt`/`Discs.kt` (pure commonKotlin, exhaustive KDoc). Unlike
those it takes the bitmap, because the region is defined by pixel *values*:

```kotlin
package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/**
 * Recolours every pixel 4-connected to (seedX, seedY) that shares its
 * colour … (full KDoc covering 3.1–3.5)
 */
fun floodFill(bitmap: CanvasBitmap, seedX: Int, seedY: Int, colour: Int) {
    if (seedX !in 0 until bitmap.width || seedY !in 0 until bitmap.height) return
    val target = bitmap[seedX, seedY]
    if (target == colour) return

    val pending = ArrayDeque<Int>() // packed y * width + x
    bitmap[seedX, seedY] = colour   // paint-on-push: colour doubles as visited marker
    pending.addLast(seedY * bitmap.width + seedX)

    while (pending.isNotEmpty()) {
        val packed = pending.removeLast()
        val x = packed % bitmap.width
        val y = packed / bitmap.width
        for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
            if (nx in 0 until bitmap.width && ny in 0 until bitmap.height &&
                bitmap[nx, ny] == target
            ) {
                bitmap[nx, ny] = colour
                pending.addLast(ny * bitmap.width + nx)
            }
        }
    }
}
```

(The neighbour list can be a small private helper or inline offsets — detail
left to the implementation; avoid allocating a `listOf` per pop in the final
code, e.g. iterate a preallocated `IntArray` of dx/dy pairs.)

## 5. Implementation steps

1. **`src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`** (new) —
   `floodFill()` per §4.
2. **`src/commonMain/kotlin/kmppaint/tools/Tool.kt`** (edit) —
    - add `object Fill : Tool`, `label = "Fill"`, whose `onDown` delegates to
      `floodFill(bitmap, x, y, colour)` and whose `onMove` is `Unit`;
    - append `Fill` to `TOOLS` (order: Pencil, Brush, Fill);
    - update the two stale "Step 6" doc comments.
3. **`src/commonTest/kotlin/kmppaint/tools/FillTest.kt`** (new) — §6 cases.
4. **`src/commonTest/kotlin/kmppaint/AppStateTest.kt`** (edit) — §6 cases.
5. **`src/commonTest/kotlin/kmppaint/tools/BrushTest.kt`** (edit) — the
   existing `toolsListIsExactlyPencilThenBrushInSelectorOrder` pins
   `listOf(Pencil, Brush)`; update to `listOf(Pencil, Brush, Fill)`.
6. Run `./gradlew :compileKotlinJvm jvmTest` (or `./gradlew allTests`) until
   green, then the headless smoke test (§7).

No changes to `AppState`, `ToolBar`, `CanvasInput`, `App`, or `CanvasBitmap`.

## 6. Test plan

`FillTest.kt` (same style as `BrushTest`: small bitmaps, exhaustive per-pixel
assertion via an `assertBitmapMatches` helper):

1. **Seed on empty canvas fills everything** — white 5×5, fill red at (2, 2):
   all 25 px red.
2. **Closed axis-aligned rectangle contains the fill** — draw a rectangle
   with `Pencil.onMove` on 4 edges, fill inside: interior red, boundary and
   exterior untouched. *(Acceptance criterion 2.)*
3. **Diagonal-only boundary contains the fill** — draw a diamond using
   `Pencil.onMove` along its 4 diagonal edges (boundary pixels touch only
   diagonally at the corners), fill the centre: nothing outside the diamond
   changes. Pins the 4-connectivity decision (§3.1).
4. **Same-colour click is a no-op and returns** — fill white canvas with
   white: bitmap unchanged, call completes (would hang/OOM without the §3.3
   guard).
5. **Out-of-bounds seed is a no-op, does not throw** — `Fill.onDown(bitmap,
   -1, 0, red)`.
6. **Other colours inside the region are edges** — a blue pixel inside the
   rectangle survives; fill wraps around it but never recolours it.
7. **Canvas edge bounds the region exactly** — region touching the border
   fills up to and including border pixels; no wraparound to the opposite
   edge.
8. **`onMove` is a no-op** — bitmap and state identical before/after.
9. **Non-rectangular region** — fill one half of a bitmap split by a vertical
   pencil line: exactly one side changes.

`AppStateTest.kt` additions:

10. `selectTool(Fill)` then `onCanvasDown` fills with `activeColour` and
    bumps `version` by exactly 1.
11. After a fill `onCanvasDown`, `onCanvasMove` changes nothing and does not
    bump `version` again (beyond the stroke-tracking no-ops).
12. Colour selected *between* two fills applies only to the second (mirrors
    `changingTheActiveColourBetweenStrokesRecoloursOnlyNewStrokes`).

## 7. Headless smoke test (per AGENTS.md)

Automated, on Xvfb `:99` with the app under
`JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE"`:

1. Baseline screenshot; measure canvas origin once (root coord = canvas
   origin + bitmap coord).
2. With the pencil (default tool) and default black, inject a click-drag
   rectangle: four drags along e.g. (100, 100)→(300, 100)→(300, 300)→
   (100, 300)→(100, 100), ~6 px motion steps.
3. Click the blue swatch, then the Fill button (centres measured from the
   baseline shot), then click (200, 200) inside the rectangle.
4. Verify with ImageMagick: `pixel:p{200,200}` and `pixel:p{150,150}` are
   `#0000FF`; `pixel:p{100,100}` (boundary) is `#000000`;
   `pixel:p{50,50}` (outside) is `#FFFFFF`; `compare -metric AE` against a
   shot taken after step 2 confirms only interior pixels changed.
5. Negative control: fill a region already holding the active colour —
   screenshot before/after must differ by `0` pixels.

## 8. Non-goals

- **Colour tolerance / anti-aliased edges** — all colours are exact opaque
  palette values; `==` matching is correct by construction.
- **8-connectivity option, pattern/gradient fills, undo** — out of scope.
- **Scanline flood fill** — only if profiling shows the plain DFS wanting
  (§3.6).
