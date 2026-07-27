# Fase 4.1 — Capas no destructivas y perspectiva editable

Canvas Studio 1.6.0 cierra la revisión funcional posterior a la primera entrega de fase 4. La prioridad de esta versión es ampliar la composición sin volver a introducir el cierre corregido en 1.5.3 ni degradar el trazo en la Galaxy Tab S8.

## Grupos de capas

- Crear un grupo desde la capa activa.
- Las capas nuevas y duplicadas heredan el grupo de la capa activa.
- Visibilidad, opacidad y colapso del grupo.
- Sacar la capa activa de su grupo.
- Persistencia del grupo y sus ajustes en el proyecto.

Los grupos son de un solo nivel. La opacidad se aplica a cada miembro y no como composición aislada del grupo; los grupos anidados quedan para la beta.

## Máscaras raster

- Añadir y eliminar una máscara por capa.
- Activar o desactivar la máscara sin destruirla.
- Editar la máscara con Pincel y Borrador.
- Pincel oculta; Borrador revela.
- Historial de deshacer/rehacer separado entre contenido y máscara.
- Almacenamiento disperso por tiles, guardado incremental y carga bajo demanda.
- Persistencia en el formato de proyecto v7.

La máscara usa una superficie de ocultación: vacía significa que la capa se muestra por completo. No hay todavía máscaras vectoriales ni feather configurable.

## Perspectiva editable

- Guías de uno o dos puntos.
- Modo Editar puntos que bloquea temporalmente el dibujo para evitar trazos accidentales.
- Arrastre directo de los puntos de fuga.
- Restablecimiento de las posiciones.
- Persistencia de posiciones y modo de guía.

## Rendimiento

- Las capas ordinarias se dibujan directamente, sin crear una superficie temporal por frame.
- `saveLayer` se reserva para máscaras y clipping, que realmente necesitan composición fuera de pantalla.
- Las máscaras participan en el mismo presupuesto LRU de tiles que el contenido.
- Precarga del viewport extendida a los tiles de máscara visibles.
- El clipping respeta también la máscara de la capa base.

## Compatibilidad

- Formato de proyecto v7.
- Lectura de proyectos v2 a v6.
- Mismo `applicationId`; instalar encima conserva la biblioteca local.
- Las versiones antiguas no deben usarse para volver a guardar un proyecto ya migrado a v7.

## Pendiente para fase 5

- Grupos anidados y composición aislada.
- Renombrado y selección múltiple de grupos.
- Feather, inversión y niveles de máscara.
- PSD básico.
- Snap de perspectiva y reglas.
- Diario de recuperación y pruebas de dispositivos para beta.
