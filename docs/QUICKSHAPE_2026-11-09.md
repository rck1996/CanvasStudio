# QuickShape (líneas) · 2026-11-09

Canvas Studio incorpora un primer paso optativo hacia la interacción de figuras de Procreate.

## Alcance implementado

- Activar/desactivar en la barra del editor con varita; persiste en las preferencias del espacio de trabajo.
- Dibujar una línea con el pincel y **mantener el lápiz quieto al terminar durante ≥420 ms**; al levantarlo, el trazo se convierte en un comando de línea preciso y editable mediante el historial de deshacer/rehacer.
- Reconocimiento conservador con pausa medida sobre coordenadas **reales** del lápiz, no sobre muestras suavizadas: soporta jitter y el retraso geométrico introducido por estabilización. Usa umbral de distancia, desviación máxima y penalización de retrazos; los arcos, zigzags y trazos liberados normalmente quedan como pincel.
- El motor usa el preview diferido existente mientras QuickShape está activo para no dejar píxeles rasterizados del trazo original antes de la conversión. Si el pincel es compatible, mantiene la vista previa de baja latencia con AndroidX Ink.
- Los ajustes de simetría, selección, máscara y tamaño de pincel pasan al comando final.

## Rendimiento

La detección de línea se ejecuta únicamente al levantar el lápiz (recorrido O(n) de muestras), sin cálculos geométricos por fotograma. Cuando QuickShape está desactivado, el recorrido inmediato del lápiz y del renderer queda igual que antes.

El modo está desactivado inicialmente para minimizar sorpresas y conservar la ruta de raster rápido. Queda pendiente medir el coste real de la vista previa diferida y el guardado sobre tablet con S Pen.

## Validación y riesgos

`QuickShapeRecognizerTest` comprueba líneas mantenidas, jitter, curvas, retrazos, lifts normales y coordenadas inválidas.

No incluye todavía elipses, rectángulos, ajuste a 15°, edición posterior de nodos ni transformaciones de perspectiva. No se presenta como paridad con Procreate.

### Vista previa de baja asignación

En la misma iteración, la vista previa diferida se optimizó con una instancia de
`Paint` y un buffer de cuatro floats reutilizados por todos los segmentos de un
fotograma. Se evita crear arreglos y objetos de muestras transformadas por punto,
conservando las operaciones `Matrix.mapPoints` y el cálculo de presión originales.
