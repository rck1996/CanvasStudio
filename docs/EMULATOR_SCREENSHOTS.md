# Capturas reales de Canvas Studio · Android 15

Este flujo genera capturas **reales**, no renders conceptuales, del código de
Canvas Studio sobre emulador Pixel C con Android API 35.

## Cómo obtenerlas

En la rama de desarrollo, abrir GitHub Actions → **Canvas Studio design screenshots**
→ ejecución de la rama → **Artifacts** → `canvas-studio-ui-real-emulator`.

Archivos PNG generados:
- `01-galeria.png` — galería y tarjetas de proyectos
- `02-editor-capas.png` — lienzo, herramientas, controles e inspector de capas
- `03-paleta-pinceles.png` — paleta flotante real del editor
- `04-editor-color.png` — inspector de color

Se usan los Composables de producción (GalleryScreen y EditorScreen) sobre
Android 15, con los proyectos de ejemplo incluidos en la app; no se sintetizan
controles ni se dibuja un mockup adicional. El lienzo de ejemplo es más pequeño
que el proyecto máximo para mantener un tiempo de captura razonable.

Para repetir las imágenes, ejecutar manualmente el flujo con
`workflow_dispatch`. Los PNG se publican por 30 días.

No se incluye una captura de la app instalada completa con navegación entre
pantallas; la captura se ejecuta con el host Compose de prueba y los componentes
de producción. Se deja clara esta diferencia para no confundirla con una toma
externa del APK.
