# Fase 2: historial escalable

## Arquitectura

`HistorySurfaceKey` identifica de forma independiente el contenido y la mascara de cada capa.
`TileCommandIndex` mantiene relaciones reversibles entre superficie, `TileStorage.Key`, comando y
posicion historica. La reconstruccion regional consulta IDs indexados y resuelve los comandos en un
registro O(1); el escaneo geometrico del historial completo no es la ruta normal.

`TileCheckpointStore` es una cache LRU de snapshots inmutables por superficie, tile y posicion. La
ruta de reconstruccion restaura el checkpoint mas cercano que no supera el cursor y reproduce solo
los comandos indexados posteriores. Sin checkpoint restaura el tile base y conserva el uso del indice.

## Politica y memoria

Se considera un checkpoint usando costo local: cantidad de comandos, costo estimado de replay o
duracion medida. Los umbrales son configurables para pruebas. El presupuesto predeterminado es 3,5 %
del heap, limitado a 4-32 MiB, separado del presupuesto de tiles activos. Los snapshots no se guardan
en proyectos y se eliminan al truncar ramas, borrar superficies, cambiar documento o cerrar la vista.

## Metricas debug

Se exponen consultas y fallbacks del indice, entradas, comandos examinados/reproducidos, tiles
reconstruidos, hits/misses/creaciones/expulsiones de checkpoints, tiempos de crear/restaurar, bytes,
presupuesto y comandos posteriores al checkpoint.

El unico fallback recorre la lista de la superficie si un ID indexado no aparece en el registro de
comandos (estado inconsistente o sesion anterior a la inicializacion). Incrementa `indexFallbacks`;
las pruebas certificadas observaron cero usos.

## Verificacion Tab S8

En el escenario distribuido de tres comandos, undo examino y reprodujo uno (el unico comando previo
del tile), en vez de tres. Un redo repetido obtuvo checkpoint hit y reprodujo cero comandos. La
certificacion usa igualdad exacta de bitmap, contenido y mascara aislados, recarga tras flush/eviction,
la suite completa y la prueba separada de 500 trazos largos.
