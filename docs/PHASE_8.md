# Fase 8 · Certificación y release

## Correcciones críticas

La desaparición instantánea al apoyar dos dedos ocurría porque el primer dedo iniciaba un
trazo raster y el segundo cancelaba ese preview mediante una reconstrucción completa de
capas. Los trazos táctiles ahora se difieren en un overlay hasta confirmar `ACTION_UP`.
El segundo dedo cambia a navegación sin modificar ni reconstruir el compuesto. AndroidX
Ink sólo procesa stylus y borrador.

## Presupuesto de memoria

Los documentos continúan usando tiles dispersos; su memoria de edición depende de tiles
residentes, no del área completa. Las operaciones aplanadas sí requieren un bitmap RGBA,
por lo que la creación se adapta al heap de la app y la exportación conserva un margen:

- 12 Mpx para heaps menores de 256 MiB;
- 26 Mpx desde 256 MiB;
- 40 Mpx desde 384 MiB;
- 8.192 px como máximo por lado para lienzos nuevos.

La compatibilidad de lectura permanece en 64 Mpx y 16.384 px por lado.

## Evidencia

Dispositivo: Samsung Galaxy Tab S8 Wi‑Fi (`SM-X700`, serial de prueba
`R52W404GGPK`).

- 28 pruebas instrumentadas aprobadas;
- 20 ciclos consecutivos;
- 560 ejecuciones;
- 16.140 verificaciones de retención;
- regresión multitouch aprobada;
- exportación 8K con seis capas dispersas aprobada;
- Android Lint sin errores;
- APK debug instalado en la tablet.

## Automatización

- `scripts/test-raster-engine.ps1` produce un reporte JSON y ya no depende de un número
  histórico fijo de tests.
- `scripts/test-tablet-stress.ps1` normaliza `PATH`, lee UTF-8 y evita enviar texto
  acentuado a `adb shell input text`.
- `.github/workflows/android-ci.yml` compila, ejecuta lint y publica artefactos APK/AAB.
