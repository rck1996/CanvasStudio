# Automatización de pruebas en tablet

## Certificación de Vulkan, pinceles y tutorial

La Fase 3 tiene un runner reproducible que compila, reinstala ambos APK, ejecuta las suites rápidas,
la suite completa sin duplicar la sesión larga y los escenarios Vulkan de 200/500 trazos:

```powershell
.\scripts\test-phase3.ps1 -Serial R52W404GGPK -IncludeTenMinute
```

Todos los comandos, horas, conteos y códigos ADB se guardan en `test-logs/`.

## Suite determinista

`scripts/test-raster-engine.ps1` es el oráculo principal. Compila, instala y ejecuta la
instrumentación directamente sobre el motor raster; no depende de capturas, texto
reconocido ni coordenadas de pantalla.

```powershell
.\scripts\test-raster-engine.ps1 -Iterations 5
```

Para reutilizar APK ya instalados:

```powershell
.\scripts\test-raster-engine.ps1 -SkipBuild -SkipInstall -Iterations 5
```

La suite rápida excluye únicamente las pruebas marcadas `LargeTest`. Para ejecutar además la
regresión de 500 trazos largos y gruesos:

```powershell
.\scripts\test-raster-engine.ps1 -Iterations 1 -IncludeMassive
```

Cada ciclo rápido ejecuta al menos 40 pruebas. La suite cubre:

- 200 marcas gruesas únicas en `4096 × 2732`;
- 539 trazos distribuidos entre once familias de pincel;
- 64 trazos HB modificados, gruesos y largos;
- presión de caché, reconstrucción por región y carga de tiles visibles;
- handoff seguro entre preview y raster;
- transición de un dedo a navegación con dos dedos sin vaciar el compuesto;
- guardado, reinicio, recuperación de metadata y versiones locales;
- presets, recursos bitmap, shortcuts y PSD;
- política de memoria y exportación 8K con seis capas dispersas;
- curvas independientes, Dual Brush, mezcla húmeda y rango físico del S Pen;
- tutorial interactivo y persistencia de su progreso;
- opcionalmente, 500 trazos largos de 180 px alternando cinco medios profesionales.
- renderer Vulkan real, comparación A/B de tinta/grafito y fallback simulado;
- tutorial modular con validación ordenada de acciones, pausa, reinicio y progreso local;
- cargas Vulkan aisladas de 200 y 500 trazos y sesión continua de diez minutos.

El script detecta dinámicamente el total de tests, exige un mínimo configurable y genera
`build/reports/phase8-certification/latest.json`.

## Runner visual ADB

`scripts/test-tablet-stress.ps1` es complementario. Interactúa con la UI, selecciona
pinceles, inyecta una matriz de hasta 200 trazos, reinicia la app y recopila capturas,
`gfxinfo`, memoria y Logcat.

```powershell
.\scripts\test-tablet-stress.ps1 -SkipBuild -SkipInstall -StrokeCount 200 `
  -BrushPresets "Lápiz HB","Carboncillo","Óleo espeso","Spray granulado" `
  -BrushSizeIncrements 14
```

Los resultados se guardan en `build/reports/tablet-stress/`.

La comparación visual sólo es concluyente sobre un proyecto de prueba inicialmente
limpio. Si el lienzo base ya contiene marcas, el script conserva métricas y capturas pero
advierte que no puede contar con precisión cada celda. ADB tampoco reproduce presión,
inclinación ni la frecuencia real de un S Pen.

## Integración continua

`.github/workflows/android-ci.yml` ejecuta en cada push y pull request:

- `lintDebug`;
- `assembleDebug`;
- `assembleDebugAndroidTest`;
- `assembleRelease`;
- `bundleRelease`;
- publicación de reportes y artefactos de compilación.
