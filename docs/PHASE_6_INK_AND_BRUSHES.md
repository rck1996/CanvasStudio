# Phase 6: Ink and brush experience

Canvas Studio keeps its tile-based raster engine as the document source of truth. For large textured brushes, an AndroidX Ink front buffer renders the active stroke at low latency while the engine commits the final deterministic raster stroke on pointer-up. This avoids rebuilding expensive texture stamps during every move event without changing the saved result.

The tablet brush library follows the supplied mockup: category navigation, search, a virtualized preset list, and a parameter inspector. Dedicated dry-brush, bristle, watercolor and oil stamp families make the listed presets materially different instead of merely changing a label.

## Validation on Galaxy Tab S8 (SM-X700)

- Raster instrumentation: 3/3 cycles passed; 14 tests per cycle and 600 strokes persisted in 6.15 seconds.
- Visual stress: 120 Carboncillo strokes at 159 px; the first half remained visible after the second half and after reopening the document.
- The stress harness handles first-launch onboarding, retries transient UIAutomator reads, and filters the virtualized brush list by name.

## Raster handoff hotfix

Finished AndroidX Ink strokes are no longer removed as soon as the pointer is lifted.
Their low-latency preview remains visible until `DrawingView` has presented two raster
frames. A small tested handoff gate merges bursts and restarts the countdown when a
new stroke finishes, preventing transient front-buffer holes that looked like older
strokes were being erased.

Unchanged grid, symmetry and perspective values no longer invalidate the canvas when
Compose updates the Android view during brush selection. The brush dock also returns
to its library header when the user taps the Brushes tab or selects a preset.

`UiAutomationService already registered` can appear in Samsung logcat when a cancelled shell inspection overlaps another UI inspection. It is emitted by the device UIAutomator service, not Canvas Studio.

## Real-device captures

![Brush library on Galaxy Tab S8](images/brush-library-tab-s8.png)

![Large textured brush stress run](images/thick-brush-stress-tab-s8.png)
