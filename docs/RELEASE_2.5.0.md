# Canvas Studio 2.5.0

Release para tablets Android centrada en el acabado profesional del espacio de trabajo, la comprensión de capas y ocultación, el acceso rápido radial y un tutorial realmente integrado con el editor.

## Novedades

- rueda Quick Access de seis acciones mediante mantener, deslizar y soltar sobre el lienzo;
- perfiles Dibujo, Color, Capas y Personalizado, con estrella como acceso alternativo;
- panel de capas adaptable, asa de reordenamiento y relación visual entre molde y capa inferior;
- ocultación reversible explicada con lenguaje visual, miniatura de máscara interactiva y salida explícita para volver a pintar la capa;
- cambio automático desde Ocultación cuando se selecciona una herramienta incompatible;
- tutorial de 14 módulos sobre el editor real, siempre iniciado en `1/14`;
- guía que evita cubrir el control objetivo, corrige insets del sistema y limpia selección, simetría y estados transitorios entre módulos;
- pistas contextuales, confirmación de resultados y señal visible para aprender el gesto de Quick Access;
- runner ADB por clases con heartbeat, timeout, estado JSON y logs independientes;
- sesión fija de diez minutos aislada como `VulkanEnduranceTest` opt-in.

## Artefactos

| Archivo | Uso | Tamaño | SHA-256 |
|---|---|---:|---|
| `CanvasStudio-2.5.0.apk` | Instalación directa en tablets | 9.291.045 bytes | `088F9B475D4ABC64324999643B0C67230CE58127FFE2C26697733576530F2F78` |
| `CanvasStudio-2.5.0.aab` | Distribución mediante tienda | 8.216.106 bytes | `D67790848D7BE07CB874AEDA2604199FE2D765D6978BCF4958FB070ED8EA94CD` |

El APK identifica `com.orbyte.canvasstudio`, `versionCode 32`, `versionName 2.5.0`, `minSdk 26` y `targetSdk 36`. La firma APK v2 y la firma JAR del AAB fueron verificadas correctamente. Certificado: `CN=Canvas Studio, OU=Orbyte, O=Orbyte, L=Santiago, ST=RM, C=CL`.

## Validación

```powershell
.\gradlew.bat check assembleRelease bundleRelease `
  --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" --max-workers=2
```

- Gradle: `BUILD SUCCESSFUL` en `2m 26s`;
- comprobaciones enfocadas de tutorial, máscara y Quick Access: `41/41`;
- instrumentación normal completa en Galaxy Tab S8: `133/133`, `34/34` clases;
- duración de instrumentación: `307,2 s`;
- fallos y timeouts: `0`;
- códigos de salida: `0`;
- firma de APK y AAB: verificada;
- enlaces Markdown locales: verificados.

La prueba continua fija de diez minutos está separada de la suite normal y no se contabiliza dentro de los 133 casos. Los escenarios acotados de 200 trazos gruesos y 500 trazos largos sí forman parte de la certificación.

Evidencia detallada: [`test-results/premium-ux-tab-s8-2026-08-09.md`](test-results/premium-ux-tab-s8-2026-08-09.md).

## Compatibilidad

- tablets Android 8.0 o superior;
- interfaz restringida a dispositivos `sw600dp`;
- documentos Canvas Studio v7 sin cambios de formato;
- historial y aceleradores de sesión no se vuelven persistentes por esta release.
