# Changelog

## Fase 4.1 · 1.6.0

- Grupos de capas de un nivel con visibilidad, opacidad, colapso y persistencia.
- Capas nuevas y duplicadas heredan el grupo activo.
- Máscaras raster dispersas por tiles, editables con Pincel/Borrador.
- Deshacer/rehacer separado para contenido y máscara.
- Máscaras activables, desactivables y eliminables sin destruir la capa.
- Guías de perspectiva con puntos de fuga arrastrables y posiciones persistentes.
- Modo de edición de perspectiva consume el gesto para evitar pintura accidental.
- Formato de proyecto v7, compatible con lectura v2-v6.
- Composición normal sin `saveLayer`; superficies temporales solo para máscara o clipping.
- El clipping toma en cuenta la máscara raster de la capa base.
- Conservado el hotfix anti-overflow de 1.5.3.

## Phase 4 Crash Hotfix 1.5.3

- Corregido cierre inmediato al terminar un trazo en `renderActiveStrokePending`.
- Eliminada la suma susceptible de overflow entre el índice activo e `Int.MAX_VALUE`.
- Vaciado final del lote limitado por la cantidad real de segmentos restantes.
- Capacidad del buffer temporal validada antes de crear `ArrayList`.
- Mantiene compatibilidad de proyectos v6 y todas las optimizaciones de la 1.5.2.

## Phase 4 Low-Latency Hotfix 1.5.2

- Eliminado el replay regional al finalizar pinceles basados en stamps; evita crecimiento cuadrático con trazos superpuestos.
- Entrada del S Pen procesada por lotes sincronizados con frame.
- Muestreo adaptativo y límites de densidad para Gouache, Pintura suave, Aerógrafo, Carboncillo y Tiza.
- Reutilización de `BlurMaskFilter` y `PorterDuffXfermode` durante el trazo.
- Autoguardado desacoplado: debounce de 3 segundos y miniatura omitida durante guardados automáticos.
- Codificación PNG de tiles fuera del bloqueo de la superficie.
- Evicción LRU sin compresión síncrona en el hilo visual.
- Formato de proyecto v6 y renderer `low-latency-visible-tiles-lru`.

## Phase 4 Performance Hotfix 1.5.1

- Reconstrucción regional de tiles al finalizar trazos con taper o respuesta a velocidad.
- Undo/redo regional en vez de reconstrucción completa de capa.
- Render del viewport con tiles residentes; la lectura de disco queda fuera del hilo visual.
- Precarga sin mantener el bloqueo de superficie durante la decodificación.
- Contabilidad O(1) de memoria residente en caché.
- Menos trabajo por frame y menor presupuesto de caché para reducir pausas de GC.
- Actualización del indicador del motor limitada a intervalos de 650 ms.


## Phase 4 Test Build 1.5.0

Cierre de fase 3C y entrega integrada de fase 4 para pruebas.

### Fase 3C

- Precarga asíncrona de tiles cercanos al viewport.
- Cancelación generacional de solicitudes obsoletas de precarga.
- Superficie dispersa y caché LRU mantenidas sin bloquear la composición más de lo necesario.
- Guardado, relleno y exportación pesada ejecutados fuera del hilo visual.
- Composición hardware del lienzo y actualizaciones sincronizadas con frame.

### Selección y transformación

- Selección rectangular, elíptica y lazo.
- Selecciones persistentes durante la sesión.
- Clip de pinceles, formas, relleno y degradado a la selección.
- Movimiento con un dedo y escala/rotación con dos dedos.
- Volteo horizontal/vertical y eliminación.
- Comandos raster no destructivos con undo/redo.

### Herramientas profesionales

- Relleno contiguo tolerante.
- Degradado lineal color-transparencia.
- Simetría radial 4/8.
- Guías de perspectiva de uno y dos puntos.
- Importación de imagen como capa.

### Capas y compatibilidad

- Bloqueo alfa.
- Clipping respecto de la capa inferior.
- Modos Oscurecer y Aclarar.
- Persistencia v5 de propiedades de capa.
- Lectura de formatos v2-v4.

### Exportación

- OpenRaster con stack XML, capas PNG, merged image y thumbnail.
- PNG aplanado conserva clipping y modos de fusión disponibles.

## Premium Alpha 1.2.0

Fase 3B: superficie raster dispersa, carga bajo demanda y caché LRU.

## Premium Alpha 1.1.0

Fase 3A: almacenamiento incremental por tiles y seguridad de documentos.

## Premium Alpha 1.0.0

Cierre de fase 2: experiencia premium inicial.
