# Canvas Studio 2.1.0

Release candidate estable para tablets Android de `600dp` o más.

## Estabilidad

- Renderer raster disperso en tiles de `512 × 512 px`.
- Autoguardado incremental, metadata transaccional y recuperación desde respaldo.
- Tres versiones locales por proyecto.
- Preview AndroidX Ink reservado al stylus; el toque usa un overlay diferido para que
  un segundo dedo pueda iniciar navegación sin reconstruir ni vaciar el lienzo.
- Retención verificada con trazos largos, gruesos, texturizados y once familias de pincel.

## Tamaños y memoria

Los lienzos nuevos se ajustan al heap comunicado por Android:

| Heap de la app | Máximo recomendado |
|---|---:|
| 384 MiB o más | 40 Mpx |
| 256–383 MiB | 26 Mpx |
| Menos de 256 MiB | 12 Mpx |

El máximo por lado para documentos nuevos es `8.192 px`. Los proyectos históricos de
hasta 64 Mpx continúan siendo legibles. El diálogo de creación informa MP, memoria RGBA,
tiles y nivel de carga. PNG, PSD compuesto y OpenRaster rechazan de forma controlada una
exportación que no quepa en memoria.

## Certificación

- Android Lint: sin errores.
- 28 pruebas instrumentadas.
- 20 ciclos consecutivos en Samsung Galaxy Tab S8 (`SM-X700`).
- 560 ejecuciones de prueba.
- 16.140 verificaciones de retención de trazos.
- Regresión de transición de uno a dos dedos.
- Exportación compuesta de un documento 8K con seis capas dispersas.
- APK debug instalado y probado en hardware real.

El reporte de la ejecución local se escribe en
`build/reports/phase8-certification/latest.json`.

## Firma y artefactos

La firma se configura fuera del repositorio mediante `keystore.properties`. El archivo y
el keystore nunca deben versionarse.

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

Artefactos:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## Antes de publicar

La publicación en Play Store todavía requiere decisiones del propietario: licencia,
política de privacidad, ficha, clasificación de contenido y distribución geográfica.
