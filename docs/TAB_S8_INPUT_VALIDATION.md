# Tab S8: entrada y estabilidad

Dos evidencias separadas: las pruebas con eventos simulados validan enrutamiento y retención, pero **no** miden la latencia del S Pen de punta a pantalla ni el rechazo de palma del digitalizador.

## 1. Eventos simulados en la tablet

Con las APK debug y de instrumentación de la misma compilación ya instaladas, ejecuta:

```powershell
.\scripts\test-tab-s8-input.ps1 -Serial '<serial exacto de la SM-X700>' -IncludeStress
```

El script solo consulta y prueba el dispositivo indicado. No instala, desinstala, borra datos, inicia emuladores ni inyecta gestos sobre la interfaz abierta. La instrumentación sí puede reiniciar el proceso de Canvas Studio: guarda y cierra el proyecto activo antes de empezar. Guarda los hashes de las APK usadas al instalarlas; el script registra la versión instalada y la fecha de actualización, que por sí solas no identifican una compilación de manera inequívoca.

- Entrada: lápiz/borra, cancelación, palma, presión e inclinación históricas, navegación con dos dedos y publicación asíncrona.
- Estrés opcional: expulsión y recarga de tiles y 500 trazos largos con marcas de control.
- Evidencia: `build/reports/tab-s8-input/`, con modelo, versión, comando, pruebas, log completo y código de salida. Una tanda sin resultado final o con pruebas omitidas no cuenta como aprobada.

## 2. S Pen físico

Prepara un registro nuevo, sin ejecutar la instrumentación:

```powershell
.\scripts\test-tab-s8-input.ps1 -Serial '<serial exacto de la SM-X700>' -Mode Physical
```

En un documento de prueba, mantén fijos la orientación, escala, brillo, frecuencia de pantalla, modo de energía y condiciones térmicas. Registra el preset, tamaño, estabilización, QuickShape y frecuencia. Compara la compilación anterior y la candidata bajo las mismas condiciones, en un lienzo vacío y en una copia de un documento cargado. Para aislar el motor de la detección de figuras, usa QuickShape desactivado durante la medición.

1. Graba con una cámara externa a 240 fps reales o más, con la punta y el primer píxel visible en el mismo encuadre. No uses una grabación interna de pantalla.
2. Calienta el editor con tres trazos por condición. Después graba al menos 20 contactos medibles por condición con un pincel simple y repite con un pincel grueso/húmedo. Distingue primer contacto de los trazos continuos; en vídeo, revisa por separado si la línea se queda atrás o tiene pausas mientras la punta sigue moviéndose.
3. Anota ambos fotogramas y el vídeo en `physical-latency.csv`. Calcula `(first_ink_frame - contact_frame) × 1000 / camera_fps`. Reporta mediana y p95 (rango más cercano: posición `ceil(0.95 × n)`), número de muestras válidas y resolución del método (~4,17 ms por fotograma a 240 fps). Repite tomas ocluidas o con fotogramas perdidos; conserva el motivo en el registro.
4. Haz al menos diez repeticiones de cada caso y regístralas en `physical-stability.csv`: palma antes del lápiz, palma durante el trazo, retirar la palma dejando el lápiz apoyado, retirar primero el lápiz, y pellizcar inmediatamente al terminar un trazo. Busca marcas de palma, trazos cortados o duplicados, desapariciones momentáneas y fallos de Deshacer. Una palma que ya estaba apoyada es un caso distinto a una palma que entra mientras el lápiz dibuja.
5. Guarda, sal y vuelve a abrir el documento de prueba. Confirma que siguen visibles las marcas de control y registra el resultado. No uses proyectos personales.

No cierres la validación física sin vídeo legible, muestras y resultados comparables. Una regresión reproducible de latencia frente al control o cualquier trazo perdido/fantasma requiere volver al caso y repetirlo antes de certificar.

## Límite de la revisión de entrada

Al levantar un lápiz, se prioriza el puntero dueño del trazo. Si el lápiz se apoya después de un contacto táctil en modo pincel o borrador, toma posesión del gesto y reemplaza el borrador táctil sin escribir píxeles de la palma. La regresión simulada comprueba que se confirma un solo trazo aunque la palma siga apoyada. Aún hay que contrastar esta secuencia con el digitalizador físico y medir punta a pantalla.

Referencia: [Rechazo de palma en Android](https://developer.android.com/develop/adaptive-apps/cookbook/stylus-palm-rejection).
