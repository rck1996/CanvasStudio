# Automatización de pruebas en tablet

`scripts/test-tablet-stress.ps1` ejecuta pruebas de carga sobre la variante debug de Canvas Studio mediante ADB. Está orientado a una Samsung Galaxy Tab S8 (`SM-X700`) conectada por USB o depuración inalámbrica.

## Qué valida

- compilación e instalación de `com.orbyte.canvasstudio.debug`;
- apertura de un proyecto de prueba existente;
- inyección de una matriz de trazos únicos;
- espera de autoguardado;
- reinicio y reapertura de la aplicación;
- comparación de capturas antes y después para detectar trazos que no se ven;
- recolección de `gfxinfo`, memoria y Logcat.

Los artefactos se generan localmente en `build/reports/tablet-stress/`; el directorio no se versiona.

## Ejemplos

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\test-tablet-stress.ps1
```

```powershell
.\scripts\test-tablet-stress.ps1 -SkipBuild -SkipInstall -StrokeCount 200
```

## Límites conocidos

La inyección ADB no reproduce presión, inclinación ni la tasa de eventos de un S Pen. Por eso las pruebas automáticas detectan regresiones de persistencia, renderizado y estabilidad, pero no sustituyen la validación manual con pinceles gruesos y texturizados.

Antes de publicar una versión se debe ejecutar la lista manual de `TEST_CHECKLIST.md`, con énfasis en trazos prolongados, autoguardado y reabrir el proyecto.
