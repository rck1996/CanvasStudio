# Fase 10: base del renderer

## Alcance cerrado

- Se mantiene el renderer de produccion Canvas/Bitmap con `SparseTileSurface` y tiles PNG de 512 px.
- La entrada de stylus, el muestreo de trazo y la evaluacion de pincel quedan separados de `DrawingView`.
- `BrushDab` es un valor sin recursos graficos y `TileRasterBackend` delimita el backend de raster.
- `BitmapCanvasTileRasterBackend` envuelve las rutas ya validadas de Canvas; no altera su resultado.
- Las metricas solo se activan en builds debug y no se persisten ni se envian fuera del dispositivo.

## Medicion disponible

`DrawingPerformanceMetrics` registra contadores de entrada, muestras, dabs, tiles, cache y replay,
y acumula tiempos de preview, commit, evaluacion, raster, prefetch, frame y guardado. Las rutas de
release no llaman a `System.nanoTime()` para estas metricas.

## Evolucion posterior

La Fase 2 reemplazo el filtrado geometrico completo de `rebuildLayerRegion` por un indice de comandos
por superficie/tile y checkpoints adaptativos de sesion. El renderer de produccion sigue siendo el
mismo backend Canvas/Bitmap.

## No implementado deliberadamente

No hay migracion a Vulkan, OpenGL o Compose Canvas. Cualquier backend experimental futuro debe
demostrar paridad visual, alpha lock, mascaras, selecciones y retencion bajo presion antes de poder
reemplazar al backend Canvas.
