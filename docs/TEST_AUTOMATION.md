# Automatización de pruebas en tablet

La prueba principal es `scripts/test-raster-engine.ps1`. Ejecuta instrumentación Android sobre el motor tiled, sin capturas ni coordenadas de pantalla. Está orientada a una Samsung Galaxy Tab S8 (`SM-X700`) conectada por USB o depuración inalámbrica.

Cada iteración ejecuta cuatro pruebas: comprueba 200 marcas gruesas únicas distribuidas por un documento de `4096 × 2732`, fuerza guardado y presión de caché, reconstruye la superficie desde los PNG y verifica cada marca por píxel. También comprueba la recuperación asíncrona de tiles visibles, una metadata principal corrupta y un guardado interrumpido que dejó metadata temporal.

```powershell
.\scripts\test-raster-engine.ps1
```

Para una prueba prolongada sin recompilar ni reinstalar:

```powershell
.\scripts\test-raster-engine.ps1 -SkipBuild -SkipInstall -Iterations 50
```

## Prueba complementaria de interfaz

`scripts/test-tablet-stress.ps1` conserva el recorrido ADB de extremo a extremo para comprobaciones manuales de interfaz y métricas. No debe usarse como oráculo principal de persistencia de trazos, porque las coordenadas y la canalización de entrada varían según la rotación y configuración del dispositivo.

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
