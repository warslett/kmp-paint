# Fill Tool — Implementation Plan

## Goal

Add a **Fill** tool to the tool selector. When the user clicks a pixel on the
canvas with the Fill tool active, every pixel of the *same colour* as the
clicked pixel that is reachable from the click by a 4-connected path of
same-coloured pixels is repainted in the active palette colour. Pixels of any
different colour act as fill boundaries — so a closed pencil outline encloses
a region that the Fill tool will paint without leaking past the outline.

This file is a plan only; it does not change any source.

## Scope and non-goals

### In scope (FR-FILL)
- A new `Fill` tool object implementing the `Tool` sealed interface.
- 4-connected flood fill seeded at the click pixel.
- Fills against the *clicked* colour, not the active colour, so clicking a red
  region with the active colour blue replaces the contiguous red region with
  blue.
- Clicking a pixel whose colour equals the active colour is a no-op (would
  otherwise fill the whole region with the same colour, which is harmless but
  wasteful and confusing to test).
- Out-of-bounds clicks (handled before reaching the tool by `onCanvasDown`
  via `CanvasBitmap` invariants) must not crash: the tool checks bounds itself
  before reading the seed colour.

### Non-goals (PRD §2 parity — not doing more than Pencil/Brush)
- Adjustable tolerance / anti-aliased edges: colours match exactly by `==`
  on the packed `Int` ARGB value, the same equality `CanvasBitmap` already
  relies on for `WHITE` and the palette swatches.
- 8-connected fill. 4-connected is the_paint-style behaviour and avoids
  leaking through single-pixel diagonal corners in pencil outlines.
- Undo / redo (no existing tool has it).
- Gradient or pattern fills.

## Design summary

`Tool.onDown(bitmap, x, y, colour)` already fires synchronously on pointer
press in `AppState.onCanvasDown` (AppState.kt:68), and `onMove` is the
drag handler. Fill is click-only, so:

- `Fill.onDown` performs the whole flood fill.
- `Fill.onMove` is a no-op (dragging the fill tool does nothing — matches
  the "click to fill" mental model and is harmless).

The flood fill lives in a new file `tools/FloodFill.kt` as a pure function
`floodFill(bitmap, startX, startY, replacement: Int)`, separate from the
`Fill` object so it is unit-testable without going through the `Tool`
interface (mirroring how `forEachPixelOnLine` and `forEachPixelInDisc`
are extracted into `Lines.kt` / `Discs.kt`).

### Algorithm

Iterative 4-connected flood fill using an explicit stack (`IntArray` of
(x, y) pairs, capacity `width * height * 2` worst case, sized lazily as an
`ArrayList<Int>`), **not** recursion — a 800×600 canvas can have 480 000
pixels and the JVM/Kotlin default stack would overflow on a deep recursion
(spiral fills, long thin regions).

The seeded fill colors every pixel `p` reachable from the seed by 4-adjacent
steps where `bitmap[p] == targetColour`, setting each such pixel to
`replacement`. Pseudocode:

```
target = bitmap[startX, startY]
if target == replacement: return          // no-op short-circuit
stack = ArrayList<Int>(); stack.add(startX, startY)
while stack not empty:
    y = stack.removeLast(); x = stack.removeLast()
    if x out of [0,width) or y out of [0,height): continue
    if bitmap[x, y] != target: continue    // boundary, already visited, or replaced
    bitmap[x, y] = replacement
    stack.add(x + 1, y); stack.add(x - 1, y)
    stack.add(x, y + 1); stack.add(x, y - 1)
```

The `bitmap[x,y] = replacement` write doubles as the "visited" mark, so no
separate visited bitmap is needed and no pixel is pushed more than four
times (once per neighbour). Memory is O(width * height) worst case, which
is fine for 800×600.

`CanvasBitmap.get` throws `IllegalArgumentException` on out-of-bounds reads
(CanvasBitmap.kt:25-28), so bounds are checked **before** the read in the
loop. The seed read in `Fill.onDown` is guarded by an explicit bounds check
because `AppState.onCanvasDown` does not pre-clamp (it forwards raw bitmap
pixels; out-of-bounds pencil strokes work only because `CanvasBitmap.set`
ignores writes — but `get` does not tolerate reads).

### Equality / matching

Packed `Int` ARGB equality (`==` on `Int`) is the colour identity used by
`CanvasBitmap`, the palette (opaque swatches, exact match per AGENTS.md
"pixels match swatch colours exactly"), and the existing tools. Tolerance
would break the "pencil outline is the boundary" contract because antialiased
edge pixels would be neither the outline colour nor the interior colour and
could be erroneously filled.

## Step-by-step

### Step 1 — `tools/FloodFill.kt`
New pure function:
```kotlin
fun floodFill(bitmap: CanvasBitmap, startX: Int, startY: Int, replacement: Int)
```
- Bounds-check `(startX, startY)`; return silently if out of bounds (defensive;
  the tool layer also checks, but the function should be safe on its own for
  unit tests).
- Read `target = bitmap[startX, startY]`.
- If `target == replacement`, return.
- Iterative stack fill as above.

### Step 2 — `Fill` object in `tools/Tool.kt`
```kotlin
object Fill : Tool {
    override val label = "Fill"
    override fun onDown(bitmap, x, y, colour) { floodFill(bitmap, x, y, colour) }
    override fun onMove(bitmap, fromX, fromY, toX, toY, colour) { /* no-op */ }
}
```
Append `Fill` to `TOOLS`:
```kotlin
val TOOLS: List<Tool> = listOf(Pencil, Brush, Fill)
```
The `ToolBar` (ToolBar.kt:39) iterates `TOOLS` and renders a button per entry
with the tool's `label`, so Fill appears automatically as a third button
labelled "Fill". No UI code changes.

### Step 3 — Tests: `commonTest/kotlin/kmppaint/tools/FillTest.kt`
Cover the contract, in the style of `PencilTest.kt` (construct a small
`CanvasBitmap`, drive the tool, assert every pixel):
1. **Fills a closed pencil rectangle.** Draw a red outline with
   `Pencil.onMove` around an interior, then `Fill.onDown` a blue interior
   pixel with active colour blue → all interior pixels become blue, outline
   stays red.
2. **Does not leak past the boundary.** Same setup; assert pixels outside the
   outline are still `WHITE`.
3. **Replaces only the clicked colour.** Two adjacent same-colour regions in
   different colours — fill one, the other is untouched.
4. **Clicking active colour on a region of active colour is a no-op.** Fill
   white with white when active is white — bitmap unchanged (assert via
   pixel scan; also confirms the `target == replacement` short-circuit).
5. **Out-of-bounds click does not throw.** `Fill.onDown(bitmap, -1, 0, red)`
   returns normally; bitmap unchanged.
6. **Flood handles a full-canvas fill.** 10×10 all-white → fill centre with
   red → entire canvas red.
7. **Diagonally-touching regions are NOT connected.** A red checkerboard
   pattern where same-colour pixels touch only at corners — fill seeds at
   one such pixel colours exactly that pixel (4-connectivity contract).
8. **`Fill.onMove` is a no-op.** Call `onMove` on a populated bitmap; assert
   no change.

### Step 4 — Smoke test docs (optional)
If a `docs/smoke/` manual-test note is wanted, add a `docs/smoke/fill.md`
with the headless Xvfb steps from `AGENTS.md`: select Fill, draw a closed
shape with Pencil, click inside with Fill, capture screenshot, and
`compare -metric AE before.png after.png null:` to confirm interior changed
while the outline didn't.

## Risks / invariants to preserve

- **No recursion.** A pathological region (e.g. a long spiral) would overflow
  the call stack. Use an explicit `ArrayList<Int>`-backed stack.
- **Bounds before read.** `CanvasBitmap.get` throws on out-of-bounds; the
  stack loop must guard each pop.
- **No separate visited matrix.** The replacement write is the visit mark;
  adding a `BooleanArray` would double memory for no correctness gain.
- **Don't touch `AppState`/`CanvasInput`/`App`.** Fill fits the existing
  `Tool` contract — `onDown` does all work, `onMove` is a no-op. The
  unidirectional "UI event → tool → bitmap → version bump → recompose" flow
  already handles Fill for free (AppState.kt:73 `onBitmapChanged()` fires
  after `onDown`, bumping `version`, which `App.kt:29` keys the image
  re-conversion on).
- **Colour equality stays exact `Int` `==`.** No tolerance, no channel
  decomposition — keeps the pencil-outline-as-boundary contract.

## Files touched

| File | Change |
|------|--------|
| `src/commonMain/kotlin/kmppaint/tools/FloodFill.kt` | NEW — `floodFill` function. |
| `src/commonMain/kotlin/kmppaint/tools/Tool.kt` | Add `Fill` object; append to `TOOLS`. |
| `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` | NEW — tests per Step 3. |

No changes to `AppState.kt`, `CanvasInput.kt`, `App.kt`, `Palette.kt`,
`CanvasBitmap.kt`, or `ToolBar.kt`.

## Verification

```bash
./gradlew check          # runs commonTest, including the new FillTest
./gradlew run            # manual / smoke: select Fill, click inside a pencil shape
```
Headless smoke equivalent follows the pattern in `AGENTS.md`: select the Fill
button by tool-bar coordinates, draw a closed rectangle with Pencil, click an
interior pixel, `import -window root shot.png`, and confirm the interior
matches the active swatch colour exactly.