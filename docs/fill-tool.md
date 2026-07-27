# Fill Tool Implementation Plan

## Goal

Add a paint-bucket-style `Fill` tool. When the user presses a canvas pixel, the tool reads that pixel's current ARGB colour and replaces the entire contiguous region of that exact colour with the active palette colour. Pixels of any other colour bound the region, so a closed one-pixel-wide shape drawn with `Pencil` contains the fill.

## Behaviour Decisions

- Treat pixels as connected only through their four orthogonal neighbours: left, right, up, and down. Do not connect regions diagonally. This lets an 8-connected, one-pixel pencil line, including diagonal segments, act as a solid boundary rather than allowing the fill through diagonal gaps.
- Compare packed ARGB `Int` values exactly. The bitmap and palette already use this representation and only opaque colours.
- Run the fill once on pointer down. `Fill.onMove` is a no-op, so dragging after the initial press does not repeatedly fill regions.
- Ignore presses outside the bitmap. This is required because `CanvasBitmap.get` rejects out-of-bounds reads and pointer coordinates can leave the canvas.
- Return immediately when the clicked colour already equals the active colour. This avoids unnecessary traversal and is required for termination when recolouring is used to mark visited pixels.
- Use an iterative traversal rather than recursion. The 800 x 600 canvas can contain a 480,000-pixel region, which is large enough to overflow the call stack with a recursive flood fill.

## Implementation

### 1. Add the fill algorithm and tool

Update `src/commonMain/kotlin/kmppaint/tools/Tool.kt`:

- Add a singleton `Fill` object implementing the sealed `Tool` interface, with the label `"Fill"`.
- Implement `onDown` as an iterative four-neighbour flood fill:
  1. Bounds-check `(x, y)` before reading it.
  2. Read the clicked pixel into `targetColour`.
  3. Return if `targetColour == colour`.
  4. Allocate an `IntArray(bitmap.width * bitmap.height)` as a work stack and encode each coordinate as `y * width + x`. This avoids recursion and per-pixel `Pair` allocations.
  5. Recolour the starting pixel and push its encoded index.
  6. Until the stack is empty, pop a pixel, inspect each in-bounds orthogonal neighbour, and when a neighbour still equals `targetColour`, recolour it immediately and push it.
- Mark pixels by applying the replacement colour when they are pushed, not when they are popped. The bitmap then doubles as the visited set, ensuring every region pixel is queued at most once and the fixed-size stack cannot overflow.
- Implement `onMove` as a no-op.
- Append `Fill` to `TOOLS`, preserving selector order as `Pencil`, `Brush`, `Fill`. `ToolBar` already renders this list and dispatches selection through `AppState`, so no Compose UI changes are needed.
- Update stale comments in `Tool.kt` that describe Fill as future work.

The algorithm is `O(width * height)` in the worst case and uses `O(width * height)` temporary integer storage. No `CanvasBitmap`, `CanvasInput`, `AppState`, or rendering API changes are needed: the existing tool dispatch mutates the bitmap on pointer down and increments `AppState.version`, causing one immediate recomposition.

### 2. Add focused tool tests

Create `src/commonTest/kotlin/kmppaint/tools/FillTest.kt` with small deterministic bitmaps covering:

- Filling a new bitmap recolours the entire canvas.
- A rectangle drawn with `Pencil` contains a fill: every interior pixel changes, border pixels retain their pencil colour, and exterior pixels remain unchanged.
- A diagonal pencil boundary contains the fill, pinning the intentional four-neighbour connectivity rule.
- A disconnected area with the same target colour is not changed.
- Internal pixels of a different colour act as boundaries and are preserved.
- A region touching each canvas edge and corner fills correctly without wrapping to another row.
- Filling with the clicked pixel's existing colour leaves the bitmap unchanged.
- Out-of-bounds `onDown` calls do not throw or mutate the bitmap.
- `onMove` does not mutate the bitmap.
- `TOOLS` is exactly `listOf(Pencil, Brush, Fill)` so the toolbar registration and order are covered. Move the existing list assertion from `BrushTest` or update it there to avoid duplicate ownership.

### 3. Verify state integration

Extend `src/commonTest/kotlin/kmppaint/AppStateTest.kt` to verify the existing event path with `Fill` selected:

- Draw or arrange a bounded region, choose a different palette colour, select `Fill`, and call `onCanvasDown` inside it.
- Assert that only the connected target-colour region changed, `version` increased once, and both `activeColour` and `activeTool` remain selected.
- Call `onCanvasMove` during the same pointer gesture and verify it does not trigger another fill. The current `AppState` still increments `version` for any moved drawing event; assert pixel behaviour rather than changing that shared versioning contract as part of this feature.

## Verification

1. Run all common tests with `./gradlew allTests --console=plain`.
2. Run the desktop build with `./gradlew jvmJar --console=plain`.
3. Perform a headless UI smoke test using the Xvfb procedure in `AGENTS.md`:
   - Draw a closed shape with `Pencil`.
   - Select a palette colour, select the new `Fill` button, and click inside the shape.
   - Capture a screenshot and confirm the interior has the selected colour while the outline and exterior are unchanged.
   - Click outside the shape with a second colour and confirm only the exterior region changes.
   - Drag with `Fill` selected and confirm only the region under the initial press is filled.

## Acceptance Criteria

- Clicking with `Fill` replaces exactly the four-connected region matching the clicked pixel's original colour.
- Different-coloured pixels, including a closed pencil outline, stop the fill and retain their colours.
- Disconnected regions of the same colour are unaffected.
- The selected palette colour is used exactly, and selecting or using Fill does not alter the active colour.
- Same-colour and out-of-bounds fills are safe no-ops.
- Large open regions fill without stack overflow or UI crashes.
- Fill appears after Brush in the toolbar and can be selected through the existing app state flow.
