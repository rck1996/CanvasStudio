# Performance Hotfix 1.5.1

Objetivo: eliminar los bloqueos progresivos detectados en una Samsung Galaxy Tab S8 durante pruebas de fase 4.

## Causas corregidas

1. Los pinceles con taper final o respuesta a velocidad reconstruían toda la capa después de cada trazo.
2. Deshacer y rehacer reconstruían toda la capa, aunque el cambio afectara pocos tiles.
3. `onDraw()` podía decodificar PNG de tiles directamente en el hilo visual.
4. La precarga retenía el bloqueo de la superficie mientras leía almacenamiento.
5. La caché recalculaba el tamaño sumando todos los bitmaps repetidamente.
6. El indicador de estado podía provocar actualizaciones de Compose casi en cada frame.

## Estrategia

- Restauración y replay por región.
- Render cached-only en UI.
- Decodificación asíncrona sin bloqueo prolongado.
- Caché con bytes residentes incrementales.
- Actualizaciones técnicas desacopladas del frame.

## Prueba recomendada en Tab S8

- Lienzo 4096 × 4096, 6 capas.
- 100 trazos continuos con lápiz y gouache.
- 20 operaciones de deshacer/rehacer.
- Zoom, giro y desplazamiento durante 2 minutos.
- Guardar, volver a galería y reabrir.
