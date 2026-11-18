# Sesión con access JWT + refresh deslizante

## Resumen

La autenticación de FinApp usa dos cookies HttpOnly:

| Cookie           | Tipo JWT       | Vigencia                                                                | Atributos                                                  | Uso                                     |
| ---------------- | -------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------- | --------------------------------------- |
| `FINAPP_AUTH`    | `tipo=access`  | 15 min (`finapp.sesion.access-seconds=900`)                             | `HttpOnly; Secure; SameSite=Lax; Path=/`                   | Autentica todas las peticiones de datos |
| `FINAPP_REFRESH` | `tipo=refresh` | 30 min de inactividad (`finapp.sesion.refresh-inactivity-seconds=1800`) | `HttpOnly; Secure; SameSite=Strict; Path=/usuario/refresh` | Solo se envía a `POST /usuario/refresh` |

El refresh se **rota en cada uso** y está limitado por un **tope absoluto de 8 horas**
(`finapp.sesion.absolute-seconds=28800`) contado desde el login. Ninguna rotación lo extiende.
La gracia para reuso concurrente es de 15 s (`finapp.sesion.reuse-grace-seconds=15`).

Los cuatro tiempos y el intervalo de limpieza viven únicamente en
`com.luisda.fin_app_back.Config.SesionConfig`. No se repiten como constantes en el código.

## Flujo

### `POST /usuario/login`

- Cuerpo: `{ "identificador": "...", "contrasena": "..." }`. Un body vacío o incompleto da **400** y
  nunca renueva una sesión.
- Conserva el rate limit y los mensajes de error previos.
- Crea una **familia** nueva (`familyId` UUID) y devuelve las dos cookies con
  `Cache-Control: no-store`.
- La familia guarda `familyId`, `idUsuario`, hash SHA-256 del refresh vigente, `usedAt`, `createdAt`,
  `absoluteExpiresAt`, `revokedAt` y `credentialVersion`. **Nunca se guarda el token en claro.**
- Un usuario con `activo=false` no puede iniciar sesión.

### `POST /usuario/refresh`

- **Sin body.** Solo lee la cookie `FINAPP_REFRESH`.
- `ValidacionOrigenInterceptor` **sí valida el Origin** (no está en la lista de exenciones).
  `UsuarioActivoInterceptor` lo exime porque el access puede estar vencido.
- Validaciones, en orden: firma/expiración/`tipo=refresh`, existencia de familia, familia no revocada
  ni vencida, usuario de la familia, hash con comparación en tiempo constante, reuso, usuario activo,
  `credentialVersion` del token y de la familia, y tope absoluto.
- Si todo pasa: emite un access nuevo y un refresh nuevo, rota la familia y devuelve ambas cookies
  igual que el login, con `Cache-Control: no-store`.
- **Cualquier fallo responde 401 exacto**, borra ambas cookies con los mismos atributos
  (`Path`, `Secure`, `HttpOnly`, `SameSite`) y no revela el motivo al cliente.
- Rate limit en memoria por IP (`refresh-ip:`), por `familyId` (`refresh-familia:`) y por usuario
  (`refresh-usuario:`), además del de login.

### `POST /usuario/logout`

- Revoca la familia leída del access (incluso si ya expiró) y mete el `jti` del access en la
  deny-list.
- Expira `FINAPP_AUTH` y `FINAPP_REFRESH` con `Set-Cookie` y `Cache-Control: no-store`.

### Cambio de contraseña / baja / desactivación

- `MUsuario.activo` vale `true` y `credential_version=0` por defecto.
- `SUsuario.modificar` incrementa `credentialVersion` **solo cuando cambia la contraseña** y revoca
  todas las familias si cambió contraseña, se desactivó la cuenta o cambió el `idUsuario`.
- `SUsuario.eliminar` revoca todas las familias antes de borrar el usuario.
- No se reemite el access al editar el perfil: si se revocó la sesión, el cliente debe renovar o
  iniciar sesión de nuevo.

## Detección de reuso

`SRefreshFamilias.evaluarReuso(...)` devuelve:

- `VIGENTE`: el hash coincide con el actual. Se rota normalmente.
- `ANTERIOR_DENTRO_GRACIA`: coincide con el anterior y `ahora - usedAt <= 15 s`. Se tolera y se rota.
- `REUSO_DETECTADO`: coincide con el anterior fuera de gracia. Se revoca la familia y se responde 401.
- `DESCONOCIDO`: no coincide con ninguno. Se responde 401 sin revocar (podría ser un token malformado).

**Desviación documentada:** solo se persisten hashes, por lo que no es posible reenviar el refresh
“vigente” literal durante la gracia. En ese caso se emite un par nuevo y se rota de nuevo; el cliente
recibe una sesión válida y la familia queda consistente. Se registra el evento con `familyId` e IP,
nunca con tokens.

## Dirección IP y proxies

`IpCliente` aplica este orden **solo si el peer (`remoteAddr`) está en
`finapp.seguridad.proxies-confiables`**:

1. `CF-Connecting-IP`
2. `X-Forwarded-For` (primera IP válida)
3. `X-Real-IP`
4. `remoteAddr`

Si el peer no es de confianza, se ignoran todas las cabeceras reenviadas: un cliente que alcance el
puerto directamente no puede falsificar su IP. Por defecto solo se confía en `127.0.0.1/32` y
`::1/128`, que es lo que usa cloudflared en el mismo host.

## Configuración

Properties y variables de entorno:

| Property                                   | Variable                                   | Default                |
| ------------------------------------------ | ------------------------------------------ | ---------------------- |
| `finapp.sesion.access-seconds`             | `FINAPP_SESION_ACCESS_SECONDS`             | `900`                  |
| `finapp.sesion.refresh-inactivity-seconds` | `FINAPP_SESION_REFRESH_INACTIVITY_SECONDS` | `1800`                 |
| `finapp.sesion.absolute-seconds`           | `FINAPP_SESION_ABSOLUTE_SECONDS`           | `28800`                |
| `finapp.sesion.reuse-grace-seconds`        | `FINAPP_SESION_REUSE_GRACE_SECONDS`        | `15`                   |
| `finapp.sesion.limpieza-intervalo-ms`      | `FINAPP_SESION_LIMPIEZA_INTERVALO_MS`      | `3600000`              |
| `finapp.seguridad.proxies-confiables`      | `FINAPP_PROXIES_CONFIABLES`                | `127.0.0.1/32,::1/128` |

`SameSite=Strict` es viable porque la web y la API están bajo el mismo sitio a través del túnel/proxy.
Si se sirven desde dominios distintos, hay que revisar esta decisión y el CORS.

## Base de datos

Ejecutar `sql/075_refresh_familias.sql` (MySQL). Crea `refresh_familia`, añade `activo` y
`credential_version` a `usuario` si faltan, y inicializa los valores por defecto. La limpieza
programada (`SRefreshFamilias.limpiarVencidas`) borra familias cuyo tope absoluto pasó, reteniendo la
gracia para no perder señales de reuso.

## Cliente web

`finApp-web/src/services/httpServicio.js`:

- `credentials: "include"` en todas las peticiones; nunca lee ni escribe las cookies, porque son HttpOnly.
- Interceptor 401: renueva una sola vez (single-flight) y repite la petición original. Excluye
  `login`, `refresh`, `logout` y `admin-login` para evitar bucles.
- Coordinación entre pestañas con `navigator.locks` y `BroadcastChannel`, para que solo una rote el
  refresh aunque varias pestañas reciban 401 a la vez.
- Renovación proactiva a los 13 min, solo si hubo actividad real desde el último refresco: una
  pestaña inactiva **no** extiende la ventana de inactividad.
- Si el refresh falla, emite `finapp:sesion-requerida` con `detail.motivo`, guarda el aviso en
  `sessionStorage` y `AuthModal` lo muestra al reabrir el acceso:
  “Tu sesión expiró por inactividad…”.

## Riesgos y deuda conocida

1. **Puerto del backend fuera del túnel.** Si el puerto del backend queda expuesto directamente,
   cualquiera puede llamar `login`/`refresh` contra él. Las cabeceras de proxy se ignoran cuando el
   peer no es de confianza, pero el endpoint sigue accesible. Mantener el puerto cerrado y publicar
   solo a través de Cloudflare Tunnel / proxy inverso.
2. **Rate limit en memoria.** `SIntentosRateLimit` no se comparte entre réplicas; tras un reinicio se
   pierde. Para múltiples instancias hay que moverlo a Redis o similar.
3. **Deny-list en memoria.** Igual que el rate limit: tras un reinicio se pierde. Las familias sí son
   persistentes, así que el refresh sigue revocado; el access individual revocado por logout podría
   volver a ser válido hasta su `exp` si la JVM se reinicia.
4. **Compatibilidad heredada.** Los tokens sin claim `tipo` siguen aceptándose como access con
   comprobación de actividad (usados por `admin-login` y `docs/generar_token_swagger.py`). Está marcado
   con `TODO(retirar tras la migración)` en `JwtTokenService`.
5. **Desviación de gracia por hashes.** Documentada arriba: no se puede reenviar el token vigente
   literal sin guardarlo en claro.
6. **Logs.** No se registran tokens ni cookies; solo `familyId`, usuario, IP y motivo. Aun así, conviene
   revisar los logs de acceso del proxy para no incluir cabeceras de autorización.
