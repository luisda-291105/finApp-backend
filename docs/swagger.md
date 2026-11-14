# Documentación de la API

FinApp publica su especificación OpenAPI y la interfaz interactiva de Swagger UI desde el backend.

## Rutas

- Swagger UI: `http://localhost:8081/docs`
- Especificación OpenAPI en JSON: `http://localhost:8081/api-docs`

Sin sesión activa, la especificación JSON devuelve **solo las rutas públicas**; con sesión devuelve la API completa.

Las operaciones están agrupadas por dominio: sesión y usuarios, Gmail, asistente, bolsillos, contactos, fuentes, grupos, movimientos, muestras y tarjetas.

## Probar operaciones

1. Inicia el backend según la guía principal del repositorio.
2. Abre `/docs` desde el mismo origen del backend.
3. Usa **Try it out** en la operación deseada. Las rutas protegidas necesitan autenticación; las públicas se pueden probar sin iniciar sesión.

## Autenticarse en Swagger UI

> **Las rutas protegidas están ocultas y bloqueadas sin credenciales.**
> Sin una sesión activa, `/api-docs` solo devuelve las rutas públicas (`/usuario/login`,
> `/usuario/admin-login`, `/usuario` y `/asistente/publico`) y cualquier endpoint de datos
> responde `401`. Al iniciar sesión, la especificación completa (76 rutas) queda visible.

La API usa JWT. Hay tres vías:

### Opción A — Login administrativo (recomendado para entrar a Swagger) ✅

1. Abre `http://localhost:8081/docs`.
2. Ejecuta **`POST /usuario/admin-login`** con el cuerpo:

   ```json
   { "identificador": "admin", "contrasena": "<LOGIN_PASSWORD del .env>" }
   ```

3. La respuesta trae la cookie `FINAPP_AUTH` (`Set-Cookie`). Como Swagger UI se sirve desde el mismo origen del backend,
   el navegador la guarda automáticamente y **todas las demás operaciones quedan autenticadas**.

   Tras el login, recarga `/docs` para que la interfaz muestre **la API completa** (con sesión,
   `/api-docs` ya no filtra las rutas).

   Las credenciales salen de `.env` (ignorado por git):

   ```properties
   LOGIN_USER=admin                  # alias del operador
   LOGIN_PASSWORD=<secreto>          # secreto compartido
   FINAPP_ADMIN_USER_ID=1101383776   # cuenta que representa la sesión
   ```

   - Si `LOGIN_USER` o `LOGIN_PASSWORD` están vacíos, el endpoint responde `404` (acceso deshabilitado).
   - La comparación de credenciales es en tiempo constante, los intentos fallidos se limitan por IP
     (5 por 15 min) y quedan registrados en el log de seguridad.

> ⚠️ Este acceso **omite la contraseña de la cuenta** y abre una sesión con los permisos de
> `FINAPP_ADMIN_USER_ID`. Mantén `LOGIN_PASSWORD` fuera del repositorio, cámbialo con frecuencia
> y desactívalo (dejando las variables vacías) en entornos que no lo necesiten.

### Opción B — Botón **Authorize** (Bearer)

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

### Opción C — Cookie de sesión

1. Ejecuta `POST /usuario/login` con tus credenciales desde Swagger.
2. Copia el valor de `FINAPP_AUTH` del header `Set-Cookie` de la respuesta y pégalo en **Authorize** → esquema `JwtFinApp` (cookie).

> La cookie que usa la web es `HttpOnly` + `SameSite=Lax` + `Secure` en producción, por eso el navegador no la expone a JavaScript.

## Notas de seguridad

- Sin token válido, las rutas protegidas responden `401 {"mensaje":"Inicia sesión para continuar."}`.
- El token también expira por inactividad (`FINAPP_JWT_INACTIVITY_SECONDS`, 30 min por defecto) y de forma absoluta a las 8 h.
- El logout revoca el token en el servidor (deny-list de `jti`), por lo que un token copiado deja de servir tras cerrar sesión.

La especificación también se puede importar en clientes compatibles con OpenAPI usando la URL `/api-docs`.
