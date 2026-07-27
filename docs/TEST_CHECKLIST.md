# Checklist de pruebas — Fase 4.1 · 1.6.0

## 0. Regresión crítica del S Pen

- Realizar un trazo corto y uno largo con Gouache, Pintura suave, Carboncillo y Tiza.
- Confirmar que levantar el S Pen no cierra la aplicación.
- Hacer 100 trazos consecutivos y 20 operaciones de deshacer/rehacer.
- Seguir dibujando mientras aparece el autoguardado.

## 1. Compatibilidad

- Instalar encima de 1.5.3 sin desinstalar.
- Abrir proyectos antiguos y confirmar capas y arte previo.
- Guardar, volver a la galería y reabrir.
- Confirmar que deshacer solo afecta los cambios de la sesión actual.

## 2. Grupos de capas

- Seleccionar una capa y pulsar **Agrupar capa**.
- Crear y duplicar una capa; confirmar que aparecen dentro del mismo grupo.
- Ocultar y mostrar el grupo.
- Cambiar su opacidad y colapsarlo.
- Sacar una capa del grupo.
- Guardar y reabrir; comprobar que estructura y ajustes permanecen.

## 3. Máscaras raster

- Añadir una máscara a una capa con contenido.
- En modo máscara, usar Pincel para ocultar y Borrador para revelar.
- Deshacer y rehacer cambios de máscara sin afectar el contenido de la capa.
- Desactivar y volver a activar la máscara.
- Eliminarla y confirmar que el contenido original sigue intacto.
- Probar máscara junto con opacidad, modo de fusión y clipping.
- Guardar y reabrir el documento.

## 4. Perspectiva editable

- Activar perspectiva de uno y dos puntos.
- Pulsar **Editar puntos** y arrastrar cada punto de fuga.
- Confirmar que un toque fuera de los puntos no crea un trazo accidental.
- Restablecer las guías.
- Guardar y reabrir para comprobar posiciones y modo.

## 5. Selección y transformación

- Crear selección rectangular, elíptica y lazo.
- Dibujar, borrar, rellenar y aplicar degradado dentro de la selección.
- Mover, escalar, rotar y voltear contenido.
- Guardar y reabrir el resultado.

## 6. Rendimiento en Galaxy Tab S8

- Lienzo 4096 × 4096 con seis capas normales: comprobar fluidez.
- Añadir máscaras a dos capas y repetir 100 trazos texturizados.
- Ocultar/mostrar un grupo durante zoom, desplazamiento y rotación.
- Probar un lienzo 8K y observar que los tiles cargan sin bloquear el S Pen.
- Confirmar que las capas sin máscara no sufren una caída visible de FPS.

## 7. Exportación

- Exportar PNG y abrirlo en la galería.
- Exportar `.ora` y abrirlo en Krita.
- Verificar máscara aplicada, orden, visibilidad y opacidad efectiva.

## Reporte útil

Adjuntar captura o log, acción exacta, tamaño del lienzo, cantidad de capas/máscaras, pincel utilizado y si el proyecto provenía de una versión anterior.
