# Certificación UX premium y suite normal · 2026-08-09

## Entorno

- dispositivo: Samsung Galaxy Tab S8 (`SM-X700`);
- Android: 16 / API 36;
- aplicación: `com.orbyte.canvasstudio.debug`;
- APK: `2.4.0-debug`, `versionCode 31`;
- actualización instalada: `2026-08-09 00:32:10`;
- paquete de pruebas: `com.orbyte.canvasstudio.debug.test`;
- actualización instalada: `2026-08-09 00:32:11`.

## Compilación

```powershell
.\gradlew.bat check assembleDebug assembleDebugAndroidTest `
  --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" --max-workers=2
```

Resultado: `BUILD SUCCESSFUL` en `1m 45s`, 107 tareas.

## Regresiones enfocadas

Se ejecutaron desde cero con el runner observable:

- `EditorTutorialIntegrationTest`: 18;
- `StudioTutorialStateTest`: 10;
- `EditorTutorialUiTest`: 3;
- `QuickAccessTest`: 7;
- `MaskEditingModeTest`: 2;
- `QuickMenuGestureTest`: 1.

Resultado: `41/41`, seis clases aprobadas, código de salida `0`.

Cubren los catorce módulos mediante eventos del editor real, comienzo siempre en `1/14`, posición de foco, tarjeta que evita el control objetivo, limpieza entre lecciones, salida y reentrada de Ocultación, transición Ocultación → Selección, gesto mantener/deslizar/soltar y sectores de la rueda contextual.

## Suite normal completa

```powershell
.\scripts\run-tablet-instrumentation.ps1 `
  -Serial R52W404GGPK -HeartbeatSeconds 3 -PerClassTimeoutMinutes 6
```

- inicio: `2026-08-09T00:32:23-04:00`;
- término: `2026-08-09T00:37:30-04:00`;
- duración: `307,2 s`;
- clases: `34/34`;
- pruebas: `133/133`;
- fallos: `0`;
- timeouts: `0`;
- código de salida: `0`.

La matriz de retención de pinceles aprobó 3/3 en `111,5 s`. Los escenarios Vulkan normales de 200 trazos gruesos y 500 trazos largos aprobaron 2/2 en `33,4 s`.

## Resistencia opcional

`VulkanEnduranceTest` contiene la sesión continua fija de diez minutos. Está anotada con `LongRunningTest`, excluida por defecto y no se contabiliza en los 133 casos anteriores.

```powershell
.\scripts\run-tablet-instrumentation.ps1 `
  -Serial R52W404GGPK `
  -Classes com.orbyte.canvasstudio.drawing.VulkanEnduranceTest `
  -IncludeEndurance -PerClassTimeoutMinutes 12
```

El mecanismo `-IncludeEndurance` se verificó con una clase rápida: 1/1, código de salida `0`. La sesión de diez minutos no se volvió a ejecutar durante esta certificación.

## Evidencia local generada

Los logs detallados se producen bajo `build-logs/instrumentation-*` y se excluyen de Git para evitar publicar archivos efímeros. El runner conserva por ejecución `events.log`, `state.json`, `results.json` y stdout/stderr por clase. Este documento registra el resultado estable que sí pertenece al repositorio.
