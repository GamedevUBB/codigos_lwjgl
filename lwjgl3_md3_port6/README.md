# Port LWJGL 2 -> LWJGL 3 para visor MD3

Este proyecto conserva el código de renderizado inmediato/fixed-function del programa original, pero reemplaza las clases eliminadas de LWJGL 2 (`Display`, `Keyboard`, `Mouse`, `Sys`, `GLU`, `Pbuffer`) por adaptadores compatibles basados en GLFW y LWJGL 3.

## Ejecutar

```bash
gradle run
```

## Archivos importantes convertidos

- `GLApp.java`: ahora funciona sobre GLFW mediante adaptadores.
- `ModelosMD3.java`: se mantiene prácticamente igual, usando la clase `Keyboard` adaptada a GLFW.
- `MD3Model.java`: mantiene el cargador MD3 original y llamadas OpenGL fixed-function.
- `GLU.java`: reemplazo parcial de GLU para `gluPerspective`, `gluLookAt`, `gluOrtho2D`, mipmaps y project/unproject.

## Nota

Este port usa un contexto OpenGL compatible con fixed-function. No es una migración moderna con VAO/VBO/shaders; es una versión funcional para LWJGL 3 manteniendo el estilo del código original.
