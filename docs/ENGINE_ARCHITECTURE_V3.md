# Canvas Studio Engine v3 — staged renderer reconstruction

Status: **experimental branch**, November 10, 2026. No changes to `main`.
This document describes code that exists and the remaining migration; it is not
a claim that the application has achieved GPU or Procreate parity.

## Ownership boundaries

| Owner | Responsibility | Source |
|---|---|---|
| Input | Read historical MotionEvent samples, preserve pressure/tilt/orientation/timestamps, transform batched coordinates | `input/StrokeInputBatch.kt`, `input/StylusInputController.kt` |
| Preview | Draw an ephemeral approximation without touching document tiles | `pipeline/StrokePreviewRenderer.kt`, AndroidX Ink |
| Stroke scheduler | Adapt per-frame raster batch size based on observed segment cost | `pipeline/StrokeFrameBudget.kt` |
| Document commands | Keep history, masks and serialized brush semantics authoritative | `DrawingModels.kt`, `DrawingView.kt` (migration pending) |
| Raster adapter | Reusable, serialized Canvas and clip for each tile mutation; no state escapes callback | `raster/TileCanvasScope.kt` |
| Sparse surface | Own tiles, LRU, dirty revisions and atomic saves | `SparseTileSurface.kt` |
| Background raster tasks | Ordered prepare/commit callbacks with cancellation and nonblocking barriers; currently used for prefetch, not authoritative strokes | `pipeline/OrderedRasterCommandQueue.kt` |
| Project IO | Coalesces obsolete saves; thumbnail composited off the UI thread | `pipeline/CoalescingSerialWorker.kt`, `raster/PreviewCompositor.kt` |
| GPU prototype | Restricted to debug; must pass CPU compatibility and overhead gates | `raster/TileRasterBackend.kt` |

## Implemented in this iteration

1. `TileCanvasScope` reuses a single software Canvas per surface, with
   document-space clipping, exact restore, and detach on exceptions. A shared
   scratch bounds object cannot escape the synchronized callback.
2. Sparse drawing paths iterate tile ranges without creating intermediate
   sets or per-tile Canvas objects. Alpha-lock continues using a reusable ARGB
   scratch tile, with `SRC_ATOP` composition.
3. `StrokeInputBatch` converts all historical sample coordinates in one
   `Matrix.mapPoints` call and preserves stylus properties without allocating
   a `StrokePoint` for rejected history samples.
4. `StrokeFrameBudget` bounds live rasterization between 1 and 32 segments
   per scheduled frame, learning an EWMA of segment costs. It is deliberately
   a hint: an individual segment can still exceed the frame budget.
5. `StrokePreviewRenderer` isolates transient stroke display from
   `DrawingView`; it never persists preview pixels.
6. `OrderedRasterCommandQueue` executes background tile prefetch and publishes
   status updates in order on the UI thread. It supports generation invalidation
   and nonblocking publication barriers, but does NOT run authoritative
   brush-raster commands yet.
7. `CoalescingSerialWorker` keeps one active save and at most one waiting
   autosave; repeated requests no longer spawn unbounded encoding threads.
8. `PreviewCompositor` composites the project's exact layer blend/mask
   structure in the save worker. `SparseTileSurface.drawAllDetached` reads
   individual tile copies without holding a surface lock across PNG IO or
   Canvas rendering. The API 26-28 blend fallback is preserved.
9. Save publication checks a committed content revision and document/save
   generation before the atomic directory commit. A changed document aborts
   the staged save, avoiding known stale-save replacement cases. This is not
   yet a fully serialized transaction with all concurrent editor mutations.
10. Both Android JVM and emulator regression workflows include the engine
   branch; tests cover layer/mask thumbnails, detached cached/disk tiles,
   save coalescing, queue ordering, barriers, and cancellation.

## Invariants that the replacement must preserve

- Project serialization and undo/redo are independent of the chosen backend.
- A stroke's final geometry, pressure, color, clipping, mask and alpha lock
  remain equivalent between CPU and GPU backends.
- A preview may be approximate; only committed commands change document pixels.
- Canceled gestures never enter history.
- No file read/PNG decode is introduced into `onDraw`.
- Dirty tiles may never be evicted before durable publication.
- Save/restore operations wait for completed writes; stale generations do not
  publish over new documents.
- GPU failures fall back to the CPU without partial or duplicate dabs.

## Remaining architecture milestones (NOT yet implemented)

**M2 — asynchronous authoritative raster (partially implemented).**
The worker/queue, nonblocking barriers, generation cancellation, serial save
and detached offscreen compositor are in place. **Final brush-dab rendering
is still performed on the main thread** to preserve existing pixel behavior,
masks, color pickup, and undo. The next migration is to extract the brush
renderer into an isolated raster session with immutable commands and a
single ordered writer for each destination tile/surface. Commit and failure
handling must join the existing history, save and preview handoff through
explicit barriers. Do NOT simply execute `DrawingView.drawCommand` from a
worker: its Paint/cache state is mutable and View-thread confined.

**M3 — lower-copy GPU path.** Today's Vulkan implementation does per-tile
CPU->GPU upload, synchronization, and GPU->CPU readback; it is debug-only and
supports only two brush materials. Prototype persistent GPU tiles and batched
dabs before deciding whether to enable Vulkan. CPU remains the compatibility
path for masks, feathering, unsupported brush types and failures.

**M4 — retained composition and input parity.** Add dirty-region composition
and pressure/tilt preview parity where GPU/AndroidX support permits. Avoid
regressions in alpha lock, layer blending, symmetry and pixel output.

## Performance qualification

Compare the *same recorded input streams and project files* against the parent
audit branch; collect Perfetto traces and Android frame metrics on an actual
S Pen tablet. Test an empty document, 20+ layers, dense textured brushes,
large 4K/40Mpx documents, fast scribbling, undo/replay, rapid zoom and low RAM.

Measure p50/p95/p99 frame time, first-pixel latency, input-to-commit latency,
GC pauses, resident/peak RAM, tile miss rate, PNG flush time, battery and
back-end fallback count. The existing `DrawingPerformanceMetrics` counters
are diagnostic and do NOT substitute for full display-presentation latency.

**Release gate:** no missing or duplicated strokes; no image regressions in
CPU compatibility tests; no unsynchronized saves; emulator/JVM/CI green;
real-device p95/p99 and stylus behavior checked. No FPS or percentage
improvement is claimed from static code review or functional test success.

## Background save safety notes

- The new save worker serializes active saves, coalesces only waiting requests,
  and completes active publication on view detachment (graceful shutdown).
- Preview layer metadata is captured on the UI thread; only immutable values and
  thread-safe raster surfaces cross the worker boundary.
- The compositor uses individual **detached** tile bitmaps, releasing each copy
  after drawing. It does not increase the live sparse-tile cache.
- Committed-document revision guards reject stale save attempts before commit;
  the short interval between its final check and atomic swap remains a future
  serialization concern. No claim of perfect crash/parallel-edit isolation yet.
- Undo/redo and final brush rendering remain UI-serialized. Existing project
  formats are unchanged and no unvalidated GPU backend is enabled.
