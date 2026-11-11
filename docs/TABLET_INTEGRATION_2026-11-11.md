# Integración del editor para tablets · 11 de noviembre de 2026

La rama de integración parte de [#8](https://github.com/rck1996/CanvasStudio/pull/8) en `401e717`. Se verificó que los commits finales de [#6](https://github.com/rck1996/CanvasStudio/pull/6) y [#7](https://github.com/rck1996/CanvasStudio/pull/7) son ancestros. El #8 agrega regresiones de recorte, manejo de palma y exportación sobre ese trabajo.

GitHub registró la [regresión seleccionada de emulador](https://github.com/rck1996/CanvasStudio/actions/runs/42947984666) como exitosa para `401e717`. En la integración local pasaron `check`, `assembleDebug` y `assembleDebugAndroidTest`. Los informes JVM de debug y release suman 92 ejecuciones sin fallos. En el AVD Pixel C con Android 15 pasaron 21 de 21 pruebas enfocadas en exportación, composición, entrada, QuickShape y controles de pincel.

La ejecución aislada de 500 trazos largos terminó en 87,494 s en el mismo AVD. Conservó las marcas de referencia, con resultado JUnit exitoso y salida ADB 0. Este tiempo corresponde a la ejecución instrumentada y no mide la latencia de entrada ni los cuadros de una sesión interactiva.

En la suite completa, el caso de 500 trazos de lápiz con undo, guardado y reapertura tardó 65,444 s. Las métricas internas acumularon 36,781 s de replay, 3.128 comandos examinados y reproducidos sobre 16 tiles y cero fallbacks del índice. El caso de 200 trazos con pinceles costosos tardó 16,593 s. La primera cifra identifica el replay como candidato a perfilar en hardware. Los casos sintéticos no produjeron muestras de tiempos de cuadro.

La primera suite estándar terminó con 227 de 228 pruebas aprobadas. El único fallo llamaba directamente a la API de bitmap completo para el caso 8K. Tras migrar la prueba de seis capas a la ruta real de PNG, se recompiló y reinstaló el APK de instrumentación y aprobaron las 7 pruebas de exportación. La suite estándar no se volvió a ejecutar de principio a fin con ese cambio de prueba. Los logs locales se conservan en `app/build/canvasstudio-full-adb.log`, `app/build/canvasstudio-final-export-adb.log` y `app/build/canvasstudio-500-strokes-adb.log`.

## Cambios de integración

- PNG usa el compositor de snapshots y conserva la preparación en un archivo temporal y la comprobación de revisiones antes de publicar. La banda de 512 filas ocupa 15 MiB en un documento de 7680 px de ancho, frente a unos 127 MiB para el bitmap RGBA 7680 × 4320. Son tamaños de buffers calculados. El pico real incluye capas temporales del compositor, tiles y compresión.
- Cada banda decodifica o copia únicamente los tiles de su fila. Miniaturas y PNG comparten la composición de opacidad, máscara, recorte y modo de fusión. PSD compuesto y ORA conservan sus rutas actuales.
- El arranque abre la galería y el menú del editor omite el tutorial retirado. Los ajustes de galería anteriores permanecen en la rama de trabajo original y requieren adaptación a la galería consolidada.

## Dirección del editor

- Lienzo como superficie principal. Tamaño y opacidad accesibles durante el dibujo, panel de capas contextual y rueda de seis acciones con selección direccional. Comprobar que al reconfigurar un gesto se mantienen accesibles las acciones y que el gesto no se confunde con pintar o navegar. [QuickMenu de Procreate](https://help.procreate.com/procreate/handbook/interface-gestures/quickmenu)
- En las capturas de la paleta flotante, el trazo HB tiene poco contraste sobre la muestra oscura. La barra lateral permanente y los diales inferiores ocupan espacio incluso al pintar. Revisar contraste y superficie libre con el editor normal en horizontal y vertical. Las capturas del workflow montan el editor directamente y la posición de las barras del sistema requiere comprobar el flujo real de la app. [Capturas del PR #8](https://github.com/rck1996/CanvasStudio/actions/runs/42947978898)
- Pinceles con comportamiento distinguible por punta, grano, presión e inclinación. Comparar trazos equivalentes en el lienzo y en Brush Studio antes de alterar espaciado, opacidad o textura para ganar rendimiento. [Brush Studio de Procreate](https://help.procreate.com/procreate/handbook/5.3/brushes/brush-studio-settings)
- Mantener la vista previa de entrada independiente de la publicación del raster. Ink API permite evaluar la autoría de baja latencia por separado del motor de pinceles. El raster asíncrono de #7 sigue limitado a debug; medir el coste de preparar, copiar y publicar tiles antes de evaluar su uso general. [Módulos de Ink API](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-modules)

## Protocolo de rendimiento

Comparar una compilación de referencia y una candidata en la misma Tab S8, con la misma frecuencia de pantalla, preset, tamaño, presión y documento. Registrar al menos cinco iteraciones para trazo HB largo, acuarela y óleo a 180 px, paneo/zoom con dos dedos y undo/redo tras expulsión. Separar la primera ejecución de las repeticiones con caché caliente. Ejecutar también la retención de 500 trazos y exportación 8K con capas, máscara y recorte.

En Perfetto correlacionar entrada, hilo principal, RenderThread y trabajo de raster/IO. Registrar p50/p95/p99 de duración CPU y exceso sobre el plazo de frame, tiempo de confirmación de trazo, memoria pico y retención de píxeles. El tiempo de la vista previa interna mide CPU y no incluye la llegada del píxel al panel. Medir esa latencia por separado con grabación de alta velocidad cuando se compare S Pen con otra app o backend. [FrameTimingMetric de Android](https://developer.android.com/topic/performance/views/benchmarking/macrobenchmark-metrics-views)

Priorizar el tramo que domine el perfil. Conservar el raster Canvas estable mientras la ruta experimental no supere las mismas pruebas de retención, replay, máscara y exportación en hardware.

## Seguimiento del S Pen durante el trazo

En la capa superior normal y opaca, el editor dibuja una vista previa desde la última muestra rasterizada hasta el extremo de la cola. Las muestras usan la estabilización del pincel. La vista previa desaparece conforme los tiles definitivos avanzan y no modifica el historial ni los archivos. Las capas con máscara activa, recorte, grupo, fusión especial o selección mantienen su composición habitual.

El replay calcula el margen del pincel una vez por comando y tile. Las pruebas de intersección conservan las mismas desigualdades que los rectángulos anteriores y evitan asignar un rectángulo por segmento. La mejora de latencia de punta a pantalla se debe medir con el S Pen físico.

Tab S8 (SM-X700): se reinstalaron los APK de app e instrumentación y pasaron las 19 pruebas enfocadas en 130,473 s, con salida ADB 0. Incluyen 500 trazos largos mixtos, HB modificado, equivalencia del raster, vista previa, rechazo de palma, navegación y aislamiento de contenido y máscara en el índice. El tiempo corresponde a la suite completa de esta tanda. La app quedó abierta para la comprobación manual del gesto. Registro local: `app/build/tab-s8-stylus-regression.log`.

## Pulido del espacio de trabajo

El selector del motor de desarrollo se movió del lienzo a **Más opciones → Motor de renderizado · diagnóstico**. La vista previa de AndroidX Ink sigue al identificador del S Pen que inició el trazo. Los contactos secundarios no la cierran y el editor usa su vista previa local cuando no hay una sesión Ink activa.

En la Tab S8 pasaron las 12 pruebas seleccionadas de esta revisión en 12,186 s, con salida ADB 0. Cubren acceso al diagnóstico, ausencia del selector sobre el lienzo, diales, paleta flotante, navegación y vista previa. El gesto del S Pen con palma apoyada aún requiere comprobación manual de la capa visual de AndroidX Ink. Registro local: `app/build/tab-s8-workspace-regression.log`.

## Galería, capas, pinceles y acciones

- La galería conserva favoritos, colección y etiquetas por proyecto en las preferencias locales. Puede ordenar por favoritos y buscar por colección o etiqueta. Los detalles muestran dimensiones, DPI y coste RGBA estimado. La papelera y el historial de versiones mantienen sus flujos.
- El lienzo ofrece **Ocultar**, **Recuperar** y **Listo** durante la edición de máscara. Elegir una acción de pintura vuelve a mostrar el resultado si se estaba comparando con el original. Quitar una máscara pide confirmación. El inspector identifica por nombre la base del recorte.
- **Más opciones → Buscar acciones** ejecuta comandos por nombre o descripción. El mismo buscador permite asignar acciones a la rueda. Deshacer, rehacer, ajustar el lienzo y modo lienzo están disponibles sin cambiar las posiciones guardadas.
- Las muestras usan el raster de pinceles Canvas fuera del hilo principal y un bitmap de hasta 768 píxeles por lado. Conservan la última imagen del mismo preset mientras se calcula el ajuste siguiente. La trayectoria de muestra conserva presión, inclinación y cadencia. Las muestras se generan con tinta blanca y fondo oscuro para comparar su comportamiento.

Pasaron `check`, `assembleDebug` y `assembleDebugAndroidTest`. En la Tab S8 pasaron 32 de 32 pruebas seleccionadas en 18,504 s. Se verificaron la búsqueda y ejecución de acciones, salida de edición de máscara, orden y búsqueda de galería, papelera, historial, muestras de pincel, diales y multitouch. Registro local: `app/build/tab-s8-studio-regression.log`.

## Candidata 2.6.0

- La referencia 2.5.0-debug en Tab S8 con Android 16 pasó 235 de 236 pruebas estándar. La única falla fue el tiempo de 156,618 s para 500 trazos gruesos de distintos materiales. La comprobación de retención se completó. El límite sigue siendo 120 s. Registro: `app/build/tab-s8-prod-baseline.log`.
- OpenRaster genera por bandas cada capa y la composición. La miniatura se compone directamente a 256 px. Las máscaras se aplican a los píxeles de cada capa y el archivo XML conserva opacidad, visibilidad y modo de fusión. El límite de 40 MP y la restricción de recortes no representables siguen vigentes. PSD compuesto conserva su ruta de bitmap completo.
- El raster evita asignar rectángulos para los óvalos, reutiliza el filtro del último color y actualiza la matriz de grano fijo únicamente cuando cambia la escala. Esta optimización conservó las huellas de los 14 pinceles de referencia en la Tab S8. Por sí sola, la prueba mixta de 500 trazos tardó 154,040 s.
- La revisión siguiente reduce el solapamiento de sellos de acuarela, óleo y cerdas por encima de 96 px, sin reducir el detalle de cada sello. Esto puede cambiar la densidad de estos pinceles grandes. La revisión requiere validar la retención y la continuidad visual.
- El APK y AAB release usan el código de versión 33. En un árbol de trabajo separado, Gradle puede leer la configuración de firma local mediante `-PcanvasSigningProperties=<ruta-al-archivo>`. El keystore y sus contraseñas se mantienen fuera del repositorio.
- El runner de tablet exige resultado JUnit exitoso, pruebas ejecutadas y código de proceso cero. Guarda cada ejecución en un directorio de evidencia separado.

En el emulador dedicado, el APK release firmado abrió, creó un proyecto de prueba, aceptó un trazo y reabrió el proyecto desde la galería tras guardar. Se regeneró la pareja APK/AAB para el ajuste de espaciado, se verificaron ambas firmas y el APK actualizado volvió a arrancar. Estas comprobaciones cubren instalación y arranque de la versión minificada.
