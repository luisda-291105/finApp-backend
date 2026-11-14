# Documentación de la API

FinApp publica su especificación OpenAPI y la interfaz interactiva de Swagger UI desde el backend.

## Rutas

- Swagger UI: `http://localhost:8081/docs`
- Especificación OpenAPI en JSON: `http://localhost:8081/api-docs`

Las operaciones están agrupadas por dominio: sesión y usuarios, Gmail, asistente, bolsillos, contactos, fuentes, grupos, movimientos, muestras y tarjetas.

## Probar operaciones

1. Inicia el backend según la guía principal del repositorio.
2. Abre `/docs` desde el mismo origen del backend.
3. Usa **Try it out** en la operación deseada. Las rutas protegidas necesitan autenticación; las públicas se pueden probar sin iniciar sesión.

## Autenticarse en Swagger UI

La API usa JWT. Hay dos vías:

### Opción A — Botón **Authorize** (Bearer) ✅ recomendado

1. Genera un token de sesión para tu usuario:

   ```bash
   python docs/generar_token_swagger.py <idUsuario>
   ```

   El script toma la clave de `FINAPP_JWT_SECRET` (variable de entorno o `.env`) y replica los claims que emite
   `JwtTokenService` (`sub`, `jti`, `iat`, `exp`, `expiraEn`, `actividad`), con expiración absoluta de 8 h.

   Para conocer tu `idUsuario`:

   ```bash
   mysql -uroot finApp -e "SELECT id_usuario, correo FROM usuario;"
   ```

2. En Swagger UI pulsa **Authorize**, elige el esquema `JwtFinAppBearer` y pega el token **sin** la palabra `Bearer`.
3. Todas las operaciones protegidas se ejecutan ya autenticadas (`Authorization: Bearer <token>`).

### Opción B — Cookie de sesión

1. Ejecuta `POST /usuario/login` con tus credenciales desde Swagger.
2. Copia el valor de `FINAPP_AUTH` del header `Set-Cookie` de la respuesta y pégalo en **Authorize** → esquema `JwtFinApp` (cookie).

> La cookie que usa la web es `HttpOnly` + `SameSite=Lax` + `Secure` en producción, por eso el navegador no la expone a JavaScript.

## Notas de seguridad

- Sin token válido, las rutas protegidas responden `401 {"mensaje":"Inicia sesión para continuar."}`.
- El token también expira por inactividad (`FINAPP_JWT_INACTIVITY_SECONDS`, 30 min por defecto) y de forma absoluta a las 8 h.
- El logout revoca el token en el servidor (deny-list de `jti`), por lo que un token copiado deja de servir tras cerrar sesión.

La especificación también se puede importar en clientes compatibles con OpenAPI usando la URL `/api-docs`.
