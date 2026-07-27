# Phase 4 test build

## Arquitectura de selección

La selección se representa mediante un `Path` en coordenadas de documento y una lista de puntos estable. Los comandos creados mientras existe una selección guardan su geometría de clip para que deshacer y rehacer no dependan del estado actual de la interfaz.

Las transformaciones siguen este flujo:

```text
selección activa
    ↓ extracción raster limitada por máscara
limpieza temporal del origen
    ↓
preview con Matrix (mover / escala / rotación)
    ↓ confirmar
TransformSelectionCommand
    ├── limpia geometría original
    └── dibuja patch transformado
```

El bitmap del comando existe solo durante la sesión. El guardado persistente almacena el resultado en tiles, por lo que al reabrir el proyecto no se necesita serializar el historial raster.

## Fase 3C

La precarga se ejecuta en un executor de un hilo con generación cancelable. Cada cambio significativo del viewport invalida la solicitud anterior. `SparseTileSurface` mantiene sincronización interna para que una carga en segundo plano no compita de forma insegura con dibujo o guardado.


## Herramientas incluidas en la entrega de pruebas

- Selección rectangular, elíptica y lazo libre.
- Transformación raster con traslado, escala, rotación y volteo.
- Relleno contiguo y degradado lineal.
- Bloqueo alfa, clipping de una capa y modos de fusión ampliados.
- Simetría vertical/radial, cuadrícula y perspectiva de uno o dos puntos.
- Importación de imágenes con muestreo preventivo para proteger la memoria.
- Exportación PNG y OpenRaster por streaming dentro del ZIP para reducir copias en RAM.

## Seguridad de memoria

- Selección transformable: máximo aproximado de 64 MB por patch.
- Relleno contiguo: máximo 12 Mpx y comprobación adicional del heap.
- OpenRaster: máximo 24 Mpx por capa en esta etapa.
- Las capas continúan siendo sparse y no asignan un bitmap completo durante la edición normal.

## Compatibilidad de proyecto v5

Nuevas propiedades:

```text
layer.<n>.alphaLocked
layer.<n>.clipping
renderer=visible-tiles-lru-async-prefetch
```

Los tiles siguen siendo PNG transparentes de 512 × 512 px y la ausencia de archivo representa un tile vacío.
