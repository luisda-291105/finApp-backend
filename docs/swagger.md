# Documentación de la API

FinApp publica su especificación OpenAPI y la interfaz interactiva de Swagger UI desde el backend.

## Rutas

- Swagger UI: `http://localhost:8081/docs`
- Especificación OpenAPI en JSON: `http://localhost:8081/api-docs`

Sin sesión activa, la especificación JSON devuelve **solo las rutas públicas**; con sesión devuelve la API completa.

Las operaciones están agrupadas por dominio: sesión y usuarios, Gmail, asistente, bolsillos, contactos, fuentes, grupos, movimientos, muestras, tarjetas y suscripciones.

El CRUD de suscripciones manuales (tabla única `suscripcion`, con `origen` MANUAL/GMAIL) está documentado en [`docs/suscripciones.md`](suscripciones.md).

## Llegada de correos Gmail (cola por mensaje)

Los correos entrantes ya no se procesan dentro del webhook: cada mensaje queda **en espera en la
base de datos** (tabla `gmail_entrada_pendiente`, entidad `MGmailEntradaPendiente`) y un worker
lo descarga y guarda de a uno por vez. Un fallo o un reinicio del backend no pierde correos.

| Pieza | Comportamiento |
|-------|----------------|
| `POST /api/gmail/webhook/pubsub` | Valida el token OIDC y **solo encola** la notificación; responde `202 Accepted` |
| `POST /api/gmail/cuentas/{cuentaId}/watch` | Registra el watch y **encola un trigger inmediato** (no espera el ciclo de 5 min) |
| `POST /api/gmail/callback` (conectar cuenta) | Registra el watch y encola la primera sincronización |
| `procesarEntradasPendientes` (`@Scheduled`) | Worker cada `finapp.google.entradas-intervalo-ms` (10 s por defecto): expande el `historyId` a mensajes y procesa uno por uno |
| `sincronizarCuentasGmail` (5 min) | Red de seguridad: si el `historyId` cambió, encola la llegada |
| `GET /api/gmail/cuentas/{cuentaId}/estado` | Estado de la cola y la sincronización de la cuenta (lo consume la bandeja del frontend) |

Estados de cada entrada: `EN_ESPERA` → `PROCESANDO` → se elimina al guardar el mensaje, o
`ERROR` terminal tras 5 intentos con backoff (30 s, 60 s, 120 s, 240 s, 480 s). Las entradas
en `ERROR` quedan guardadas con `ultimo_error` para inspección. Si el backend se reinicia con
entradas en `PROCESANDO`, el worker las devuelve a `EN_ESPERA` tras 10 minutos sin avanzar.

`GET /api/gmail/cuentas/{cuentaId}/estado` devuelve el resumen para la UI:

```json
{
  "cuenta_id": "…", "correo": "…@gmail.com",
  "actualizando": true, "en_espera": 3, "procesando": 1, "errores": 0,
  "ultimo_error": "", "ultima_sincronizacion": "2026-11-17T23:40:12Z",
  "watch_expira_en": "2026-11-18T23:40:12Z"
}
```

La bandeja del frontend consulta este estado cada 15 s mientras está abierta; cuando la cola
drena invalida la caché de mensajes y los recarga sin esperar el ciclo de 5 minutos.

Instalaciones sin `ddl-auto=update` deben aplicar `sql/073_cola_entrada_gmail.sql`.

## Papelera de correos (Gmail)

Gestión de la Papelera de Gmail desde FinApp: mover un correo a la papelera, listar la Papelera real
en vivo y restaurar. Todas las rutas viven bajo `/api/gmail/cuentas/{cuentaId}/…` y exigen sesión
activa; `{cuentaId}` es una fuente de tipo `CORREO` con conexión Gmail del usuario (si no existe o no
es suya, `404`). Documentado en [`specs/040-papelera-correos/contrato-api.md`](../specs/040-papelera-correos/contrato-api.md).

| Método | Ruta | Propósito |
|--------|------|-----------|
| `GET` | `/api/gmail/cuentas/{cuentaId}/papelera` | Listar la Papelera en vivo (TRASH de Gmail), con paginación por `page_token` |
| `POST` | `/api/gmail/cuentas/{cuentaId}/mensajes/{mensajeId}/papelera` | Mover un correo a la papelera (única vía manual, desde el detalle) |
| `POST` | `/api/gmail/cuentas/{cuentaId}/mensajes/{mensajeId}/restaurar` | Restaurar un correo desde la papelera |
| `GET` | `/api/gmail/cuentas/{cuentaId}/papelera/configuracion` | Leer la configuración por categoría de la cuenta |
| `PUT` | `/api/gmail/cuentas/{cuentaId}/papelera/configuracion` | Actualizar la configuración (`advertencia_aceptada` obligatoria para activar UPDATES) |
| `GET` | `/api/gmail/cuentas/{cuentaId}/papelera/lista-blanca` | Listar los remitentes de la lista blanca |
| `POST` | `/api/gmail/cuentas/{cuentaId}/papelera/lista-blanca` | Añadir un remitente a la lista blanca (`201`) |
| `DELETE` | `/api/gmail/cuentas/{cuentaId}/papelera/lista-blanca/{remitenteId}` | Quitar un remitente de la lista blanca (`204`) |
| `POST` | `/api/gmail/cuentas/{cuentaId}/papelera/vista-previa` | Vista previa de la limpieza: elegibles, remitentes por categoría y excluidos por motivo |
| `POST` | `/api/gmail/cuentas/{cuentaId}/papelera/limpieza` | Iniciar la limpieza explícita en lote (`202`) |
| `GET` | `/api/gmail/cuentas/{cuentaId}/papelera/limpieza/{loteId}` | Consultar el estado de un lote de limpieza |

Mover y restaurar son **idempotentes** respecto al estado final: repetir la operación sobre un correo
ya en el estado pedido responde `200` sin efectos adicionales. El criterio de papelera lo define
Gmail: un correo está en la Papelera si y solo si está en el TRASH de Gmail.

### Errores

El cuerpo de error sigue la convención de `CGmail` (`{ "message": … }`) y añade un flag booleano en
los casos que requieren una acción del usuario.

| Código | Flag | Cuándo | Acción esperada del cliente |
|--------|------|--------|------------------------------|
| `400` | — | Solicitud inválida (categoría, antigüedad, límite, remitente o `advertencia_aceptada` no válidos; falta `vista_previa_id`/`total_estimado` o `confirmado` no es `true`). | Corregir la solicitud. |
| `401` | — | No hay sesión activa. | Iniciar sesión. |
| `404` | — | La cuenta no existe o no es del usuario; el mensaje no pertenece a la cuenta; el lote o el remitente no existen. | — |
| `409` | `requires_scope_authorization: true` | Falta el permiso ampliado `https://www.googleapis.com/auth/gmail.modify` en la cuenta. | Ofrecer la autorización del permiso ampliado (RF-23/RF-24). |
| `409` | `requires_reconnect: true` | La conexión de la cuenta fue revocada o expiró. | Pedir reconectar la cuenta (RF-26). |
| `409` | `mail_unavailable: true` | El correo ya no está disponible en la vista Papelera (la reconciliación lo quitó). | Informar sin presentarlo como fallo (RF-20). |
| `409` | `limpieza_en_curso: true` | Ya hay una limpieza en curso para esa cuenta (solo una por cuenta a la vez). | Informar y esperar a que termine la limpieza en curso. |
| `409` | `limite_ejecucion_alcanzado: true` | Una ejecución de la regla o de la limpieza alcanzó el límite por ejecución y queda remanente para la siguiente. | Informar del remanente; la siguiente ejecución lo retoma. |
| `409` | `conteo_cambiado: true` | El conteo real de la limpieza difiere del `total_estimado` confirmado en la vista previa. | Pedir una nueva vista previa y confirmación. |
| `410` | — | El correo ya fue eliminado definitivamente por Gmail (purgado). | Avisar de que no es recuperable y quitarlo de la vista (RF-18/RF-19). |
| `429` | — | Cuota de Gmail agotada. | Reintentar más tarde con espera progresiva. |
| `502` / `503` | — | Gmail no responde o falla (listar, mover, restaurar, evaluar categorías); `503` también si la cola de limpiezas está ocupada. | Mostrar aviso de error y permitir reintentar (RF-11/RF-05/RF-16/CD-04). |

**Permiso ampliado vs. reconexión.** `requires_scope_authorization` (también `409`) significa que
falta el permiso ampliado `gmail.modify`: el usuario debe **autorizar** ese permiso (el frontend lo
pide con `incluirModificar` reutilizando `POST /api/gmail/callback`). `requires_reconnect` (también
`409`) significa que la **conexión fue revocada o expiró** y el usuario debe **reconectar** la
cuenta. Son casos distintos y se atienden de forma distinta.

**Evaluación en vivo.** La regla automática por categorías evalúa **en vivo** con `messages.list`
(el TRASH real y el buzón completo de Gmail, incluidos correos nunca sincronizados), **no** desde la
caché local de FinApp ni desde el marcador de movimiento contable. Por eso la vista Papelera puede
mostrar y restaurar correos con antigüedad mayor que la retención de la caché (RF-22).

**Ambigüedad conocida del contrato.** En `GET …/papelera`, el campo `total` es el **tamaño de la
página devuelta**, no un conteo global de la papelera: `messages.list` de Gmail no ofrece un conteo
fiable del total. Decidido e implementado así en T3; la paginación real se hace con
`siguiente_page_token` y el parámetro `page_token`.

**Migración.** Instalaciones sin `ddl-auto=update` deben aplicar `sql/075_papelera_correos.sql`
(idempotente) para crear las columnas de papelera en `gmail_mensaje`, `fuente.gmail_scope_concedido`
y las tablas `gmail_papelera_configuracion`, `gmail_papelera_categoria` y `gmail_papelera_lista_blanca`.

## Almacenamiento en la nube (B1–B8)

Rutas del dominio `finapp.almacenamiento.*`, con proveedor por conexión (`GOOGLE_DRIVE`, `NEXTCLOUD`) vía `SArchivosPorConexion`.

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/almacenamiento/conexiones` | Listar conexiones del usuario | Manual |
| `GET` | `/api/almacenamiento/conexiones/{idConexion}` | Obtener conexión | Manual (ownership) |
| `POST` | `/api/almacenamiento/conexiones/iniciar` | Iniciar OAuth nuevo almacenamiento | Manual |
| `GET` | `/api/almacenamiento/conexiones/callback` | Callback OAuth | Público* (valida `state`) |
| `DELETE` | `/api/almacenamiento/conexiones/{idConexion}` | Desconectar | Manual (ownership) |
| `GET` | `/api/almacenamiento/conexiones/nextcloud` | Listar archivos Nextcloud | Manual (ownership) |

### Archivos sobre una conexión

| Método | Ruta | Función | Body/Params |
|--------|------|---------|-------------|
| `GET` | `/api/almacenamiento/conexiones/{idConexion}/archivos` | Listar (carpetaId, vista, busqueda, orden, ordenCampo, ordenDireccion, pagina) | Query |
| `GET` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}` | Metadatos | Path |
| `GET` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/contenido` | Contenido (descargar=false stream inline) | Path + `descargar` |
| `GET` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/previsualizacion` | Previsualización (stream inline) | Path |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/carpeta` | Crear carpeta | `SolicitudCarpeta` |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/subir` | Subir archivo (multipart, 100 MB) | `MultipartFile archivo` + `idPadre` |
| `PATCH` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}` | Renombrar / mover / destacar | `SolicitudActualizar` |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/copiar` | Copiar | `SolicitudCarpeta` |
| `DELETE` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}` | Papelera | — |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/restaurar` | Restaurar | — |
| `DELETE` | `/api/almacenamiento/conexiones/{idConexion}/archivos/papelera` | Vaciar papelera | — |
| `GET` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/permisos` | Listar permisos | — |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/permisos` | Compartir | `SolicitudPermiso` |
| `DELETE` | `/api/almacenamiento/conexiones/{idConexion}/archivos/{fileId}/permisos/{permisoId}` | Revocar permiso | — |
| `POST` | `/api/almacenamiento/conexiones/{idConexion}/archivos/lote` | Lote (ZIP en backend) | `SolicitudLote` |

Vistas soportadas: `unidad`, `recientes`, `compartidos`, `destacados`, `papelera`. Orígenes permitidos: escrituras con `Origin` en `WEB_ALLOWED_ORIGINS` (validación CSRF), nunca con `X-Requested-With` solamente.

## Cookie de sesión y orígenes permitidos

La sesión viaja en la cookie `FINAPP_AUTH` (`HttpOnly`). Su comportamiento se ajusta con el entorno:

| Variable | Default | Para qué sirve |
|----------|---------|----------------|
| `FINAPP_JWT_COOKIE_SECURE` | `true` | `Secure` en la cookie. Chrome y Firefox aceptan cookies `Secure` en `http://localhost`; en producción debe quedar `true` (HTTPS). |
| `FINAPP_JWT_COOKIE_SAME_SITE` | `Lax` | `Lax` cuando la web y la API comparten sitio. Usa `None` si la web se sirve en otro dominio (por ejemplo `finapp.luisda.org`) y la API en `localhost:8081`; `None` obliga a `Secure`. |
| `WEB_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Orígenes permitidos para CORS y para validar el origen de las escrituras (protección CSRF). Se comparan **sin distinguir mayúsculas** y sin barra final, pero conviene escribirlos en minúsculas. |

Errores frecuentes:

- `401 {"mensaje":"Inicia sesión para continuar."}` → la petición **no envió cookie**: falta iniciar sesión,
  la cookie no se guardó (p. ej. `Secure` en un sitio HTTP que no sea `localhost`) o el navegador bloqueó
  cookies de terceros (la web y la API están en sitios distintos). La solución robusta es servir la API
  en el **mismo origen** que la web.
- `401 {"mensaje":"Tu sesión expiró o se cerró. Inicia sesión de nuevo."}` → había cookie, pero la sesión
  venció (30 min de inactividad por `FINAPP_JWT_INACTIVITY_SECONDS`) o se cerró con `POST /usuario/logout`.
- `403` en escrituras → el `Origin`/`Referer` no está en `WEB_ALLOWED_ORIGINS`.

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

## Almacenamiento en la nube (resumen B1–B8)

- **Conexiones y tokens:** OAuth Google Drive con scope `https://www.googleapis.com/auth/drive openid email profile`;
  refresh token renova access token; fallo → conexión `REQUIERE_RECONEXION` (`409` con código `CONEXION_REQUIERE_RECONEXION`).
- **Cifrado:** AES/GCM con `FINAPP_ALMACENAMIENTO_CIPHER_SECRET` (clave propia; ya no se deriva del JWT).
- **Proveedores:** `GoogleDriveArchivos` (Drive v3) y `NextcloudArchivos` (WebDAV), ambos tras `SArchivosPorConexion`
  (router por `proveedor`); el proveedor no soportado responde `400 AlmacenamientoProveedorNoSoportado`.
- **Errores operativos:** proveedor 401 → renovar y reintentar → si falla, `409 CONEXION_REQUIERE_RECONEXION`;
  403 → reconexión; 404 → `AlmacenamientoRecursoNoEncontrado`; 429 → cuota agotada; 5xx → backoff.
- **Listados:** caché de 60 s por conexión+carpeta (`SCacheListadosAlmacenamiento`), invalidada tras escrituras.
- **Subidas:** multipart con `spring.servlet.multipart.max-file-size=100MB` (y `max-request-size=110MB`).

La documentación de los servicios y controladores se mantiene en el cerebro de desarrollo:
[`Controller/CAlmacenamiento.md`](../../Nextcloud/cerebro/desarrollo/finApp/backend/Controller/CAlmacenamiento.md) y
[`Services/README.md`](../../Nextcloud/cerebro/desarrollo/finApp/backend/Services/README.md).
