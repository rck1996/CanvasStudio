# Canvas Studio — fluidez y flujo de creación tipo Procreate (2026-11-10)

Esta fase acerca Canvas Studio a un espacio de ilustración profesional **nativo de Android**.
No afirma paridad funcional con Procreate ni aceleración GPU certificada; prioriza
gestos inmediatos, opciones reutilizables y consumo de memoria acotado.

## Cambios implementados

### Renderer y memoria
- `SparseTileSurface.drawPreservingAlpha` mantiene un buffer ARGB temporal por
  superficie y lo limpia antes de cada uso, en lugar de crear/destruir un bitmap
  del tamaño de tile en cada dab con bloqueo alfa. El buffer se libera al reciclar
  la superficie. Misma operación `SRC_ATOP`, mismas reglas de transparencia.
- `mutateTilePixels` reutiliza un `IntArray` **exactamente** del tamaño del tile,
  compatible con el contrato JNI/Vulkan. El buffer se restablece al reciclar.
- `PreviewBitmapRepository` decodifica PNG en `Dispatchers.IO`, utiliza
  `inSampleSize` para no cargar la resolución original y mantiene un caché
  LRU de 12 MiB. No recicla eager los bitmaps desalojados porque Compose puede
  seguir mostrándolos.
- El selector HSV abandona 120 arcos y 576 celdas por redibujo y utiliza
  **un anillo y dos gradientes** para preservar la continuidad de tono, saturación
  y luminosidad. El gesto admite arrastrar, no solo tocar.

### Interacción y flujo creativo
- Los controles flotantes de tamaño y opacidad ahora se pueden **deslizar**
  directamente sin abrir la biblioteca de pinceles. Mantienen botones +/- para
  ajustes precisos y accesibilidad.
- El slider de tamaño tiene una curva logarítmica **2–180 px**: mejor resolución
  en lápices finos sin sacrificar tamaños grandes. Opacidad **5–100%**.
- Los lienzos personalizados se guardan como formatos reutilizables desde
  «Nuevo lienzo», con nombre, tamaño y DPI; se pueden actualizar y eliminar.
  Máximo 24 formatos; no modifica ni elimina dibujos.
- «Colores recientes» recuerda hasta diez tonos **opacos** en el dispositivo;
  las escrituras ocurren 550 ms después del último cambio para no interrumpir
  gestos continuos de la rueda de color. La paleta base sigue disponible.

## Verificación

Suites de regresión añadidas:
- `SparseTileScratchTest`: alpha lock conserva transparencia y no contamina
  trazos consecutivos; buffers JNI exactamente dimensionados.
- `PreviewBitmapRepositoryTest`: miniaturas limitadas y caché efectivo.
- `CanvasPresetRepositoryTest`: límites 40 Mpx, tamaños, nombres,
  actualizaciones y recuperación frente a JSON corrupto.
- `RecentColorRepositoryTest`: persistencia, unicidad, límite y limpieza.
- `QuickDialUiTest` y `ColorWheelUiTest`: deslizamiento real en Compose.
- `QuickBrushSizingTest` y `ColorWheelGeometryTest`: lógica pura en JVM.

La suite del emulador Android 15 (API 35) y lint/build deben aprobar antes
de fusionar. Las métricas de rendimiento reales (p95/p99 de frame, latencia del
S Pen, RAM, FPS con capas y documentos pesados) requieren profiling en tablet;
no se extrapolan porcentajes de velocidad a partir de pruebas funcionales.

## Siguientes prioridades

1. QuickShape con snapping y edición numérica de figuras, sin degradar trazos.
2. Gestos configurables de dos/tres dedos con supresión de entrada accidental.
3. ORA import con capas, TIFF/PSD por capas y exportación tiled.
4. Separar `DrawingView.kt` y `EditorScreen.kt` mediante límites testeados.
5. Pruebas de rendimiento y estrés sobre Galaxy Tab / S Pen.
