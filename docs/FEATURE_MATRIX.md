# Feature matrix · 2.5.0

| Área | Estado 2.5.0 | Evolución futura |
|---|---|---|
| Galería | Adaptativa, búsqueda, duplicado, papelera recuperable, historial visual y miniaturas asíncronas con caché LRU | Colecciones |
| Lienzos | Presets incluidos y hasta 24 formatos personales persistentes, orientación reversible, DPI, estimación de memoria y límite dinámico 12/26/40 Mpx | Colecciones de formatos |
| Pinceles | 14 presets profesionales, 4 experimentales opcionales, control flotante táctil de tamaño/opacidad, presión, inclinación, taper, grano, dispersión, velocidad, puntas bitmap y migración de IDs históricos | Texturas multicanal |
| Biblioteca | Tres paneles, preview reactivo, curva de presión, favoritos, recientes y JSON portable | Orden manual |
| Renderer | Sparse tiles 512, viewport-only, LRU, prefetch, replay regional, entrada táctil diferida, buffers reutilizados en alpha-lock y raster JNI | Backend gráfico especializado |
| Multitouch | Zoom/pan/rotación sin borrar ni reconstruir temporalmente el compuesto | Gestos configurables |
| QuickShape | Enderezado optativo de líneas al mantener el lápiz quieto al terminar; no modifica trazos normales | Círculos, rectángulos y edición posterior de figuras |
| Selección | Rectángulo, elipse, lazo, invertir, expandir, contraer y feather 0–64 px | Operaciones booleanas |
| Transformación | Mover, escalar, rotar y voltear | Perspectiva, warp y valores numéricos |
| Capas | Multiselección, orden, clipping, bloqueo alfa, grupos anidados y máscaras raster | Composición aislada |
| Fusión | 12 modos | Gestión por espacio de color |
| Guías | Cuadrícula opcional, reglas px/cm, guías arrastrables, simetría y perspectiva | Rejillas personalizadas |
| Importación | Imagen como capa, biblioteca de pinceles y PSD compuesto | ORA de entrada y PSD por capas |
| Exportación | PNG, OpenRaster por capas y PSD compuesto hasta 40 Mpx | TIFF, PDF y exportación tiled |
| Persistencia | Autoguardado incremental, publicación transaccional recuperable y tres versiones locales seleccionables | Certificación ante cortes de energía |
| Color | HSV con selección por arrastre y gradientes acelerados, paleta básica, 10 colores recientes persistentes y ARGB 8-bit | ICC, CMYK, gamut y 16-bit |
| Tutorial | 14 lecciones dentro del editor real, documento temporal, foco contextual, progreso por evidencia, pistas y tarjeta minimizable | Tutoriales especializados adicionales |
| Distribución | APK/AAB firmables, CI y requisito `sw600dp` | Publicación Play Store |
