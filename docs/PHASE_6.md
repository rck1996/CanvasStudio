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

## Siguientes bloques

- feather e inversión de selecciones;
- reglas arrastrables y snapping directo a perspectiva;
- selección múltiple de capas y grupos anidados;
- puntas/texturas bitmap importables;
- accesibilidad completa con TalkBack y pruebas finales de carga.
