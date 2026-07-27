# Performance Hotfix 1.5.2 — Samsung Galaxy Tab S8

## Síntoma reproducido

Después de aproximadamente 30 trazos superpuestos con Gouache, Pintura suave, Carboncillo o Tiza, el tiempo entre el movimiento del S Pen y el trazo visible comenzaba a crecer.

## Causas

1. Los pinceles con taper final reconstruían la región al terminar cada trazo y reproducían todos los comandos anteriores que intersectaban esa zona. En áreas superpuestas el coste crecía de forma cuadrática.
2. Cada muestra histórica del S Pen abría operaciones independientes sobre los tiles.
3. `BlurMaskFilter` y el modo de borrado se creaban nuevamente para cada stamp.
4. El autoguardado comenzaba a los 850 ms, generaba una miniatura y comprimía todos los tiles pendientes mientras mantenía bloqueada la superficie.
5. La caché podía comprimir PNG durante la ruta de dibujo para expulsar un tile modificado.

## Correcciones

- Los pinceles basados en stamps se conservan de forma incremental al terminar el trazo; el ajuste final por replay queda reservado a lápices y tinta.
- Hasta 24 segmentos se agrupan en una sola operación de raster por frame y por eje de simetría.
- La distancia mínima de entrada se adapta al diámetro y espaciado del pincel.
- Densidad mínima segura para pintura, aerógrafo y texturas; máximo de 48 stamps por segmento anómalo.
- Filtros y xfermode reutilizados.
- Guardado automático después de 3 segundos de inactividad, sin regenerar preview.
- Cada tile se copia brevemente bajo lock y se codifica fuera del lock en un hilo de prioridad reducida.
- Tiles sucios protegidos de la evicción síncrona hasta el autoguardado.

## Prueba recomendada

1. Lienzo 4096 × 4096, una capa inicialmente.
2. Gouache opaco a 70–100 px: 100 trazos en la misma zona.
3. Carboncillo y Tiza seca: 100 trazos adicionales.
4. Continuar dibujando mientras aparece `Guardando…`.
5. Añadir 5 capas, repetir y realizar 20 operaciones deshacer/rehacer.
6. Volver a galería, reabrir y comprobar todos los trazos.

La meta de esta revisión es mantener estable la latencia de entrada. El deshacer de una región con muchos trazos todavía puede tardar más que un trazo normal porque debe reconstruir esa región; su sustitución por snapshots raster pertenece a la siguiente revisión del historial.
