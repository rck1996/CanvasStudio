# Automatización de pruebas en tablet

## Runner instrumentado con estado observable

La suite recomendada se ejecuta por clase para aislar bloqueos y publicar su estado en tiempo real:

```powershell
.\scripts\run-tablet-instrumentation.ps1 -Serial R52W404GGPK
```

Cada ejecución crea `build-logs/instrumentation-<fecha>/` con:

- `state.json` para conocer la clase actual y su estado `START`, `RUNNING`, `PASS`, `FAIL` o `TIMEOUT`;
- `events.log` con heartbeats y marcas de tiempo;
- stdout y stderr independientes por clase;
- `results.json` con duración y código de salida.

El runner no interpreta `INSTRUMENTATION_CODE: -1` como error: AndroidJUnitRunner usa ese valor al finalizar correctamente. El éxito exige además el marcador `OK (...)` en la salida. La sesión fija de diez minutos está anotada con `LongRunningTest` y se excluye de la suite normal.

Para ejecutarla de manera deliberada:

```powershell
.\scripts\run-tablet-instrumentation.ps1 -Serial R52W404GGPK `
  -Classes com.orbyte.canvasstudio.drawing.VulkanEnduranceTest `
  -IncludeEndurance -PerClassTimeoutMinutes 12
```

La última certificación normal en Galaxy Tab S8 obtuvo `133/133` pruebas y `34/34` clases en `307,2 s`, con código de salida `0`. Véase [`test-results/premium-ux-tab-s8-2026-08-09.md`](test-results/premium-ux-tab-s8-2026-08-09.md).

## Certificación de Vulkan, pinceles y tutorial

El runner histórico de Fase 3 todavía permite compilar, reinstalar ambos APK y solicitar expresamente
los escenarios Vulkan de 200/500 trazos junto con la sesión larga:

```powershell
.\scripts\test-phase3.ps1 -Serial R52W404GGPK -IncludeTenMinute
```

Todos los comandos, horas, conteos y códigos ADB de ese flujo se guardan en `test-logs/`. Para la
certificación cotidiana debe preferirse `run-tablet-instrumentation.ps1`, que excluye endurance por defecto.

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
- cargas Vulkan aisladas de 200 y 500 trazos; la sesión continua de diez minutos se ejecuta únicamente como endurance opt-in.

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
