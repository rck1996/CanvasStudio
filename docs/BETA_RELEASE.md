# Canvas Studio 2.0.0 beta 01

Esta entrega cierra la base técnica de la beta para tablets Android.

## Estabilidad y recuperación

- Guardado incremental de tiles con metadata transaccional.
- Recuperación automática desde archivos temporales o respaldo.
- Guardado al detener o cerrar el editor.
- Hasta tres versiones locales completas por proyecto, separadas por al menos 15 minutos.
- Restauración atómica disponible en `ProjectVersionStore`.

Las versiones locales viven en el almacenamiento privado de la aplicación. Al eliminar
un proyecto también se eliminan sus versiones.

## Experiencia de tablet

- Requisito de actividad `smallestWidthDp=600`.
- Onboarding inicial con gestos, stylus y guardado.
- Ayuda rápida contextual desde el menú del editor.
- Perfiles de atajos por letras o fila numérica.
- Identidad, launcher, splash y logo interno definitivos.
- Contraste del logo verificado en Samsung Galaxy Tab S8.

## Backup

Los proyectos raster y sus versiones se excluyen del backup en nube de Android porque
pueden superar la cuota y producir copias parciales. La transferencia directa entre
dispositivos puede incluirlos. Para una copia portable, exporta PNG u OpenRaster.

## Pruebas verificadas

- Lint Android sin errores.
- Ocho pruebas instrumentadas.
- 20 ciclos consecutivos en Galaxy Tab S8.
- 4.000 trazos gruesos persistidos entre los ciclos.
- Recuperación de metadata, versiones locales y perfiles de teclado.

## Firma de lanzamiento

La configuración lee `keystore.properties` cuando está presente. Copia
`keystore.properties.example`, completa la ruta y credenciales y conserva tanto el
archivo como el keystore fuera de Git.

```powershell
gradlew.bat assembleRelease bundleRelease
```

Sin `keystore.properties`, Gradle genera artefactos release sin firma para inspección.
