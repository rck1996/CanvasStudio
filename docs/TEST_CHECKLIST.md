# Checklist de publicación · Canvas Studio 2.1.0

## 1. Entrada y navegación

- Dibujar trazos cortos y largos con S Pen.
- Cambiar entre Lápiz HB, Carboncillo, Óleo espeso y Spray granulado.
- Aumentar el tamaño por encima de 150 px y confirmar que el preview reacciona.
- Apoyar un dedo y luego un segundo: el dibujo existente no debe desaparecer.
- Hacer zoom, paneo y rotación con dos dedos; al volver a uno no debe pintarse por error.
- Apoyar la palma mientras se dibuja con S Pen.

## 2. Retención y persistencia

- Ejecutar `scripts/test-raster-engine.ps1 -Iterations 20`.
- Crear al menos 200 trazos largos y gruesos sobre varias teselas.
- Confirmar trazos antiguos antes y después del autoguardado.
- Cerrar, reabrir y comprobar exactamente el mismo contenido.
- Probar deshacer/rehacer después de presión de caché.

## 3. Tamaños

- Crear lienzos 2048², ilustración 4096 × 2732, 4K y 8K.
- Verificar MP, MiB RGBA, tiles y nivel de carga en el diálogo.
- Confirmar que dimensiones fuera del límite se ajustan antes de crear.
- En tablets de heap reducido, confirmar que presets demasiado grandes no se ofrecen.
- Abrir proyectos históricos grandes sin migración destructiva.

## 4. Capas y edición

- Crear, duplicar, seleccionar y reordenar capas.
- Agrupar selección y probar grupos anidados y colapsables.
- Probar visibilidad, opacidad, 12 modos de fusión, bloqueo alfa y clipping.
- Crear, editar, desactivar y eliminar una máscara.
- Probar selección rectangular, elíptica y lazo con feather.
- Guardar y reabrir estructura, guías, reglas y preferencias.

## 5. Exportación

- Exportar PNG y verificar dimensiones y transparencia.
- Exportar OpenRaster y abrirlo en una app compatible.
- Exportar/importar PSD compuesto.
- Exportar el preset 8K en una tablet compatible.
- Confirmar que una exportación fuera del presupuesto falla con mensaje y no cierra la app.

## 6. UI, accesibilidad y compatibilidad

- Probar orientación horizontal y vertical en una tablet `sw600dp`.
- Navegar por controles principales con TalkBack.
- Verificar que botones de proyecto y menús tengan nombres accesibles.
- Probar modo lienzo, paneles colapsables y atajos de teclado.
- Confirmar logo, icono adaptativo, icono temático y splash.
- Verificar que la app no se ofrezca como interfaz para teléfonos.

## 7. Release

- `lintDebug`, `assembleDebugAndroidTest`, `assembleRelease` y `bundleRelease`.
- Verificar firma del APK y del AAB.
- Instalar el APK release sobre una versión anterior compatible.
- Revisar Logcat: sin `FATAL EXCEPTION`, `OutOfMemoryError` ni señales nativas.
- Registrar versión, commit, dispositivo, Android y reporte de certificación.
