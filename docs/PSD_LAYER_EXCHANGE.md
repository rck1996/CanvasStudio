# Intercambio PSD por capas

**Más opciones → Exportar PSD por capas** escribe un PSD v1 RGB de 8 bits. Cada capa raster mantiene nombre Unicode, orden, transparencia, visibilidad, opacidad, bloqueo alfa y los modos de fusión compatibles. El archivo incluye además la imagen compuesta. Se escribe por bandas y recorta las capas al área de tiles ocupados. En un lienzo de 7680 px de ancho la banda de 512 filas usa 15 MiB de bitmap, frente a unos 127 MiB del lienzo RGBA entero; ambas cifras son de buffers, no del pico total del proceso. El PSD temporal no se publica hasta validar que el documento no cambió.

- Las máscaras activas se aplican a la transparencia de la capa exportada. Los grupos se aplanan conservando las capas y sus propiedades efectivas; el original no cambia. Se avisa antes de exportar un documento con máscaras o grupos.
- PSD puede expresar recortes simples. Canvas Studio rechaza cadenas de recorte que no se pueden representar con la misma semántica. Usa PNG si necesitas conservar el compuesto de esas cadenas.
- **Importar PSD** agrega las capas raster a un lienzo que tenga espacio para ellas, centrando el documento importado sin reescalar. Admite datos de capa Raw, PackBits y ZIP; un PSD compuesto compatible sigue entrando como una sola capa. Las capas se publican juntas al terminar de prepararlas. Hay límites de bytes, dimensiones y memoria.
- No se prometen carpetas PSD editables, máscaras PSD editables, ajustes no rasterizados, objetos inteligentes, texto, PSB, CMYK, 16 bits ni modos de fusión que no existan en Canvas Studio. Un archivo de capas fuera del alcance se rechaza en vez de aplanarlo silenciosamente.
- La política de color es **sRGB de 8 bits sin conversión ICC**. Exportamos sin un perfil incrustado; si un PSD por capas trae un ICC, la app avisa de que no lo convierte. Un PSD Adobe RGB o Display P3 debe convertirse a sRGB antes de importar si se necesita fidelidad.

Referencia de formato: [especificación PSD de Adobe](https://www.adobe.com/devnet-apps/photoshop/fileformatashtml/). La auditoría cruzada de salida usa `scripts/verify-psd-interop.py` con psd-tools.
