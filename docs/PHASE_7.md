# Fase 7 · Feature Complete

## Bloque 7.1

- selección múltiple explícita de capas;
- agrupación de la selección y grupos anidados persistentes;
- movimiento y eliminación por selección;
- jerarquía visual indentada con grupos colapsables;
- propiedades de capa colapsables para aprovechar mejor la pantalla de tablet;
- reglas con guías horizontales y verticales arrastrables y persistentes;
- cinco modos de fusión adicionales mediante `BlendMode` moderno;
- ocho presets nuevos con identidades de material diferenciadas;
- atajos `Ctrl+S`, `Ctrl+Y`, `Ctrl+Shift+Z`, `Tab` y `0`;
- importación y exportación PSD básica del compuesto RGBA;
- prueba automática de ida y vuelta PSD.

El PSD básico intercambia la imagen compuesta. OpenRaster sigue siendo el formato recomendado
cuando se necesita conservar la estructura editable de capas.

## Bloque 7.2 · cierre feature complete

- preview de material determinista y reactivo a tamaño, opacidad, flujo, dureza,
  espaciado, textura, presión, inclinación, taper, dispersión y velocidad;
- editor de pinceles de tres columnas alineado al mockup;
- pestañas colapsables `General` y `Dinámicas`;
- curva gráfica de presión reactiva;
- importación de puntas bitmap con conversión a máscara alfa y límite de 256 px;
- recursos bitmap portables dentro de las bibliotecas JSON;
- caché LRU de puntas limitada a ocho bitmaps;
- 25 pruebas instrumentadas aprobadas en la Galaxy Tab S8.

La fase 7 queda feature complete. La certificación masiva, capturas finales y artefactos
de publicación pertenecen a la fase 8.
