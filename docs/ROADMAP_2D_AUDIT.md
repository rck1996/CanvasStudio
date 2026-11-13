# Auditoría de los puntos 1–4 · 12 de noviembre de 2026

Compilación debug de integración; no es una release. Se reemplazan ambas APK conservando los datos de la app. Las pruebas usan dibujos de laboratorio, no documentos personales.

| Punto | Evidencia focalizada en Tab S8 | Límite |
|---|---|---|
| 1. Entrada y estabilidad | Palma antes y durante el lápiz; cancelar sin perder píxeles; muestras históricas; recuperación de proyectos y publicación asíncrona. | El usuario no percibió retraso en su prueba. No hay medición instrumental de punta a pantalla ni auditoría física de palma en esta compilación. |
| 2. Pinceles | Cobertura, flujo, presión, estabilización del preview, retención e igualdad de píxeles en undo/redo y entre tiles. | Falta comparar muestras físicas por preset y presión; no se certifica equivalencia con otros motores. |
| 3. Editor | QuickShape con contornos imperfectos, edición opcional con tiradores, transformación numérica con un paso de historial, menús y aislamiento de máscaras. | No incluye warp general ni evaluación de usabilidad con varios usuarios. |
| 4. Intercambio | PSD raster de ida y vuelta, apertura de un PSD de psd-tools y comprobación independiente del PSD emitido por la app; PNG, OpenRaster y PSD 8K por bandas. | El alcance es RGB8 con restricciones documentadas. No incluye gestión ICC completa, carpetas o máscaras PSD editables. |

La primera tanda ejecutó 79 pruebas y detectó dos fallos. Para el triángulo redondeado se descartaron candidatos rectangulares con lados opuestos desproporcionados, sin elevar la longitud mínima de los lados de un rectángulo estrecho. Para OpenRaster, la prueba ya no presupone que una punta con cobertura parcial borra toda la transparencia: comprueba una máscara sólida y otra suave por separado. La segunda tanda focalizada, con un fixture PSD externo adicional, terminó **80/80** con salida ADB 0 y cierre final de AndroidJUnitRunner.

La suite estándar de esa compilación ejecutó 284 pruebas y abrió tres incidencias adicionales: cambio de píxeles previos al deshacer una edición entre tiles, contraste insuficiente de Tiza seca y una prueba que aún buscaba Elipse en «Más herramientas». La corrección de historial congela la captación de pigmento también en pinceles de toma directa, sin releer tiles vecinos durante la reconstrucción. Se ajustó el depósito de Tiza seca y se actualizó la prueba del menú secundario para comprobar Relleno; el grupo de figuras tiene su propia prueba.

En la tanda anterior a esas correcciones, los 500 trazos mixtos conservaron cuatro marcas de control en **71,388 s** y los 200 trazos gruesos sobrevivieron a expulsión y recarga en **1,803 s**. El ensayo anterior de 46,840 s correspondía a otra compilación y no se midió bajo condiciones controladas idénticas. Estas duraciones no miden latencia del S Pen.

La matriz completa detectó también que Pincel seco y Acuarela granulada necesitaban más contraste tras corregir la opacidad. Se recalibró el depósito sin bajar el umbral de la prueba. La verificación de acuarela exige que una marca suave sea visible y siga translúcida, y que más presión aumente la cobertura. La compilación corregida pasó **84/84** pruebas focalizadas, incluida la matriz de 11 familias, los píxeles exactos de Deshacer/Rehacer y el intercambio PSD.

En la misma compilación, las tandas aisladas terminaron con salida ADB 0 y resultado final: **500 trazos mixtos en 72,067 s**, conservando cuatro marcas de control, y **200 trazos gruesos en 1,425 s**, con expulsión y recarga de tiles. El tiempo del cuerpo de la prueba de 500 fue 70,248 s; Acuarela granulada consumió 36,088 s para sus 100 trazos. Es el primer caso a perfilar en una comparación controlada, no una medida del retraso visible de la punta. Los registros son `app/build/tab-s8-roadmap-20261112-143102-focused.log`, `...-500.log` y `...-200.log`.

El PSD 8K generado por bandas tuvo 132.710.560 bytes. Un lector independiente (psd-tools 1.16.0) abrió el archivo de intercambio de 768 × 640 y comprobó sus tres capas, orden, nombres Unicode, opacidad, recorte y alfa. La prueba de importación usa un fixture distinto creado por ese lector/codificador con PackBits y ZIP.

## Cierre de la auditoría automatizada

- Pasaron `check`, `assembleDebug` y `assembleDebugAndroidTest`. La suite estándar final pasó **285/285** en Tab S8 (SM-X700, Android 16), sin omisiones, en **265,852 s**, con salida ADB 0 y `INSTRUMENTATION_CODE: -1`. Registro: `app/build/tab-s8-roadmap-20261112-143728-full.log`.
- La pasada previa de 285 tenía un único fallo: la prueba de perfiles asumía que la primera acuarela era siempre ligera. Se distinguieron por identificador Acuarela granulada y el lavado ligero, y se repitió la suite completa. Solo cambió el APK de pruebas en esa última iteración; se mantuvo el SHA-256 de la app de las tandas de estrés.
- Se dejó abierta la app debug en la Tab S8. No se inició la fase de publicación. Queda pendiente la medición física del S Pen y comparar el coste de acuarela gruesa en condiciones controladas.

## Repetir

Compila `check assembleDebug assembleDebugAndroidTest`. Después:

```powershell
.\scripts\test-roadmap-2d.ps1 -Serial '<serial de la Tab S8>' -Install -IncludeStress -Full
```

El script guarda hashes de ambos APK cuando instala, selección de pruebas, tiempos, salida ADB y log final bajo `app/build/tab-s8-roadmap-*.log`. La suite estándar excluye la sesión de diez minutos de Vulkan. Para separar pruebas físicas de simuladas, usa [el protocolo de S Pen](TAB_S8_INPUT_VALIDATION.md). Para regenerar el fixture externo, usa `scripts/verify-psd-interop.py --make-fixture app/src/androidTest/assets/psd/external-rgb-raster.psd` con psd-tools 1.16.x.
