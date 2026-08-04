# Fase 3: renderer experimental, materiales y tutorial

## Arquitectura

El documento y los tiles siguen siendo ARGB_8888 y Canvas/Bitmap continúa como renderer estable.
La elección del renderer existe solo en builds debug y nunca se guarda en el proyecto.

```text
StylusInputController
  -> StrokeSampler
  -> BrushEvaluator / BrushDabBatchBuilder
  -> TileRasterBackend
       |- BitmapCanvasTileRasterBackend (predeterminado)
       `- VulkanTileRasterBackend (debug, experimental)
            -> JNI Vulkan 1.1 compute
            -> lotes por tile
            -> readback único por lote de tile
```

`VulkanTileRasterBackend` soporta inicialmente `technical-ink` y `graphite-shader` sobre contenido
de capa. El resto de los presets, las máscaras, las selecciones con feather, una inicialización
fallida o un error de ejecución vuelven explícitamente a
Canvas/Bitmap. Antes de un lote Vulkan se conserva una copia transaccional de los tiles; un fallo
restaura esas copias antes del fallback, evitando doble acumulación o contenido parcial.

El pipeline y los descriptores Vulkan se crean una vez. Los buffers host-visible se amplían por
potencias de dos y se reutilizan. Todos los dabs de un tile se registran en un command buffer y se
envían con una sola submission. La implementación incluye selección poligonal, selección invertida,
borrador, alpha lock, punta circular/elíptica, inclinación, orientación, presión, taper, flujo y
grano determinista anclado a coordenadas del documento.

Perfetto expone secciones `CanvasStudio.Vulkan.Upload`, `Raster`, `Submit`, `Wait`, `Readback` y
`TileBatch`. Las métricas locales incluyen percentiles de frame, dabs/s, fallbacks y tiempos CPU/GPU.

## Materiales prioritarios

- **Lápiz HB:** depósito moderado, rango claro en baja presión, grano fino de papel anclado,
  ensanchamiento gradual por tilt y acumulación controlada.
- **Lápiz 6B:** mayor carga, borde más suave, grano áspero, respuesta más fuerte a presión/tilt y
  sombreado claramente más ancho que HB.
- **Tinta técnica:** punta circular sin grano ni dual brush, spacing 2,5 %, presión/velocidad estables
  y taper corto de entrada/salida.
- **Plumilla cómic:** punta oval orientada por trayectoria, presión rápida, negro sólido y taper.
- **Rotulador plano:** punta chisel orientada por S Pen, flujo estable, superposición controlada y
  textura de papel muy ligera.
- **Acuarela granulada:** glazing de baja opacidad, carga decreciente, borde húmedo y grano de
  acuarela anclado. No se presenta como simulación física de fluidos.

`BrushFixture` define siete trayectorias deterministas: presión lenta, línea rápida, curva, zigzag,
sombreado con tilt, tres pasadas y cruce de cuatro tiles. Los hashes se publican en Logcat con
`CanvasStudioBrushFixtures`.

## Tutorial

El tutorial utiliza un documento temporal y un reducer de eventos de dominio; no conoce
coordenadas de la interfaz del editor. Sus 14 módulos son navegación, S Pen, borrador, color,
capas, máscaras, selección, transformación, formas/relleno, degradado, simetría/guías, undo/redo,
guardado/exportación y personalización de pinceles.

Cada módulo exige un evento válido antes de habilitar **Siguiente**. Se puede pausar, reanudar,
omitir, reiniciar o repetir por módulo. El progreso se guarda exclusivamente en
`canvas_studio_tutorial_progress`; cerrar el tutorial no modifica ningún proyecto. La composición
se adapta a horizontal y vertical y todos los controles de acción tienen semántica accesible.

## Alcance y límites

- Vulkan sigue siendo experimental y nunca se selecciona automáticamente.
- Acuarela, óleo, cerdas, dual brush complejo, filtros, transformaciones y composición completa
  continúan en Canvas.
- El readback ocurre una vez por tile/lote; todavía representa una parte importante del costo total.
- Canvas y Vulkan usan los mismos `StrokePoint`, `BrushDab`, color y semilla, pero sus bordes
  antialias no son bit-exactos. Las pruebas A/B exigen cobertura equivalente con tolerancia explícita.
- El formato de proyecto y el historial escalable de Fase 2 no cambian.

## Certificación

Los comandos y resultados medidos finales se guardan en `test-logs/` y no se versionan. La suite
incluye A/B de tinta y grafito, fallback simulado, selección, alpha lock, borrador, 200 trazos
gruesos, 500 trazos largos, cambio seguro de backend y una sesión continua de diez minutos.
