# Fill tool — implementation plan

Adds a third tool, **Fill** (the paint bucket), alongside Pencil and Brush. A
click reads the colour under the pointer and recolours the maximal
4-connected run of that colour containing the clicked pixel, stopping at any
pixel of a different colour. The existing architecture anticipates this: the
tool set is data-driven off `TOOLS`, and `CanvasBitmap`'s KDoc already names
flood fill as the caller that must bounds-check its own reads.

---

## 1. Acceptance criteria

1. Selecting **Fill** in the tool bar and clicking a pixel recolours every
   pixel reachable from it by N/S/E/W steps through pixels of the *same*
   original colour, in the active palette colour.
2. Pixels of any other colour are boundaries: they are never recoloured and
   the fill never crosses them.
3. A shape drawn with the Pencil and closed by the user — including one drawn
   with purely diagonal strokes — contains the fill: clicking anywhere inside
   it recolours the interior only, leaving the outline and everything outside
   it untouched.
4. Clicking a pixel that is already the active colour is a no-op (and, in
   particular, terminates).
5. Filling the whole 800×600 canvas completes promptly and without a
   `StackOverflowError`.
6. Fill is a click action: dragging after the press does not fill again.

Non-criteria are listed in §8.

---

## 2. How a tool plugs in (existing architecture)

Nothing outside `tools/` needs to change. The relevant seams:

- `tools/Tool.kt:10-19` — `sealed interface Tool` with `label`, `onDown`,
  `onMove`. Tools are stateless `object`s that mutate the bitmap directly.
- `tools/Tool.kt:58` — `val TOOLS: List<Tool> = listOf(Pencil, Brush)`; the
  comment on `:57` already says "Step 6 appends Fill".
- `tools/ToolBar.kt:39` — the selector iterates `TOOLS`, so appending to that
  list is the *only* change needed to get a button.
- `AppState.kt:68-74` — `onCanvasDown` calls `activeTool.onDown(bitmap, x, y,
  activeColour)` and then `onBitmapChanged()`, which bumps `version`.
- `App.kt:29` — `remember(state.version) { state.bitmap.toImageBitmap() }`
  re-converts and re-uploads the whole buffer on every bump, so one fill costs
  exactly one conversion regardless of how many pixels it changed.
- `CanvasBitmap.kt:25-34` — `get` **throws** `IllegalArgumentException` out of
  bounds; `set` silently ignores out-of-bounds writes. The fill must therefore
  bounds-check every read itself, seed included.
- `palette/Palette.kt` — colours are packed ARGB `Int`s and all opaque, so
  matching is exact `Int ==`. No tolerance, no alpha blending.

---

## 3. Design decisions

### 3.1 Connectivity: 4-way, not 8-way

The fill spreads only N/S/E/W. This is the single decision that makes
acceptance criterion 3 hold.

`forEachPixelOnLine` (`tools/Lines.kt:22-34`) is Bresenham, so a diagonal
pencil stroke lays down pixels that are only *diagonally* adjacent — a 45°
line has a one-pixel diagonal "gap" between every pair of pixels:

```
. . X          an 8-connected fill escapes through the
. X .          diagonal gaps between the outline pixels;
X . .          a 4-connected fill cannot.
```

An 8-connected fill would leak out of any diagonally-drawn outline, which is
exactly the shape a user draws when they scribble a triangle or a circle.
4-connectivity treats a diagonal chain as a wall. It is also what MS Paint
does, so it matches user expectation.

The dual is accepted and intentional: a *diagonally-connected region of
background* is not filled in one click, because the fill cannot travel
diagonally either. That is the correct trade — sealing outlines matters more
than reaching diagonal nooks.

### 3.2 Iterative with an explicit stack — never recursion

The worst case is the whole canvas, 480 000 pixels. The textbook recursive
flood fill would recurse to a depth proportional to the region size and blow
the JVM stack long before that. The implementation uses an explicit stack of
packed indices (`y * width + x`) held in an `IntArray` that doubles on
demand.

An `IntArray` rather than `ArrayDeque<Int>`: `ArrayDeque<Int>` boxes every
element, and peak stack depth is O(region size) in adversarial shapes (a
spiral), so a full-canvas fill could churn hundreds of thousands of `Integer`
allocations. A primitive array avoids that entirely for ~15 lines of code.

Stack (LIFO) rather than queue: order is irrelevant to the result, and a
stack has better locality and a simpler growth policy.

### 3.3 Painting *is* the visited mark

Each pixel is recoloured at the moment it is pushed, not when it is popped.
Because the fill colour differs from the target colour (guaranteed by the
guard in §3.4), an already-painted pixel no longer matches the target and
cannot be pushed a second time. So:

- no separate `BooleanArray` visited set is needed;
- every pixel is pushed at most once, bounding both the work and the stack
  depth by the region size.

Painting on *pop* instead would let the same pixel be pushed up to four times
and inflate the stack fourfold — avoid it.

### 3.4 Two guards, both required

```kotlin
if (seed is out of bounds) return          // CanvasBitmap.get would throw
val target = bitmap[seedX, seedY]
if (target == colour) return               // otherwise: infinite loop
```

The second guard is not merely an optimisation. With paint-as-visited, if the
target colour equals the fill colour then painting a pixel does not change
it, nothing ever stops matching, and the fill loops forever re-pushing the
same pixels. It must be checked before the loop.

The seed guard matters because `onCanvasMove` can deliver out-of-range
coordinates when a drag leaves the canvas (`CanvasInput.kt:31`), and
`floor()` on the far edge of the `Image` can yield `width` / `height`. Today
that is harmless only because `set` ignores such writes; a fill *reads* and
would throw.

### 3.5 Fill acts on `onDown`; `onMove` is a no-op

`Fill.onMove` does nothing, so dragging with Fill selected fills once, at the
press point — the MS Paint behaviour. `AppState.onCanvasMove` will still call
it and still bump `version` on every drag event, causing a redundant
`toImageBitmap()` of an unchanged buffer. That cost is identical to what a
Pencil drag already pays per event, so it is accepted rather than fixed here;
see §8 for the optional follow-up.

### 3.6 A bitmap-aware helper, not a geometry generator

`Lines.kt` and `Discs.kt` are pure generators that take a `plot` callback and
know nothing about the bitmap. Flood fill cannot follow that pattern: the set
of pixels it visits depends on pixels it has already painted, so it must read
and write the bitmap as it goes. `floodFill` therefore takes the
`CanvasBitmap` directly. Worth a line of KDoc, since it breaks the sibling
files' convention.

Name it `floodFill`, not `fill` — `CanvasBitmap.fill(argb)`
(`CanvasBitmap.kt:37-39`) already exists and means "recolour the entire
buffer".

### 3.7 Complexity

Time O(n) in the pixels of the filled region (each pushed once, four
neighbour reads each); memory O(n) worst case for the index stack — bounded
by 480 000 `Int`s ≈ 1.9 MB on this canvas, and far less for typical shapes.
Both are fine at this canvas size. Scanline
fill would cut the stack to O(rows spanned) but is materially more code and
more test surface — deferred (§8).

---

## 4. Reference implementation sketch

`src/commonMain/kotlin/kmppaint/tools/FloodFill.kt` (new):

```kotlin
package kmppaint.tools

import kmppaint.canvas.CanvasBitmap

/** N/S/E/W neighbour offsets — see docs/fill-tool.md §3.1 for why not 8-way. */
private val NEIGHBOUR_DX = intArrayOf(0, 0, -1, 1)
private val NEIGHBOUR_DY = intArrayOf(-1, 1, 0, 0)

private const val INITIAL_STACK_CAPACITY = 1024

/**
 * Recolours the maximal 4-connected region of pixels sharing the colour at
 * (seedX, seedY) with [colour]. Pixels of any other colour bound the region.
 *
 * Unlike [forEachPixelOnLine] and [forEachPixelInDisc] this takes the bitmap
 * rather than a plot callback: the region depends on pixels already painted,
 * so reading and writing cannot be separated.
 *
 * No-op if the seed is out of bounds, or if it already holds [colour].
 */
fun floodFill(bitmap: CanvasBitmap, seedX: Int, seedY: Int, colour: Int) {
    val width = bitmap.width
    val height = bitmap.height
    if (seedX !in 0 until width || seedY !in 0 until height) return

    val target = bitmap[seedX, seedY]
    // Required for termination, not just speed: painting is the visited mark,
    // so a same-colour fill would never stop matching.
    if (target == colour) return

    var stack = IntArray(INITIAL_STACK_CAPACITY)
    var top = 0

    bitmap[seedX, seedY] = colour
    stack[top++] = seedY * width + seedX

    while (top > 0) {
        val packed = stack[--top]
        val x = packed % width
        val y = packed / width
        for (i in NEIGHBOUR_DX.indices) {
            val nx = x + NEIGHBOUR_DX[i]
            val ny = y + NEIGHBOUR_DY[i]
            if (nx !in 0 until width || ny !in 0 until height) continue
            if (bitmap[nx, ny] != target) continue
            bitmap[nx, ny] = colour           // paint on push: doubles as "visited"
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = ny * width + nx
        }
    }
}
```

`src/commonMain/kotlin/kmppaint/tools/Tool.kt` (append after `Brush`, line 55):

```kotlin
/**
 * Fill: recolours the contiguous same-coloured region under the pointer
 * (FR-4). 4-connected, so a diagonal pencil outline seals — see
 * docs/fill-tool.md §3.1. A click action: [onMove] is deliberately empty, so
 * dragging fills once, at the press point.
 */
object Fill : Tool {
    override val label = "Fill"

    override fun onDown(bitmap: CanvasBitmap, x: Int, y: Int, colour: Int) {
        floodFill(bitmap, x, y, colour)
    }

    override fun onMove(bitmap: CanvasBitmap, fromX: Int, fromY: Int, toX: Int, toY: Int, colour: Int) {
        // Intentionally empty.
    }
}
```

---

## 5. Implementation steps

1. **Add `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt`** with
   `floodFill` as sketched in §4, KDoc'd in the house style (explain *why*,
   cross-reference this doc).
2. **Add `object Fill : Tool`** to `tools/Tool.kt` after `Brush` (`:55`).
3. **Append `Fill` to `TOOLS`** (`tools/Tool.kt:58`) and delete the now-stale
   comments: "Step 6 appends Fill" on `:57` and "Steps 5–6 add Brush and Fill
   without touching UI code" in the interface KDoc (`:6-8`), which should
   become a statement that the tool set is complete and UI-independent.
4. **Fix the pinning test** at `tools/BrushTest.kt:138-141`:
   `assertEquals(listOf(Pencil, Brush, Fill), TOOLS)`. Consider moving that
   assertion out of `BrushTest` into a new `ToolsTest` — it is about the tool
   registry, not the brush, and it has now been broken by two consecutive
   tool additions.
5. **Add `src/commonTest/kotlin/kmppaint/tools/FillTest.kt`** per §6.
6. **Extend `AppStateTest.kt`** per §6.
7. **Verify**: `./gradlew check`, then the headless smoke test in §7.

No changes to: `AppState.kt`, `App.kt`, `CanvasInput.kt`, `ToolBar.kt`,
`CanvasBitmap.kt`, `Palette.kt`, `PaletteBar.kt`, `ImageBitmaps.kt`,
`build.gradle.kts`.

---

## 6. Test plan

`src/commonTest/kotlin/kmppaint/tools/FillTest.kt`, following the
`BrushTest.kt:13-19` idiom — small bitmaps plus an `assertBitmapMatches(
bitmap) { x, y -> expectedColour }` helper that asserts every pixel with a
`"pixel ($x, $y)"` message. Local `private val red/blue/green` hex constants,
not palette imports.

1. `fillOnUniformCanvasRecoloursEveryPixel` — 5×4 white bitmap, fill at
   (2, 2) with red, every pixel red.
2. `fillStopsAtTheOutlineOfARectangle` — draw a hollow red rectangle with
   `Pencil.onDown`, fill the interior blue; assert interior blue, outline
   red, exterior white.
3. `fillIsContainedByADiagonalPencilOutline` — **the 4-vs-8-connectivity
   regression test.** Draw a diamond by four `Pencil.onMove` calls between
   the midpoints of a bitmap's edges, fill an interior pixel; assert no pixel
   outside the diamond changed. Fails loudly if anyone "optimises" the
   neighbour table to 8-way.
4. `fillLeaksThroughAGapInAnOpenShape` — same rectangle with one boundary
   pixel left white; assert the exterior *is* recoloured. Documents the
   behaviour as intended rather than a bug.
5. `fillOnlyAffectsTheRegionContainingTheSeed` — two disjoint white regions
   separated by a full-height red wall; fill one, assert the other is
   untouched.
6. `fillWithTheColourAlreadyUnderTheSeedIsANoOp` — fill red on a red pixel;
   assert the bitmap is unchanged *and* the call returns (the test failing by
   hanging is itself the termination check; a `@Test` timeout is not
   available in `kotlin.test` common, so keep the bitmap small).
7. `fillWithAnOutOfBoundsSeedIsANoOp` — negative and `>= width/height`
   coordinates; assert no throw and no pixel changed. `assertFailsWith` is
   *not* expected here.
8. `fillWorksOnASingleColumnAndASingleRow` and a 1×1 bitmap — degenerate
   geometry.
9. `fillUsesExactlyTheColourArgument` — two fills in different colours,
   counted via `copyPixels().count { it == red }`, mirroring
   `BrushTest.kt:125-136`.
10. `fillingTheFullCanvasCompletes` — 800×600 `CanvasBitmap`, one fill;
    assert all 480 000 pixels changed. Guards against stack overflow and
    quadratic blow-up.
11. `fillOnMoveDoesNothing` — call `Fill.onMove` on a non-uniform bitmap and
    assert every pixel is unchanged.
12. `toolsListIsExactlyPencilBrushFillInSelectorOrder` — the relocated
    registry assertion from step 4.

`src/commonTest/kotlin/kmppaint/AppStateTest.kt` additions:

13. Selecting `Fill`, then `onCanvasDown` on the default white canvas,
    recolours the whole bitmap in `activeColour` and bumps `version` exactly
    once.
14. A press-move-move-release sequence with `Fill` selected fills exactly
    once (assert the bitmap equals the single-fill result, not that `version`
    stayed at 1 — `onCanvasMove` still bumps it; see §3.5).
15. Fill uses the *currently selected* palette colour: select blue, fill,
    assert blue.

Run with `./gradlew jvmTest` (or `./gradlew check`).

---

## 7. Headless smoke test (per AGENTS.md)

Setup, input injection and capture exactly as in `AGENTS.md` — Xvfb on `:99`,
`skiko.renderApi=SOFTWARE`, python-xlib XTEST, ImageMagick `import`. From a
baseline screenshot measure the canvas origin and the **Fill** button centre;
Fill is the third tool button, ~44 dp below Brush in the left-hand `Column`
(`ToolBar.kt:19-20`, 40 dp buttons with 4 dp spacing).

1. **Closed shape, interior fill.** Pencil-drag a closed rectangle on the
   white canvas. Click **Fill**, click **blue** in the palette, click a pixel
   inside the rectangle. Verify with
   `convert shot.png -format "%[pixel:p{x,y}]" info:` that an interior pixel
   is `#0000FF`, an outline pixel is still `#000000`, and a pixel outside the
   rectangle is still `#FFFFFF`.
2. **Diagonal outline containment.** Pencil-drag a closed triangle (all three
   sides diagonal), fill inside it, and check a pixel just outside each
   diagonal edge is still `#FFFFFF`. This is the visual counterpart of test 3
   in §6.
3. **Whole-canvas fill.** On a fresh canvas, fill with red; sample four
   corners and the centre for `#FF0000`.
4. **Same-colour no-op.** Fill the same region twice with the same colour;
   `compare -metric AE before.png after.png null:` must print `0`.
5. **Drag does not repeat the fill.** With Fill selected, press inside a
   region, drag across a boundary into a second region, release. Only the
   first region changes.
6. **Regression: other tools unaffected.** Re-select Pencil and draw; the
   stroke still appears.

Clean up with `pkill -f kmppaint`, then kill `Xvfb` — one `pkill`/`kill` per
command, as AGENTS.md warns.

---

## 8. Non-goals

- **Tolerance / fuzzy matching.** Matching is exact `Int ==`. The palette is
  12 opaque colours, so nothing is anti-aliased and tolerance would buy
  nothing.
- **Antialiased fill edges.** Same reason.
- **Global ("fill all pixels of this colour anywhere") fill.** Contiguous
  only.
- **Undo.** The project has no undo/redo of any kind; a fill is as
  irreversible as a pencil stroke. If undo is added later it will need a
  bitmap-snapshot or command abstraction that does not exist today, and fill
  will fall out of it for free.
- **Scanline flood fill.** A worthwhile optimisation (§3.7) but not needed at
  800×600; revisit only if profiling shows the pixel-stack version stuttering.
- **Skipping the redundant recomposition during a Fill drag** (§3.5). Would
  need a new `Tool` property (e.g. `val continuous: Boolean`) plus a check in
  `AppState.onCanvasMove` — more surface than the wasted work justifies.
