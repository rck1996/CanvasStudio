# Changelog

## 2.2.0

- Brush Studio 4.0 con dinámicas independientes de tamaño, opacidad, flujo, velocidad e inclinación;
- rango físico completo de inclinación del S Pen e interpolación angular sin saltos;
- Dual Brush no recursivo con punta/grano propios y modos Normal, Multiply y Screen;
- medios húmedos con carga, ataque, sangrado y recogida de pigmento existente;
- presets authored para diferenciar materiales y herramientas de una misma familia;
- bounds ampliados para segunda punta, partículas, blur y sangrado;
- replay filtrado por tile para reducir el costo de trazos largos y gruesos;
- preview reactivo a los nuevos parámetros y controles avanzados dentro del editor;
- tutorial interactivo tablet-first de siete etapas;
- 44 pruebas instrumentadas completas más una regresión de 500 trazos largos de 180 px en Galaxy Tab S8.

## 2.1.0

- corregido el parpadeo completo del dibujo al pasar de un dedo a navegación con dos dedos;
- los trazos táctiles se mantienen en overlay hasta confirmar el gesto y AndroidX Ink queda reservado al stylus;
- política de lienzos nuevos adaptada al heap de la tablet, con máximo de 40 Mpx y 8.192 px por lado;
- estimación previa de megapíxeles, RGBA, tiles y nivel de carga en el diálogo de creación;
- exportaciones PSD y OpenRaster alineadas con el máximo seguro de 40 Mpx;
- regresiones automáticas de multitouch y exportación 8K;
- suite ampliada a 28 pruebas y certificada 20 veces en Galaxy Tab S8;
- workflow de GitHub Actions para lint, APK de prueba, APK release y AAB;
- dependencias AndroidX compatibles actualizadas y versión estable `2.1.0`.

## 2.1.0-beta02

- preview de material ampliado y reactivo a todos los parámetros relevantes del pincel;
- biblioteca alineada al mockup con panel de tres columnas y pestañas General/Dinámicas;
- curva gráfica de presión visible y actualizada en tiempo real;
- importación de puntas bitmap con normalización automática a máscara alfa;
- puntas bitmap incluidas de forma portable en la importación/exportación JSON;
- caché acotada de puntas para mantener estable el rendimiento;
- 25 pruebas instrumentadas aprobadas en una Samsung Galaxy Tab S8.

## 2.1.0-beta01

- selección múltiple de capas con agrupación, movimiento y eliminación conjunta;
- grupos anidados persistentes y jerarquía colapsable;
- guías arrastrables desde las reglas, persistentes por documento;
- ocho pinceles nuevos y cinco modos de fusión modernos;
- intercambio PSD básico del compuesto RGBA;
- propiedades de capa colapsables y selección visual más clara;
- atajos de guardar, rehacer, modo lienzo y restablecer vista;
- prueba automática de ida y vuelta PSD aprobada en una Galaxy Tab S8.

## 2.0.0-beta06

- corregida la pérdida real de trazos antiguos al finalizar un lápiz HB modificado, grueso y largo;
- la reconstrucción regional ahora se expande a los límites exactos de las teselas restauradas y reproduce todos sus comandos;
- el replay queda recortado a las teselas reconstruidas para evitar sobrepintado fuera de la zona invalidada;
- los pinceles lineales con taper o velocidad se rasterizan una sola vez al finalizar, reduciendo 57 % el tiempo de la suite de estrés medida;
- nueva regresión automática con 64 trazos HB de 180 px que atraviesan el documento y conservan trazos centinela antiguos;
- 23 pruebas instrumentadas aprobadas en una Samsung Galaxy Tab S8.

## 2.0.0-beta05

- corregida la vista previa ampliada para que responda inmediatamente a tamaño, opacidad, flujo, dureza, grano y familia;
- eliminado el falso efecto de desaparición al evitar el preview frontal sólido en pinceles translúcidos o texturizados;
- matriz automática de 539 trazos gruesos distribuida entre las 11 familias de render;
- selección invertida persistente en pincel, forma, degradado, relleno, borrado e historial;
- feather raster por tile con transición alfa real y límites de memoria locales;
- snapping directo de líneas y degradados a perspectiva de uno o dos puntos;
- reglas en píxeles o centímetros calculadas desde el DPI y persistidas por proyecto;
- semántica TalkBack ampliada para pinceles, parámetros y capas;
- firma debug estable para automatización reproducible;
- 22 pruebas instrumentadas aprobadas en una Samsung Galaxy Tab S8.

## 2.0.0-beta04

- biblioteca profesional de tres paneles con búsqueda, favoritos y recientes;
- duplicado de presets y renombrado/eliminación de pinceles personalizados;
- importación y exportación de bibliotecas JSON versionadas, con un máximo de 80 presets propios;
- vista previa ampliada del material y controles accesibles en la biblioteca;
- búsqueda automática en el panel cuando un documento contiene seis o más capas;
- expansión y contracción de selecciones en incrementos de 16 px;
- reglas documentales opcionales y ajuste angular de 15° para líneas y degradados;
- cuadrados y círculos exactos cuando el ajuste angular está activo;
- semántica de selección para herramientas y pestañas principales;
- suite instrumentada ampliada a 17 pruebas, validada en una Galaxy Tab S8;
- captura real actualizada de la biblioteca añadida al README.

## 2.0.0-beta03

- corregida la desaparición visual de trazos al retirar demasiado pronto el front buffer de AndroidX Ink;
- entrega segura de dos frames entre la previsualización de baja latencia y el raster tiled;
- prueba instrumentada específica para impedir regresiones del handoff;
- setters de cuadrícula, simetría y guías idempotentes para evitar invalidaciones al cambiar pincel;
- la biblioteca vuelve al inicio al tocar Pinceles o elegir un preset;
- prueba ADB ampliada con captura intermedia, verificación de retención y pinceles gruesos;
- 14 pruebas instrumentadas ejecutadas en tres ciclos: 600 trazos persistidos;
- 120 trazos de Carboncillo a 159 px conservados antes y después de reiniciar en una Galaxy Tab S8.

## Fase 6 · en desarrollo

- rechazo explícito de eventos de palma cancelados;
- curva editable de presión;
- HUD interactivo de tamaño, opacidad y color;
- búsqueda de pinceles y diez presets nuevos;
- objetivos táctiles ampliados;
- suite instrumentada ampliada a catorce pruebas.

## 2.0.0-beta01

- identidad final aplicada al launcher, splash, documentación y galería;
- contraste del logo ajustado y verificado en una Galaxy Tab S8;
- versiones locales automáticas, limitadas y restaurables de forma atómica;
- onboarding inicial y ayuda contextual del editor;
- perfiles persistentes de atajos por letras o números;
- reglas explícitas de backup y transferencia de Android;
- configuración de firma externa y generación release APK/AAB;
- suite ampliada a ocho pruebas y 4.000 trazos gruesos verificados en dispositivo real.

## Fase 5 · estabilidad en desarrollo

- Autoguardado inmediato al enviar el editor a segundo plano o retirar su composición.
- Recuperación atómica desde `project.properties.tmp` cuando un guardado fue interrumpido.
- Restauración desde `project.properties.bak` cuando la metadata principal está dañada.
- Suite instrumentada de cuatro pruebas para trazos tiled, prefetch y recuperación de metadata.
- Nuevo icono adaptativo, iconos por densidad y logotipo oficial de Canvas Studio.

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
