# Crash Hotfix 1.5.3

## Síntoma

La aplicación se cerraba al levantar el S Pen, incluso después de un solo trazo.

## Causa

`finishStroke()` solicitaba procesar todos los segmentos pendientes mediante `renderActiveStrokePending(Int.MAX_VALUE)`. El renderer calculaba el final con una suma de enteros. Al existir al menos un punto ya procesado, esa suma podía superar `Int.MAX_VALUE`, envolver a un número negativo y terminar en `ArrayList` con capacidad negativa.

## Corrección

El renderer compara el límite solicitado con la cantidad real de segmentos restantes antes de sumar. Cuando el límite cubre todo lo pendiente, usa directamente `points.size`. También valida la capacidad mínima del buffer temporal.

## Regresión cubierta

- Un solo punto pendiente.
- Varios puntos pendientes.
- Solicitud normal por frame.
- Solicitud de vaciado con `Int.MAX_VALUE`.
- Índice situado cerca del final de la lista.
