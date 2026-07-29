# Fase 9.1 — Brush Studio 4.0

## Objetivo

Convertir el motor 3.0 en un sistema de pinceles profesional y editable, con respuestas
independientes del S Pen, una segunda punta, medios húmedos interactivos, presets realmente
diferenciados y una introducción guiada dentro de la aplicación.

CanvasStudio no copia recursos ni formatos propietarios. La referencia de producto es alcanzar
una profundidad de configuración y una calidad visual comparables en Android, respetando las
limitaciones del renderer por tiles y la Galaxy Tab S8.

## Motor

### Dinámicas independientes

`BrushDynamicsProfile` separa:

- presión → tamaño;
- presión → opacidad;
- presión → flujo;
- velocidad → tamaño;
- velocidad → opacidad;
- inclinación → tamaño;
- inclinación → opacidad;
- umbral físico de inclinación.

`AXIS_TILT` se normaliza desde radianes (`0..π/2`), por lo que el último tercio del recorrido
del S Pen ya no queda saturado. La orientación interpola por el camino angular más corto.

### Dual Brush

`DualBrushProfile` añade una segunda punta no recursiva con:

- forma, redondez, rotación y cantidad propias;
- grano independiente;
- escala, opacidad, offset y dispersión;
- combinación Normal, Multiply o Screen.

El perfil se serializa en la biblioteca JSON y conserva valores por defecto para pinceles
creados con versiones anteriores.

### Medios húmedos

`BrushRenderProfile` incorpora:

- `charge`: carga inicial;
- `attack`: conservación del depósito durante el trazo;
- `bleed`: expansión húmeda;
- `colorPickup`: mezcla con el pixel existente.

La recogida de color usa muestreo puntual directo del tile, sin crear bitmaps temporales, y
mezcla canales en espacio lineal aproximado. Los colores se capturan antes de rasterizar el
comando para que el resultado sea determinista durante rebuild y replay.

## Integridad y rendimiento

- `maximumBrushExtent()` contempla inclinación, partículas, scatter, blur, sangrado y segunda
  punta al seleccionar y marcar tiles.
- El índice de estampa es global por segmento del trazo, evitando repetir jitter y moving grain.
- La primera estampa de replay conserva la orientación original.
- `SparseTileSurface.drawPerTile()` entrega el rectángulo del tile al rasterizador.
- El replay descarta segmentos que no intersectan el tile actual. Así un trazo largo deja de
  procesarse completo una vez por cada tile.

## Presets authored

La familia deja de ser el único descriptor. Los perfiles distinguen explícitamente:

- 2H y portaminas: duros, claros y con inclinación reducida;
- 6B y grafito lateral: mayor depósito, textura e inclinación;
- tinta técnica: ancho estable;
- plumillas y manga: curva de tamaño sensible;
- caligrafía y sumi: punta chisel orientada por stylus;
- acuarela granulada: mayor grano, sangrado y pickup;
- redondo húmedo: carga sostenida y mezcla suave;
- óleo e impasto: cerdas, arrastre y segunda punta propios.

## Editor y tutorial

El preview responde a curvas, velocidad, inclinación, carga, ataque y Dual Brush.

El editor expone controles para:

- segunda punta, escala, opacidad y dispersión;
- dilución, carga, ataque, sangrado y recogida;
- curvas separadas de tamaño, opacidad y flujo;
- velocidad de tamaño/opacidad y umbral de inclinación.

El tutorial integrado contiene siete etapas: biblioteca, S Pen, preview, parámetros, gestos y
práctica guiada. Se muestra en la primera ejecución de 2.2.0 y queda disponible desde
**Más opciones → Tutorial de pinceles**.

## Validación en Samsung Galaxy Tab S8

Dispositivo: Samsung SM-X700 por USB.

- compilación Kotlin principal y de pruebas: correcta;
- suite instrumentada: 44/44 antes de añadir la carga final;
- prueba adicional: 500 trazos largos de 180 px alternando 6B, carboncillo, cerdas, acuarela
  granulada e impasto;
- centinelas antiguos en tiles compartidos: 4/4 retenidos;
- tutorial: 5/5 pruebas de estado y navegación;
- variante masiva: dentro del límite automatizado de 120 segundos.

La suite queda automatizada para repetirse sin intervención manual. Tras finalizar, deben
desinstalarse `com.orbyte.canvasstudio.debug` y `com.orbyte.canvasstudio.debug.test`, y conservar
solo `com.orbyte.canvasstudio`.

## Referencias

- https://help.procreate.com/procreate/handbook/5.3/brushes/brush-studio
- https://help.procreate.com/procreate/handbook/5.3/brushes/brush-studio-settings
- https://help.procreate.com/procreate/handbook/brushes/dual-brush
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input
- https://developer.android.com/reference/android/view/MotionEvent
- https://docs.krita.org/en/reference_manual/brushes/brush_engines/pixel_brush_engine.html

