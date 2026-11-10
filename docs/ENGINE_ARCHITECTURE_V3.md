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
6. Both Android JVM and emulator regression workflows include the engine
   branch, with specific new unit/instrumentation tests.

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

**M2 — asynchronous authoritative raster.** Separate the remaining command
executor and document state from the 5k-line view. Use immutable command
snapshots, a single ordered writer per raster surface, generation tokens, and
explicit save/undo/preview synchronization barriers. Do not move the current
mutable `DrawingView` code to a background executor directly: callbacks,
tiles and command history currently assume UI-thread coordination.

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
