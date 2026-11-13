# QuickShape · Ajuste en vivo

Activa **Más opciones → Activar QuickShape** y usa el pincel. Dibuja una línea o acércate al punto de partida al dibujar un círculo, óvalo, rectángulo o triángulo. Mantén el lápiz apoyado hasta ver el ajuste y levántalo para confirmarlo. La figura visible se conserva aunque el lápiz se desplace un poco al levantarlo. Solo un movimiento claro con el lápiz todavía apoyado vuelve al trazo libre.

El ajuste usa las posiciones originales del puntero, no la trayectoria suavizada del pincel. Aparece tras 320 ms de pausa y tolera un pequeño temblor. Los óvalos y rectángulos conservan la inclinación; las formas casi circulares se ajustan a un círculo. Para rectángulos y triángulos se ajustan los lados para recuperar esquinas dibujadas de forma redondeada. En las figuras cerradas se admite un pequeño hueco. Las curvas abiertas y los contornos ambiguos se dejan a mano alzada.

La vista previa no escribe en el documento. Al confirmar, la figura y sus copias simétricas se guardan como una sola operación de historial y conservan la selección vigente. No cambia el formato del proyecto.

## Figuras precisas

Toca el botón **Rectángulo** de la barra lateral para desplegar **Rectángulo**, **Triángulo**, **Elipse** y **Línea**. El botón conserva el icono de la última figura elegida. Arrastra para crear un borrador, ajusta sus extremos o esquinas y usa el tirador central para moverlo sin deformarlo. **Aplicar** guarda el resultado en un solo paso de historial; **Cancelar** descarta el borrador sin pintar. QuickShape a mano alzada sigue separado en **Más opciones** y confirma al levantar el lápiz.

Con QuickShape activo, **Más opciones → QuickShape: ajustar antes de aplicar** permite abrir la figura reconocida en el mismo editor de tiradores. Los rectángulos y las elipses inclinados conservan su ángulo al cambiar de tamaño. Es optativo; sin esta opción se conserva la confirmación al levantar el lápiz. En la barra de una selección, **Precisión** permite introducir desplazamientos en píxeles, escala y giro como una sola operación de historial.

El gesto de mantener se inspira en el [manual de QuickShape de Procreate](https://help.procreate.com/articles/zymjvk-quickshape).

## Verificación · 12 de noviembre de 2026

- `check`, `assembleDebug` y `assembleDebugAndroidTest` correctos; 22 pruebas del reconocedor y la captura de movimiento pasaron.
- Galaxy Tab S8 (SM-X700, Android 16): se reinstalaron la app debug y el APK de pruebas por reemplazo. Pasaron 18 pruebas de QuickShape, máscaras, historial y navegación multitáctil. ADB terminó con código 0 y `INSTRUMENTATION_CODE: -1`.
- Se comprobó la confirmación tras un desplazamiento al levantar el lápiz simulado, el reconocimiento de polígonos con esquinas redondeadas y un pequeño hueco, y la igualdad de píxeles al deshacer y rehacer figuras que cruzan tiles. Sigue pendiente la prueba manual con S Pen.
- Registro local: `app/build/tab-s8-quickshape-confirm-20261112-095856.log`.

### Figuras precisas y trazos rápidos

- Gradle completó `check`, `assembleDebug` y `assembleDebugAndroidTest`. Pasaron 23 pruebas del reconocedor y 2 de geometría de tiradores. Los contornos rápidos se prueban con solo cuatro o cinco muestras, sin aceptar los equivalentes abiertos.
- En la Galaxy Tab S8 con la compilación reinstalada, pasaron 20 pruebas de integración y 1 del menú real del editor. Se verificó que el borrador no modifica los píxeles, que el rectángulo editado se puede deshacer y rehacer como un solo paso, y que cancelar el triángulo no deja marcas.
- Registros locales: `app/build/tab-s8-precise-shape-20261112-100742.log` y `app/build/tab-s8-precise-shape-ui.log`. No reemplazan la validación con trazos manuales del S Pen.

### Menú lateral unificado y desplazamiento de figuras

- Pasaron `check`, `assembleDebug` y `assembleDebugAndroidTest`. En la Tab S8 se reinstaló la compilación debug por reemplazo y pasaron 22 pruebas. La prueba del menú recorrió las cuatro opciones y verificó que no quedaran accesos duplicados en **Más opciones**.
- Se verificó que mover el centro conserva la geometría, que la línea mantiene su inclinación, que la elipse no se convierte en un contorno rectangular y que la figura editada se deshace y rehace en un solo paso. Registro: `app/build/tab-s8-shape-group-20261112-105024.log`.
