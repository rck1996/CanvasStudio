# Canvas Studio 2.6.0

Actualización para tablets Android centrada en la continuidad del lienzo, la pintura por materiales y el intercambio de documentos editables.

## Qué cambia

- El editor evita que el suavizado del borde de cada tile deje pasar el fondo. La corrección no modifica el documento ni la exportación a tamaño nativo.
- Marcadores con depósito regular y translúcido; carboncillo de grano más fino; óleo plano, cerda impasto y acuarela con comportamientos diferenciados. Activa **Mostrar óleo y acuarela (en prueba)** desde **Pinceles → Biblioteca**.
- Panel de pinceles con muestra reactiva y papel de prueba. Tamaño y opacidad quedan fuera del área de dibujo.
- Galería con favoritos, colecciones, etiquetas, papelera e historial de versiones. Máscaras con acciones **Ocultar**, **Recuperar** y **Listo**.
- Formas precisas agrupadas en un solo control. Búsqueda de acciones y rueda Quick Access configurable.
- PNG, OpenRaster y PSD exportan por bandas. PSD intercambia capas raster RGB de 8 bits; no preserva máscaras editables, grupos ni perfiles CMYK/ICC.
- La app abre la galería y ya no ofrece el tutorial interactivo anterior.

## Compatibilidad

- APK de instalación directa para tablets Android 8.0 o superior. Los teléfonos no están soportados. AAB para distribución; no es una publicación en Google Play.
- `com.orbyte.canvasstudio`, versión `2.6.0`, código `33`. Documentos Canvas Studio v7.
- Actualiza sobre la app existente con una firma compatible. **No desinstales para forzar la actualización**: los proyectos se guardan localmente. Antes de reemplazar una instalación incompatible, exporta y comprueba tus copias.
- Canvas/Bitmap sigue siendo el motor de producción. El historial de deshacer/rehacer es de sesión.

## Validación

- **Galaxy Tab S8**, Android 16: 131 pruebas instrumentadas seleccionadas en 64,558 s; resultado JUnit correcto y salida ADB 0.
- Suite estándar en la misma tablet: **306/306**, 282,149 s, salida ADB 0. Excluye la sesión prolongada Vulkan de diez minutos.
- Ejecuciones aisladas en la Tab S8: **500 trazos largos** en 44,519 s y **200 trazos gruesos con expulsión y recarga de tiles** en 1,084 s. Ambas conservaron sus marcas de control y terminaron con salida ADB 0.
- El caso de montaña reprodujo antes del arreglo una diferencia de 18 niveles en rojo en la unión de `x = 512`. La misma prueba pasó tras la corrección en el editor al 78,97 %.
- Se verificaron trazos visibles de seis pinceles seleccionados desde la biblioteca, con presión simulada. El agarre, la latencia punta-a-pantalla y el acabado a mano alzada no se midieron con un S Pen físico.
- [Capturas de la prueba en dispositivo](SCREENSHOTS.md) · [Intercambio PSD](PSD_LAYER_EXCHANGE.md) · [Integración y arquitectura](TABLET_INTEGRATION_2026-11-11.md).

## Artefactos

| Archivo | Bytes | SHA-256 |
|---|---:|---|
| `CanvasStudio-2.6.0.apk` | 9.405.733 | `7F437B787FD212F69CD0CCCAFD1E7A51ABD8949BDDB35777F769FD08AFA79EEE` |
| `CanvasStudio-2.6.0.aab` | 8.533.762 | `24893D911806AD54197C2BB530173A1944BE4D94668DFAE7D8F68A8AB3C456E3` |

Gradle: `check assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease` superados. Firma APK v2 y firma JAR del AAB verificadas. Las pruebas instrumentadas se ejecutaron con los APK de debug correspondientes al código de esta versión.
