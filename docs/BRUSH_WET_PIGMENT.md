# Motor de pinceles · Transporte de pigmento húmedo

La acuarela y el óleo pueden arrastrar pigmento de una zona pintada a una zona transparente. La punta recoge parte del color disponible y vuelve gradualmente al color cargado. La distancia recorrida, el tamaño, la humedad y el arrastre del pincel determinan cuánto dura la mezcla. Un punto repetido no hace que el pincel recoja más pigmento por el mero hecho de que el dispositivo envíe más eventos.

El flujo no cambia el comportamiento de un pincel seco, el borrador ni las máscaras. Sigue tomando pigmento de la capa activa. La mezcla interpola en RGB lineal y conserva el alfa del color cargado; la opacidad, el flujo, las puntas y el grano siguen en el raster de Canvas. Es una aproximación artística, no una simulación de pigmentos físicos ni un difuminador independiente.

## Reproducibilidad

- Al confirmar el trazo, se resuelve la mezcla con los píxeles previos a la escritura. En simetría, cada copia toma sus muestras antes de que alguna pinte.
- El resultado vive en una traza de color de la sesión. Las pinceladas sin mezcla visible comparten una traza vacía. Deshacer, rehacer o reconstruir un tile no vuelve a consultar el pigmento de una zona que pudo haberse modificado después.
- El papel de prueba usa el mismo transporte de color y el mismo raster. El motor experimental Vulkan deriva estos comandos a Canvas.
- El proyecto sigue guardando los píxeles de sus capas. No se cambia el formato y los proyectos existentes no se vuelven a pintar al abrirlos. No se guarda el historial de la sesión.

## Referencias

- [Krita · Color Smudge Brush Engine](https://docs.krita.org/en/reference_manual/brushes/brush_engines/color_smudge_engine.html): separar el color aplicado de la longitud de arrastre y el modo de mezcla.
- [Infinite Painter · controles de pintura](https://docs.infinitestudio.art/painter/brushes/settings/paint/?lang=es): distinguir la aplicación de color y la interacción con la pintura existente.

El parecido al pintar con un S Pen físico se evaluará aparte. La prueba automática verifica el transporte a través de un límite de tiles, el desvanecimiento por distancia y la estabilidad del historial.

## Validación · 12 de noviembre de 2026

- `check`, `assembleDebug` y `assembleDebugAndroidTest` pasaron. Las cinco pruebas unitarias nuevas cubren densidad de muestras, desvanecimiento, transparencia, reinicio del depósito y exclusión de pinceles secos y borradores.
- Galaxy Tab S8 SM-X700, Android 16: 23 pruebas dirigidas en 0,954 s. La regresión de mezcla comparó el compuesto con igualdad exacta de píxeles al deshacer y rehacer, sin recurrir al replay de respaldo.
- La prueba separada de 500 trazos largos, con pinceles húmedos y de doble punta, pasó en 71,036 s. Conservó las cuatro marcas de control y cumplió el límite de 120 s. Ambas tandas concluyeron con salida ADB 0.
- Registros completos: `app/build/tab-s8-wet-pigment-targeted.log` y `app/build/tab-s8-wet-pigment-500.log`. Los APK de pruebas y app se reinstalaron con reemplazo, sin borrar datos.
