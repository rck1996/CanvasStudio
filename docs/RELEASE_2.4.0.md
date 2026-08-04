# Canvas Studio 2.4.0

Release para tablets Android `sw600dp` o superiores, certificada en Samsung Galaxy Tab S8 (`SM-X700`) con Android 16.

## Cambios principales

- biblioteca curada de 14 pinceles profesionales y 4 medios experimentales opcionales;
- previews conectadas al mismo evaluador de pincel utilizado por el lienzo;
- historial raster escalable por tile y superficie, con checkpoints acotados de sesión;
- backend Vulkan compute experimental disponible solo en builds debug compatibles;
- tutorial de 14 lecciones integrado en el editor real mediante un documento temporal;
- foco del tutorial reposicionable, tarjeta minimizable y comienzo limpio desde la lección 1;
- máscaras explicadas como **Ocultar sin borrar**, con acciones **Ocultar** y **Recuperar**;
- compatibilidad de documentos conservada en formato v7.

## Artefactos

| Archivo | Uso | SHA-256 |
|---|---|---|
| `CanvasStudio-2.4.0.apk` | Instalación directa en tablets | `5B716B3E3479280C3AFB5D98560FB4471F494EA3050D36D1B606A1533F475791` |
| `CanvasStudio-2.4.0.aab` | Distribución mediante tienda | `4293895751670F46EEAD2F4F84960E4EB765762274A0C6B8AD2F23B24ED555EF` |

El APK está firmado con un certificado RSA de 4096 bits (`CN=Canvas Studio, OU=Orbyte, O=Orbyte`) y verifica mediante APK Signature Scheme v2.

## Validación

```powershell
.\gradlew.bat check assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease
```

- Gradle: `BUILD SUCCESSFUL`;
- instrumentación específica del tutorial: 9/9;
- instrumentación completa en Tab S8: 116/116;
- duración de la suite completa: `808,15 s`;
- sesión continua: diez minutos;
- estrés: 500 trazos largos y 200 trazos gruesos;
- código de salida ADB: `0`;
- enlaces Markdown locales: verificados.

## Alcance conocido

- la interfaz está destinada exclusivamente a tablets;
- Canvas/Bitmap tiled continúa como backend de producción;
- Vulkan sigue siendo experimental y no reemplaza el renderer para todos los pinceles;
- PSD representa el compuesto RGBA, no capas PSD editables;
- el historial y sus checkpoints no persisten entre reinicios de la aplicación;
- no incluye sincronización en nube ni gestión ICC/CMYK.
