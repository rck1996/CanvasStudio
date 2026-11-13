# Motor de pinceles · Espaciado por distancia

Los pinceles de acuarela, óleo y cerdas de 48 px o más ya no depositan como mínimo una marca por segmento recibido. Conservan la fracción pendiente del espaciado al pasar al siguiente segmento. Así, añadir puntos sobre la misma trayectoria no obliga al motor a depositar más pintura. La presión y la velocidad siguen modulando el tamaño de la punta; el espaciado se limita cuando la huella efectiva se vuelve estrecha.

La distribución y la semilla de textura se calculan sobre la trayectoria completa antes de rasterizar por tiles. Cada tile puede omitir segmentos fuera de su región sin reiniciar la fase ni el orden de marcas. El papel de prueba, las muestras y el replay de Canvas usan la misma política. Los pinceles finos, lápiz, tinta, borrador y el backend experimental conservan su ruta anterior; un comando con este espaciado se deriva a Canvas.

La idea toma como referencia [libmypaint](https://github.com/mypaint/libmypaint/blob/master/brushsettings.json), que distingue la densidad de marcas según el tamaño de la punta y la densidad según el tiempo. Esta etapa implementa distancia, no deposición por tiempo ni compatibilidad con presets externos. No cambia el formato de los proyectos.

## Qué se verifica

- Las mismas posiciones y semillas de textura para una línea subdividida de maneras distintas.
- La fracción pendiente se mantiene al detenerse o variar el espaciado. Los segmentos extremos conservan el límite de 48 marcas.
- Igualdad exacta de píxeles con distinta densidad de puntos en un caso controlado, continuidad en la unión entre tiles y reproducción exacta del raster por tiles al deshacer y rehacer.
- Retención de las marcas antiguas tras 500 trazos largos en la Tab S8. La sensación con el S Pen físico se mantiene fuera de esta tanda.

## Validación · 12 de noviembre de 2026

`check`, `assembleDebug` y `assembleDebugAndroidTest` pasaron, incluidas 5 pruebas unitarias de espaciado. En la Tab S8 (SM-X700, Android 16) pasaron las 20 pruebas dirigidas y la carga de 500 trazos largos. Esta última conservó las cuatro marcas de control en 46,840 s, por debajo del límite de 120 s. La revisión anterior de mezcla húmeda registró 71,036 s en el mismo escenario y dispositivo; son dos ejecuciones individuales, no una media.

`scripts/test-brush-engine.ps1 -Install` repite ambas tandas y guarda los registros en `app/build/tab-s8-brush-engine-*.log`. Para solo la tanda dirigida se puede añadir `-WithoutStress`.
