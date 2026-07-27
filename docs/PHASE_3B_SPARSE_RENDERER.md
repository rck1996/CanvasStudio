# Phase 3B — Sparse tiled renderer

## Data flow

```text
project/layers/<id>/tiles
          │ open (file copy, no decode)
          ├── session/<id>/base   immutable undo baseline
          └── session/<id>/work   current raster backing store
                                      │
                                      └── SparseTileSurface LRU cache
                                             ├── visible tiles
                                             ├── one-tile prefetch margin
                                             └── tiles touched by drawing
```

## Invariants

- A layer never allocates a bitmap with the dimensions of the whole document.
- Every resident bitmap represents exactly one tile, including smaller edge tiles.
- Dirty resident tiles are written to the session backing store before eviction.
- The project directory changes only during an explicit/autosave transaction.
- The base directory is not promoted on save; this preserves undo/redo throughout the open session.
- A transparent tile is represented by the absence of its PNG file.
- Save acknowledgements are versioned, so edits made during a background save remain dirty.

## Cache policy

- Access-ordered LRU per layer.
- A global budget is derived from the process heap and divided among open layers.
- The minimum resident allocation is one full 512 px tile per layer.
- Adding or deleting a layer redistributes the budget immediately.
- Rendering a large preview/export iterates tiles and evicts older entries instead of retaining the full document.

## Compatibility

- v2: full layer PNG is tiled once, then saved as v4.
- v3: tile files are copied into the session without decoding.
- v4: native sparse tiled format.

## Phase 3C boundary

Phase 3B still decodes a missing visible tile synchronously. Phase 3C will add asynchronous prefetch, batched raster work, frame-safe invalidation and a low-latency front buffer/GPU composition path.
