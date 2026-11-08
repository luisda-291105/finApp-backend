# Documentación de la API

FinApp publica su especificación OpenAPI y la interfaz interactiva de Swagger UI desde el backend.

## Rutas

- Swagger UI: `http://localhost:8081/docs`
- Especificación OpenAPI en JSON: `http://localhost:8081/api-docs`

Las operaciones están agrupadas por dominio: sesión y usuarios, autenticación Google, Gmail, asistente, bolsillos, contactos, fuentes, grupos, movimientos, muestras y tarjetas.

## Probar operaciones

1. Inicia el backend según la guía principal del repositorio.
2. Abre `/docs` desde el mismo origen del backend.
3. Para probar rutas protegidas, ejecuta `POST /usuario/login` con las credenciales de desarrollo. La sesión se conserva en la cookie `JSESSIONID` del navegador.
4. Usa **Try it out** en la operación deseada. Las rutas protegidas necesitan una sesión activa; las rutas públicas se pueden probar sin iniciar sesión.

La especificación también se puede importar en clientes compatibles con OpenAPI usando la URL `/api-docs`.
