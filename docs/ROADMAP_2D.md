# Roadmap · Canvas Studio para ilustración 2D en Android

Objetivo: una app fiable y fluida para pintar en tablets Android. No implica copiar todo Procreate ni usar un número de funciones como medida de calidad. Estas son cinco fases propuestas para el núcleo 2D, después de agrupar y ajustar las figuras.

| Fase | Trabajo | Criterio de cierre |
|---|---|---|
| 1. Entrada y estabilidad | Latencia del S Pen, palma, navegación, trazos largos, memoria y recuperación de documentos. | Medir punta a pantalla y fluidez en hardware; completar las pruebas de estrés, guardado y reapertura en la compilación candidata. |
| 2. Pinceles | Pulir HB, tinta, cerdas y húmedos; presión, inclinación y fidelidad de la vista previa. | Comparar muestras reales a distintos tamaños, verificar que no se pierdan trazos y evitar regresiones de latencia. |
| 3. Editor y UX | QuickShape a mano alzada, capas, recortes, máscaras, transformaciones avanzadas y gestos accesibles. | Usuarios completan los flujos frecuentes sin instrucciones externas; no hay diferencias de píxeles inesperadas al deshacer o rehacer. |
| 4. Intercambio profesional | PSD por capas, con alcance de compatibilidad explícito; perfilar exportaciones grandes y definir gestión de color. | Verificar ida y vuelta con archivos representativos, nombres y orden de capas, transparencia y formatos admitidos. |
| 5. Candidata a publicación | Suite completa, compatibilidad entre tablets, documentación, firma, actualización y recuperación. | Validar la compilación exacta a publicar, sin bloqueos de estabilidad, pérdida de trabajo ni incompatibilidades críticas. |

Entrada y estabilidad, pinceles y UX pueden avanzar en paralelo. La validación manual con S Pen está pendiente de medición. Las pruebas automatizadas de retención en Tab S8 son evidencia de conservación de trazos, no de latencia de punta a pantalla. La integración ya incluye [PSD raster por capas](PSD_LAYER_EXCHANGE.md) dentro de un alcance RGB de 8 bits, además de OpenRaster. La [matriz de la versión 2.5.0](FEATURE_MATRIX.md) es una referencia histórica, no una lista de todo lo incorporado en integración.

Fuera del núcleo de publicación: animación, páginas múltiples, grabación del proceso y pintura 3D. Son extensiones que se pueden evaluar después de consolidar el flujo 2D. Referencias oficiales: [Procreate para iPad](https://procreate.com/procreate/) y [formatos de archivo](https://help.procreate.com/procreate/handbook/gallery/gallery-file-types).
