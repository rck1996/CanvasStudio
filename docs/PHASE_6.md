# Fase 6 · experiencia creativa

## Bloque 1: entrada y pinceles

- Rechazo explícito de eventos de palma cancelados en Android 13 o superior.
- Reversión del trazo temporal cuando Android cancela un contacto accidental.
- Curva de presión configurable entre `0.35` y `2.5`.
- Controles rápidos interactivos de tamaño y opacidad sobre el lienzo.
- Búsqueda por nombre o categoría en la biblioteca.
- Objetivos táctiles mínimos de 48 dp en la barra de herramientas.
- Diez pinceles adicionales:
  - Lápiz azul;
  - Lápiz de color;
  - Entintado manga;
  - Plumilla G;
  - Rotulador plano;
  - Aerógrafo duro;
  - Pincel seco;
  - Pincel de cerdas;
  - Acuarela granulada;
  - Óleo espeso.

La suite instrumentada incluye ahora diecisiete pruebas. El primer bloque fue verificado
en una Galaxy Tab S8 con tres ciclos consecutivos y 600 trazos gruesos persistidos.

El front buffer de AndroidX Ink se conserva durante dos frames presentados por el
renderer tiled. Esta entrega evita huecos visuales al completar ráfagas de trazos y
queda protegida por dos pruebas instrumentadas específicas.

## Bloque 2: flujo creativo y administración

- biblioteca de tres paneles inspirada en el mockup de producto;
- favoritos y lista de pinceles recientes persistentes;
- duplicado, renombrado y eliminación de presets;
- importación/exportación JSON con validación de formato;
- búsqueda de capas para documentos complejos;
- expansión y contracción de selecciones;
- reglas documentales y ajuste angular opcionales;
- semántica accesible en herramientas y pestañas;
- 17 pruebas instrumentadas aprobadas en una Galaxy Tab S8.

## Bloque 3: precisión profesional

- inversión de selecciones conservada por los comandos de historial;
- feather raster configurable entre 0, 8, 16 y 32 px desde la barra contextual;
- reglas documentales en píxeles o centímetros según DPI;
- snapping de líneas y degradados al punto de fuga más cercano;
- persistencia de reglas, unidades y modos de ajuste dentro del proyecto.

## Bloque 4: cierre beta

- preview de pincel reactivo a todos los parámetros visibles;
- preview frontal AndroidX Ink limitado a familias visualmente compatibles;
- semántica TalkBack para presets, parámetros y capas;
- validación de tablet redimensionable en una Galaxy Tab S8;
- 22 pruebas instrumentadas aprobadas;
- matriz de 539 trazos gruesos: 49 por cada una de las 11 familias.

La fase 6 queda cerrada. Grupos anidados, selección múltiple de capas, reglas arrastrables
y recursos bitmap importables pasan a la fase 7.
