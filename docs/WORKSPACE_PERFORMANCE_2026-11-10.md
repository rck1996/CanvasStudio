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

## Iteración: navegación fluida de galería

- La galería usa una cuadrícula adaptativa más densa en pantallas compactas, con
  márgenes reducidos sin afectar el lienzo ni el editor.
- Se agregaron filtros de ordenación por recientes, nombre y cantidad de píxeles
  (comparación `Long` para evitar overflow), y estado vacío con limpieza rápida.
- `PreviewBitmapRepository` incorpora 16 locks suspendibles por hash de clave;
  las solicitudes concurrentes del mismo PNG reutilizan la miniatura decodificada,
  mientras otras claves pueden decodificarse en paralelo. Conserva el caché LRU
  de 12 MiB y la decodificación en IO.
- Se añadieron pruebas para ordenación, búsqueda de resolución y solicitudes
  concurrentes de miniaturas. No se atribuyen mejoras porcentuales de FPS sin
  medición sobre dispositivo físico.

### Pendientes para acercarse a Procreate

QuickShape, transformación por perspectiva/warp, gestión avanzada de color y
compatibilidad PSD con capas requieren etapas separadas y pruebas de documentos
reales. Prioridad inmediata: perfilar latencia de trazo, p95/p99 de frame y
presión de memoria en tablet Android con stylus.

## QuickShape de bajo riesgo · 9 de noviembre, continuación

- Reconocimiento conservador de línea mantenida, activable desde la barra del editor y persistente por preferencias.
- Desactivado de forma predeterminada: la ruta de dibujo en tiempo real no cambia si no se usa.
- Usa la vista previa diferida existente durante el gesto activo; los pinceles compatibles conservan AndroidX Ink.
- La vista previa diferida reutiliza su instancia de `Paint`, evitando crearla en cada fotograma.
- La detección solo se ejecuta en `ACTION_UP`, con coste lineal respecto de las muestras almacenadas.
- Casos de prueba JVM y de instrumentación: línea válida, pausa insuficiente, jitter, retrazos y curvas.
- Para validar rendimiento real siguen pendientes métricas de FPS, p95/p99, memoria y latencia en tablet con S Pen.

Documentación de la función: [QUICKSHAPE_2026-11-09.md](QUICKSHAPE_2026-11-09.md).

### Última optimización: sin asignaciones por segmento de vista previa

El render de la vista previa diferida utilizaba `transformPoint()` dentro de cada
segmento, creando un `FloatArray` y una copia `StrokePoint` por muestra **y por
fotograma**. Ahora transforma ambos extremos con un arreglo de cuatro floats reutilizado
por `DrawingView` mediante `Matrix.mapPoints`; también conserva una instancia
persistente de `Paint`. Las coordenadas y la presión dibujadas mantienen el mismo
algoritmo; el objetivo es reducir pausas de GC con trazos largos y simetría activa.
Las mejoras de FPS requieren medición en dispositivo real.

## Controles rápidos · corrección de certificación (noviembre 2026)

Se amplía de 36 a 48 dp el alto táctil de ambos sliders y se amplían los botones +/- a 42 dp para facilitar la interacción con dedos y stylus. El valor del aro central pasa a 11 sp. Las pruebas de regresión separan un gesto de arrastre originado sobre el pulgar real de la acción de accesibilidad `SemanticsActions.SetProgress`; la prueba anterior usaba `swipeRight()` desde 10% del ancho aunque el pulgar empezaba en 15%. No se oculta el fallo anterior: consultar el run 42748420295 (63/65).

## Iteración viewport y editor · 10 noviembre 2026

**Motor de dibujo, ruta de renderizado visible**

- El tablero de transparencia pasa de miles de rectángulos por fotograma a un único draw del área visible con shader de una textura repetida de 96 × 96 px. Se conservan las celdas originales de 48 px y la alternancia cromática.
- El viewport inverso se calcula **una vez por fotograma** sobre un FloatArray de ocho números y un RectF reutilizados. El resultado se comparte entre tablero, tiles, grilla y prefetch. La grilla dibuja solo segmentos visibles.
- Los `saveLayer` para clipping y máscaras usan `visibleBounds` y no la extensión total del documento. Beneficio esperado especialmente en lienzos grandes con poco zoom; la máscara persistida no cambia.
- El render visible por tile calcula directamente sus cuatro coordenadas y evita asignar un `Rect` por tile visible en cada capa/fotograma.
- La función de simetría mantiene matrices cacheadas mientras no cambien el tipo de simetría o la geometría del documento; la rutina de previsualización reusa esos objetos.
- La detección de tiles no residentes evita crear una lista intermedia por fotograma y termina en la primera falta.

**Workspace / acercamiento al concepto visual**

- La opción Ajustes de pincel de la barra izquierda abre una paleta flotante sobre el lienzo, con previews existentes, búsqueda, filtros de favoritos/recientes y selección inmediata. No abre un diálogo de pantalla completa. Se mantiene el botón para abrir la biblioteca avanzada, además del acceso antiguo de la rueda rápida.
- Barra de herramientas lateral más estrecha (64 dp compacta / 76 dp normal, desde 72/92), recuperando espacio útil del documento.
- Los previews de pincel permanecen virtualizados con `LazyColumn`, con cálculos de vista previa memorizados por preset.

**Validación**

- Nuevo `RenderPathOptimizationTest`: patrón de 48 px y reutilización / invalidación de matrices de simetría.
- Nuevo `FloatingBrushPaletteUiTest`: selección de preset sin modal, navegación a biblioteca avanzada, búsqueda que conserva abierta la paleta.
- Ejecutar CI Android, instrumentación Android 15 y regresiones de clipping/máscaras. No se declaran mejoras cuantitativas de FPS, latencia o memoria sin medición física con S Pen.

## Iteración de trazo y composición, 10 de noviembre

- `StrokeBatchTransform` transforma las coordenadas de un lote en una sola llamada a `Matrix.mapPoints`; reutiliza su `FloatArray`, `ArrayList` y `RectF` entre lotes, sin compartir datos entre vistas.
- La estimación de bounds por lote usa min/max de muestras y una expansión con `maximumBrushExtent` calculada una sola vez, idéntica a la unión de rectángulos por segmento.
- La búsqueda de pigmento de la capa se resuelve una sola vez por lote, si está activada. La información de presión, inclinación, orientación y tiempo de las muestras se mantiene.
- El selector flotante de pinceles puede desplazarse tomando su encabezado. La posición está acotada al área del lienzo y no interfiere con la búsqueda ni la elección del pincel.
- El panel lateral de inspector ocupa 286/320 dp en lugar de 316/360 dp, recuperando lienzo.
- Añadidas pruebas de transformación, reutilización y equivalencia de límites. La reducción esperada de asignaciones no sustituye el perfilado de latencia y frame time en un S Pen físico.
