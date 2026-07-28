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

La suite instrumentada incluye ahora catorce pruebas. El primer bloque fue verificado
en una Galaxy Tab S8 con tres ciclos consecutivos y 600 trazos gruesos persistidos.

El front buffer de AndroidX Ink se conserva durante dos frames presentados por el
renderer tiled. Esta entrega evita huecos visuales al completar ráfagas de trazos y
queda protegida por dos pruebas instrumentadas específicas.

## Siguientes bloques

- paneles y capas;
- administración e intercambio de pinceles;
- referencias y reglas;
- operaciones avanzadas de selección;
- accesibilidad y pruebas finales de carga.
