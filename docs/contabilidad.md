# Contabilidad: contrato, cálculos y persistencia

Estado: documento técnico vivo<br>
Fecha: 2026-11-18<br>
Alcance: backend `finApp-backend` (Spring Boot 4.1.2 / Java 25) y consumidor web `finApp-web`.

Este documento describe **cómo funciona realmente la contabilidad de FinApp**: de dónde salen los
movimientos, qué se persiste, cómo se calculan los totales, qué respuestas devuelve la API y qué
errores son esperables. Está escrito a partir del código y las pruebas; cuando una convención no es
uniforme en el proyecto se indica explícitamente en lugar de inventar una regla.

## Índice

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Convenciones de la API](#2-convenciones-de-la-api)
3. [Autenticación, orígenes HTTP y seguridad](#3-autenticación-orígenes-http-y-seguridad)
4. [Orígenes de los movimientos](#4-orígenes-de-los-movimientos)
5. [Persistencia](#5-persistencia)
6. [Endpoints del núcleo contable](#6-endpoints-del-núcleo-contable)
7. [Cálculos](#7-cálculos)
8. [Flujos de negocio](#8-flujos-de-negocio)
9. [Errores esperados](#9-errores-esperados)
10. [Configuración relevante](#10-configuración-relevante)
11. [Integración con finApp-web](#11-integración-con-finapp-web)
12. [Observaciones y deuda conocida](#12-observaciones-y-deuda-conocida)
13. [Referencias rápidas](#13-referencias-rápidas)

---

## 1. Resumen ejecutivo

- Un usuario tiene **una contabilidad** (`contabilidad`, relación 1:1 con `usuario`) y dentro de ella
  varios **bolsillos** (`bolsillo`). Dos bolsillos son fijos y se crean automáticamente:
  `Ingresos` (tipo `INGRESO`) y `Gastos` (tipo `GASTO`). Existe además un bolsillo interno
  `Por revisar` (tipo `POR_REVISAR`) que no se muestra en el listado normal.
  (`SBolsillo.java:26-29`, `SBolsillo.java:43-65`, `SBolsillo.java:157-162`.)
- Los movimientos viven en `movimiento_contable`. Pueden estar **asociados a un bolsillo** o ser
  **globales del usuario** (`id_bolsillo = NULL`, `id_usuario` informado). Los globales se usan para
  importaciones de Gmail cuando no hay un bolsillo destino inequívoco.
  (`MMovimientoContable.java:15-20`, `sql/069_movimientos_globales.sql:1-10`.)
- Todo movimiento tiene un tipo contable (`INGRESO`, `GASTO`, `AHORRO`, `TRANSFERENCIA`) **y** un
  estado de revisión independiente (`POR_REVISAR` o `CONFIRMADO`). Los totales solo incluyen los
  confirmados, salvo los contadores explícitos de «por revisar».
  (`EstadoRevisionMovimiento.java:3-7`, `IMovimientoContable.java:24-31`.)
- Los orígenes se codifican en `origen_tipo` / `origen_id` / `origen_nombre`: `GMAIL`, `BOLSILLO`,
  `CUENTA`, `EFECTIVO` o sin origen. Gmail usa la referencia estable `"{cuentaId}:{mensajeId}"`.
  (`SMovimientoContable.java:556-612`, `sql/058_origen_correo_movimientos.sql:1-4`.)
- Los envíos de Gmail que dicen «Transferiste … a la cuenta *X» se guardaron históricamente como
  `GASTO`. Si `X` es una **cuenta propia** configurada (`25310487123` por defecto), la fila se
  reclasifica de verdad a `TRANSFERENCIA` y queda como movimiento global (`id_bolsillo = NULL`).
  Ya no hay una corrección solo en el resumen: el `tipo` persistido es la única fuente de verdad.
  (`sql/076_reclasificar_transferencias_cuenta_propia.sql`, `SMovimientoContable.java:236-255`.)

---

## 2. Convenciones de la API

| Aspecto | Regla real |
|---------|-----------|
| Formato | JSON. No hay `spring.jackson.*` configurado: los DTO `record` se serializan en **camelCase** (`idMovimiento`, `estadoRevision`, `montoPorRevisar`). |
| DTO de entrada/salida | `record` inmutables de Java. No usan `@JsonProperty` en este módulo. |
| Dinero | `BigDecimal`, 2 decimales, moneda implícita COP. `movimiento_contable.monto` es `DECIMAL(19,2)`. |
| Fechas | `LocalDate` → `"YYYY-MM-DD"`. El parámetro `mes` del resumen usa `YearMonth` → `"YYYY-MM"`. |
| Errores de controlador | `RespuestaModalDTO`: `{"tipo":"error","mensaje":"...","campo":"...","datos":null}`. |
| Errores globales | `ManejoErroresApi`: `{"mensaje":"..."}` (clave en español, sin `tipo`/`campo`). |
| Errores de Gmail | Algunos controladores de Gmail usan `{"message":"..."}` (clave en inglés); ver sección 9. |
| Sesión | Cookie `FINAPP_AUTH` o cabecera `Authorization: Bearer <jwt>`. |
| Escrituras | Requieren cabecera `Origin` (o `Referer`) permitida; si no, `403` sin cuerpo. |

Fuentes: `MovimientoContableDTO.java:7-20`, `RespuestaModalDTO.java:3-5`,
`ManejoErroresApi.java:26-61`, `JwtTokenService.java:27,195-206`,
`ValidacionOrigenInterceptor.java:39-65`, `specs/040-papelera-correos/contrato-api.md:19-23`.

---

## 3. Autenticación, orígenes HTTP y seguridad

### 3.1 Sesión

Todas las rutas de contabilidad y bolsillos pasan por `UsuarioActivoInterceptor`
(`CorsConfig.java:37-45`):

- Sin token: `401` con
  `{"tipo":"error","mensaje":"Inicia sesión para continuar.","campo":null,"datos":null}`
  y se envía una cookie expirada.
- Con token inválido/expirado: `401` con
  `{"tipo":"error","mensaje":"Tu sesión expiró o se cerró. Inicia sesión de nuevo.","campo":null,"datos":null}`.
- El `OPTIONS` de CORS no exige sesión.
- `/docs` y `/api-docs` pueden ser públicos según `finapp.docs.publico` (por defecto `true`); sin
  sesión se entrega una especificación reducida con solo login/registro/asistente público.
- Swagger define dos esquemas: `JwtFinAppBearer` (JWT pegado en Authorize) y `JwtFinApp` (cookie
  `FINAPP_AUTH`). El token de prueba puede generarse con `python docs/generar_token_swagger.py <idUsuario>`.

(`UsuarioActivoInterceptor.java:29-95`, `EspecificaPublicaApi.java:21-60`, `OpenApiConfig.java:14-44`,
`application.properties:24-26`.)

### 3.2 Orígenes web permitidos (CORS / CSRF)

`finapp.cors.allowed-origins` define la lista; se normaliza en `OrigenesWeb` (minúsculas, sin barra
final). CORS responde con credenciales y devuelve exactamente el origen permitido. Para `POST`,
`PUT`, `PATCH` y `DELETE`, `ValidacionOrigenInterceptor` exige que `Origin` (o `Referer`) esté en la
lista; rutas exentas: `/api/gmail/webhook/pubsub`, `/asistente/publico`, `/usuario/login`,
`/usuario/admin-login`, `/usuario`. Un origen no permitido responde **`403` sin cuerpo**.
(`CorsConfig.java:30-64`, `ValidacionOrigenInterceptor.java:21-27,39-65`.)

### 3.3 Cabeceras de seguridad

Todas las respuestas incluyen `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: no-referrer`, HSTS, CSP `default-src 'self'`, `X-XSS-Protection: 0` y
`Permissions-Policy` restrictiva. (`CabecerasSeguridadFilter.java:21-31`.)

---

## 4. Orígenes de los movimientos

`origen_tipo` es un `VARCHAR(20)` libre, pero el servicio solo genera estos valores:

| `origenTipo` | `origenId` | `origenNombre` | Cuándo se usa | Regla |
|--------------|------------|----------------|---------------|-------|
| `GMAIL` | `"{cuentaId}:{mensajeId}"` | Remitente (p. ej. `banColombia`) | Importación automática o sugerencia por revisar | Obliga a identifier + remitente no vacíos. Deduplica por `(GMAIL, origenId)`. |
| `BOLSILLO` | `idBolsillo` de origen | Nombre del bolsillo | Transferencias entre bolsillos | El bolsillo de origen debe ser distinto del destino, estar activo y ser de tipo `INGRESO` o `AHORRO`. |
| `CUENTA` | `idTarjeta` | `"{entidad} · {titular}"` | Gasto pagado desde tarjeta/cuenta propia | La tarjeta debe existir y ser del usuario. También vincula `id_tarjeta`. |
| `EFECTIVO` | `null` | `Efectivo` | Gasto en efectivo | No vincula tarjeta. |
| `null` | `null` | `null` | Ingreso/gasto manual sin origen explícito | Para `INGRESO`/`GASTO` sin origen reconocido, `aplicarOrigen` limpia los tres campos. |

Una **transferencia** siempre requiere `origenTipo = BOLSILLO`; si no, se rechaza con
`"Una transferencia requiere origen en otro bolsillo."`. Los tipos `INGRESO` y `GASTO` permiten
origen `GMAIL`, `EFECTIVO`, `BOLSILLO`, `CUENTA` o ninguno; cualquier otro valor se rechaza con
`"Selecciona de dónde salió el dinero."`.
(`SMovimientoContable.java:556-599`, `SMovimientoContable.java:602-612`.)

### 4.1 Formato Gmail

La referencia se guarda como `cuentaId:mensajeId` para poder reconstruir la cuenta y el mensaje sin
tablas intermedias. La consulta de transferencias propias extrae el mensaje con
`SUBSTRING(m.origen_id, LOCATE(':', m.origen_id) + 1)` — portable en MySQL y H2 — y lo une con
`gmail_mensaje.google_message_id`. (`IMovimientoContable.java:49-57`, `sql/058...:1-4`.)

Aunque se menciona "cuenta Gmail", internamente es una `fuente` con `tipo = CORREO`; los tokens de
OAuth, `history_id`, expiración del watch, última sincronización y el scope concedido se guardan
cifrados u ocultos a JSON en esa misma entidad. Una fuente se considera conectada si tiene refresh
token o access token vigente, y el permiso ampliado se detecta por `gmail.modify`.
(`MFuente.java:19-40,49-76,143-170`, `SFuente.java:36-77`.)

No existen valores `TELEGRAM`, `TARJETA` ni `SUSCRIPCION` en `origen_tipo`: tarjetas y suscripciones
son datos vinculados al movimiento, no orígenes. (`SMovimientoContable.java:556-600`.)

### 4.2 Pipeline de Gmail hasta el movimiento contable

1. **Conexión**: `POST /api/gmail/callback` canjea el código OAuth, crea o reutiliza la fuente
   `CORREO`, cifra los tokens, guarda el scope, exige `gmail.modify`, registra el watch sobre
   `INBOX` y encola una primera sincronización. Sin el scope ampliado: `409 requires_scope_authorization`.
   (`CGmail.java:140-252,220,1296-1309`.)
2. **Notificación**: el watch (`POST .../watch`) y el webhook Pub/Sub (`POST /api/gmail/webhook/pubsub`)
   validan el JWT OIDC, extraen `emailAddress`/`historyId`, localizan las fuentes conectadas con ese
   correo y encolan una entrada por cuenta, respondiendo `202`. (`CGmail.java:612-668,801-827`.)
3. **Tareas programadas**: renovación de watch, sincronización cada `finapp.google.sync-interval-ms`
   (300000 ms), purga de caché, worker de cola (10000 ms) y regla automática de papelera.
   (`CGmail.java:670-799`, `SReglaPapeleraGmail.java:173-203`.)
4. **Sincronización y caché**: se buscan mensajes de hasta 90 días de remitentes prioritarios,
   suscriptores, `IMPORTANT` y direcciones propias; se descargan con `format=full` y se guarda en
   `gmail_mensaje` solo lo relevante, cifrando el contenido con AES-GCM. Los mensajes en `TRASH` o
   con más de 90 días se descartan de la caché. (`CGmail.java:837-956`, `SGmailMensajes.java:113-123,167-229,386-413`.)
5. **Cola persistente**: `gmail_entrada_pendiente` deduplica por `(id_fuente, google_message_id)` y
   usa estados `EN_ESPERA`, `PROCESANDO`, `ERROR`. En fallo aplica backoff de 30/60/120/240/480 s;
   tras 5 intentos queda en `ERROR`, y las entradas `PROCESANDO` con más de 10 minutos vuelven a la
   cola. (`MGmailEntradaPendiente.java:23-63`, `SGmailEntradas.java:26-33,45-61,67-120,161-179`.)
6. **Worker y estados por correo**: drena hasta 50 entradas, descifra, aplica el parser, clasifica y
   crea el movimiento. El mensaje queda como `IGNORADO`, `PENDIENTE_BOLSILLO`, `REGISTRADO`,
   `DUPLICADO` o `ERROR`. Solo se reprocesan automáticamente los `PENDIENTE`: un
   `PENDIENTE_BOLSILLO` **no** se reintenta solo. (`CGmail.java:760-799,971-1037`.)
7. **Decisión contable**: si la clasificación pide revisión o el bolsillo no es único/compatible,
   va a `Por revisar`; si hay un único bolsillo compatible se usa; si hay varios y el tipo es
   `GASTO`/`INGRESO`, se crea un movimiento global confirmado. Antes de crear se deduplica por
   `(GMAIL, origenId)`; el worker detecta el texto `ya fue importado` y marca `DUPLICADO`.
   (`SMovimientoContable.java:346-402,415-436`, `CGmail.java:1018-1023`.)
8. **Alerta**: al registrar se activa `movimiento_alerta_pendiente`; se consulta con
   `GET /api/gmail/movimientos/notificaciones` y se descarta con
   `POST /api/gmail/movimientos/notificaciones/confirmar`, que **no modifica** `movimiento_contable`
   (máximo 100 ids, responde `204`). (`CGmail.java:579-610`.)
9. **Importación por lote**: `POST /api/gmail/cuentas/{cuentaId}/suscripciones/importar-lote` filtra
   el remitente bancario oficial (excluye el de mercadeo), parsea con remitente verificado, crea el
   movimiento y devuelve por mensaje `IMPORTADO`, `POR_REVISAR`, `DUPLICADO` o `ERROR`; responde
   `202` al encolar y el progreso se consulta en `GET .../lotes/{loteId}`.
   (`SImportacionLoteGmail.java:21-145`, `CGmail.java:509-577`.)

### 4.3 Parser y clasificador

- **Parser Bancolombia**: reconoce etiquetas como `valor`, `monto`, `pagaste`, `recibiste`,
  `transferiste` y la cuenta origen `desde tu cuenta *(\d{4})`. Solo produce `INGRESO` o `GASTO`
  (nómina es ingreso) y devuelve vacío si no hay señal o si gasto e ingreso aparecen a la vez.
  Categorías: `Ingresos`, `Suscripciones`, `Pagos con tarjetas` si hay cuenta origen, o
  `Compras y pagos`. Concepto recortado a 160 caracteres. Normaliza miles/decimales y exige un
  `BigDecimal` positivo con escala 2. (`ParserMovimientoBancolombia.java:14-23,25-68,77-104`.)
- El flujo automático exige literalmente `bancolombia` en el cuerpo; el lote con remitente
  verificado usa las mismas reglas sin esa compuerta. El parser sigue produciendo `GASTO` con
  categoría `Pagos con tarjetas` para `Transferiste … desde tu cuenta *5201 a la cuenta *25310487123`,
  pero el servicio consulta el fragmento del correo y, si el destino es propio, persiste
  `TRANSFERENCIA` (categoría `Transferencia`) como movimiento global. Los envíos a terceros siguen
  siendo `GASTO`. (`ParserMovimientoBancolombia.java:38-68`, `SMovimientoContable.java:237-255`.)
- **Clasificador**: primero aplica las reglas activas del usuario (normalizadas sin acentos y con
  coincidencia por palabra/frase completa); si una coincide, devuelve confianza `1` y `revisar=false`.
  Si no, consulta al modelo con un prompt que trata remitente y mensaje como datos no confiables y
  pide JSON con `bolsillo`, `monto`, `comercio`, `fecha` y `confianza`. (`SClasificadorMovimiento.java:26-40`,
  `ClasificadorMovimiento.java:24-33`, `PromptClasificador.java:5-22`.)
- **Fallo seguro**: confianza < `0.75`, error del modelo, JSON inválido o validación fallida
  degradan a `TipoBolsillo.OTROS` con `revisar=true`. El validador solo acepta un objeto JSON plano
  de hasta 4000 caracteres y limita la confianza a `[0,1]`. (`ClasificadorMovimiento.java:15-16,34-62`,
  `ValidadorRespuesta.java:16-21,37-67,114-119`.)
- **Reglas aprendidas**: corregir un `POR_REVISAR` crea una regla `REMITENTE` a partir de
  `origenNombre`, única por `(id_bolsillo, tipo_regla, valor_normalizado)`. (`SReglaFiltro.java:16-49`,
  `MReglaFiltro.java:8-33`.)

---

## 5. Persistencia

### 5.1 Estrategia

- JPA/Hibernate con `spring.jpa.hibernate.ddl-auto=update` (`application.properties:6`).
- **No hay Flyway/Liquibase**. Los scripts en `sql/` son migraciones manuales ejecutadas en MySQL;
  por ejemplo `058` (amplía `origen_id`), `059` (consolida cuentas Gmail en `fuente`), `066`
  (estado de importación automática), `069` (movimientos globales), `070` (correo del usuario),
  `073` (cola de entrada Gmail), `074` (descripción/orígenes del bolsillo) y `075` (papelera).
- MySQL en producción; H2 en pruebas. Las consultas nativas se mantienen compatibles con ambos.
- Además, `MigracionMovimientosGlobales` es un `ApplicationRunner` que solo actúa en MySQL/MariaDB:
  vuelve nullable `id_bolsillo` si hace falta y rellena `id_usuario` desde `bolsillo` para filas
  históricas. (`MigracionMovimientosGlobales.java:26-49`.)

`contabilidad` es una **entidad JPA real**, no una vista (`MContabilidad.java:15-18`), con relación
1:1 con `usuario`.

### 5.2 Tablas principales

| Tabla | Columnas relevantes | Notas |
|-------|---------------------|-------|
| `contabilidad` | `id_contabilidad` (PK, 36), `id_usuario` (FK única), `nombre`, `valor` (`double`), `estado`, `fecha_creacion` | Se crea automáticamente con nombre `Contabilidad personal`, `valor=0`, `estado=true` si el usuario no la tiene. (`SBolsillo.java:207-225`, `MContabilidad.java:15-38`.) |
| `bolsillo` | `id_bolsillo` (PK, 36), `id_contabilidad` (FK), `id_usuario`, `nombre` (100), `tipo` (40), `monto_objetivo` `DECIMAL(19,2)`, `estado`, `fecha_creacion`, `descripcion` TEXT, `origenes` TEXT | `origenes` guarda un **array JSON serializado** de strings. `tipo` se normaliza a `INGRESO`/`GASTO`/`AHORRO`/`OTROS`/`POR_REVISAR`. (`MBolsillo.java:18-55,100-113`, `sql/074...`.) |
| `movimiento_contable` | `id_movimiento` (PK, 36), `id_bolsillo` nullable, `id_usuario`, `fecha`, `monto` `DECIMAL(19,2)`, `tipo` (40), `categoria` (80), `concepto` (160), `origen_tipo` (20), `origen_id` (180), `origen_nombre` (120), `id_tarjeta` (36), `estado_revision` (20) | `id_tarjeta` es referencia suave sin FK. `id_bolsillo` nullable desde `sql/069`. (`MMovimientoContable.java:8-56`.) |
| `gmail_mensaje` | `id_fuente`, `google_message_id` (únicos), `fragmento`, cabeceras, etiquetas, `list_unsubscribe`, contenido cifrado, `fecha_interna`, `movimiento_automatico_estado` (default `PENDIENTE`), `movimiento_automatico_id`, `movimiento_alerta_pendiente`, campos de papelera | `fragmento` es la evidencia usada para detectar transferencias a cuentas propias. El contenido se cifra con AES-GCM (nonce + ciphertext en Base64). (`MGmailMensaje.java:15-92`, `SGmailMensajes.java:386-413`, `sql/066...:1-7`.) |
| `gmail_entrada_pendiente` | `id`, `id_fuente` (FK), `google_message_id`, `history_id`, `estado`, `intentos`, `ultimo_error`, fechas | Cola drenada por el worker; `UNIQUE (id_fuente, google_message_id)` e índice por `(estado, fecha_creacion)`. (`sql/073...:1-19`, `MGmailEntradaPendiente.java:23-63`.) |
| `fuente` | `id_fuente`, `tipo`, `contenido`, tokens cifrados, `gmail_history_id`, `gmail_watch_expira_en`, `gmail_sincronizado_en`, `gmail_scope_concedido`, `id_usuario` | Las cuentas Gmail son fuentes de tipo `CORREO`; mensajes y suscripciones se eliminan en cascada. (`MFuente.java:19-85`, `sql/059...:23-55`.) |
| `regla_filtro` | `id_regla`, `id_bolsillo`, `tipo_regla`, `valor`, `valor_normalizado`, `activa` | Única por `(id_bolsillo, tipo_regla, valor_normalizado)`; normaliza automáticamente en `@PrePersist`/`@PreUpdate`. (`MReglaFiltro.java:8-55`.) |
| `tarjeta` | `id_tarjeta`, usuario, entidad, titular, PAN enmascarado | Se usa para vincular movimientos por sufijo de cuenta. (`SMovimientoContable.java:409-413`.) |

**Estado no persistido**: el progreso de los lotes de importación, las vistas previas de limpieza de
papelera y los lotes de limpieza viven en `ConcurrentHashMap` en memoria. Un reinicio los pierde;
las vistas previas duran 1800 s, los lotes terminados 24 h y solo puede haber una limpieza en curso
por cuenta. (`SImportacionLoteGmail.java:21-40,175-179`, `SRegistroPapeleraGmail.java:13-37,59-110`.)

### 5.3 Movimientos globales vs. por bolsillo

Un movimiento es **del usuario** cuando `b.idBolsillo IN :idsBolsillo` **o** cuando
`b IS NULL AND m.idUsuario = :idUsuario`. Todas las consultas de listado y resumen usan esa misma
condición para evitar fugas entre usuarios. (`IMovimientoContable.java:12-17,24-31`.)

`propio(idUsuario, idMovimiento)` autoriza con `m.idUsuario = :idUsuario OR b.idUsuario = :idUsuario`
(`IMovimientoContable.java:19-22`), de modo que un movimiento global y uno de bolsillo son editables
por su dueño.

### 5.4 Bolsillos fijos y de sistema

`SBolsillo.listar` garantiza que `Ingresos`/`INGRESO` y `Gastos`/`GASTO` existan, estén activos y
sean únicos; si hay duplicados, adopta el canónico, mueve sus movimientos y elimina el duplicado.
El bolsillo `Por revisar` se consulta por `GET /bolsillo/por-revisar` y se crea bajo demanda.
(`SBolsillo.java:43-105`, `CBolsillo.java:36-45`.)

---

## 6. Endpoints del núcleo contable

Base de la API: el backend expone estas rutas sin prefijo `/api` (swagger en `/docs`).

### 6.1 Movimientos — `CMovimientoContable`

| Método | Ruta | Parámetros | Respuesta | Efecto |
|--------|------|------------|-----------|--------|
| `GET` | `/movimiento` | `idBolsillo?`, `tipo=TODOS`, `estadoRevision?` | `200` → `[MovimientoContableDTO]` | Lista del usuario, ordenada por fecha DESC. |
| `GET` | `/movimiento/resumen` | — | `200` → `[ResumenMovimientoBolsilloDTO]` | Totales por bolsillo. |
| `GET` | `/movimiento/resumen-global` | `mes=YYYY-MM?` (por defecto mes actual) | `200` → `ResumenGlobalContabilidadDTO` | Resumen mensual agrupado por el `tipo` persistido. |
| `GET` | `/movimiento/recurrentes` | — | `200` → `[SuscripcionRecurrenteDTO]` | Detecta cobros repetidos en gastos confirmados. |
| `GET` | `/movimiento/correos` | `ids` (lista de referencias) | `200` → `[String]` | Referencias Gmail ya importadas por el usuario. |
| `POST` | `/movimiento` | `MovimientoContableSolicitudDTO` | `201` → `MovimientoContableDTO` | Crea movimiento. |
| `POST` | `/movimiento/gmail/por-revisar` | `MovimientoContableSolicitudDTO` (`origenTipo=GMAIL`) | `201` → `MovimientoContableDTO` | Crea sugerencia en `Por revisar`. |
| `POST` | `/movimiento/{id}/confirmar-global` | — | `200` → `MovimientoContableDTO` | Confirma Gmail `POR_REVISAR` como global. |
| `PUT` | `/movimiento/{id}` | `MovimientoContableSolicitudDTO` | `200` → `MovimientoContableDTO` | Actualiza y, si venía de revisión, registra corrección y confirma. |
| `DELETE` | `/movimiento/{id}` | — | `200` → `{"tipo":"exito","mensaje":"Movimiento eliminado.","campo":null,"datos":null}` | Borra la fila. |

Fuente: `CMovimientoContable.java:32-165`.

Observaciones del listado y del resumen:

- `estadoRevision` se convierte con `valueOf(trim().toUpperCase(Locale.ROOT))`; un valor inválido
  produce `400` con `campo = "tipo"`.
- Sin `idBolsillo` se incluyen los movimientos globales del usuario; **con** `idBolsillo` la query
  los trae pero el filtro en Java los descarta porque exige `getBolsillo() != null`.
  (`SMovimientoContable.java:78-87`, `IMovimientoContable.java:12-17`.)
- `/movimiento/resumen`, `/movimiento/recurrentes` y `/movimiento/correos` no tienen `try/catch`
  propio, así que un `NoSuchElementException` o un parámetro `ids` ausente termina en el `500`
  genérico. (`CMovimientoContable.java:52-85`.)

### 6.2 Bolsillos — `CBolsillo`

| Método | Ruta | Cuerpo | Respuesta | Efecto |
|--------|------|--------|-----------|--------|
| `GET` | `/bolsillo` | — | `200` → `[MBolsillo]` (serializado) | Lista bolsillos del usuario sin `Por revisar`; **escribe**: asegura/reactiva fijos, consolida duplicados y repara campos legacy. |
| `GET` | `/bolsillo/por-revisar` | — | `200` → `MBolsillo` / `404` | Bolsillo interno (`"Todavía no existe un bolsillo Por revisar."`). |
| `POST` | `/bolsillo` | `BolsilloSolicitudDTO` | `201` → `MBolsillo` | Solo permite tipo `AHORRO` para bolsillos creados por el usuario. |
| `PUT` | `/bolsillo/{id}` | `BolsilloSolicitudDTO` | `200` → `MBolsillo` | No permite editar fijos ni `Por revisar`. |
| `DELETE` | `/bolsillo/{id}` | — | `200` → `RespuestaModalDTO.exito` | No permite borrar fijos, `Por revisar` ni bolsillos con movimientos. |

Fuente: `CBolsillo.java:29-89`, `SBolsillo.java:43-100,107-133,135-199,234-254`.

> Aunque es un `GET`, `SBolsillo.listar` está `@Transactional` y muta datos: crea o reactiva
> `Ingresos`/`Gastos`, adopta homónimos del tipo correcto, mueve los movimientos de los duplicados
> al bolsillo canónico antes de borrarlos y normaliza `montoObjetivo`, `idUsuario` y `tipo` legacy.
> Conviene tratarlo como operación mutante a efectos de concurrencia y auditoría.

### 6.3 DTOs de entrada

```json
// MovimientoContableSolicitudDTO
{
  "idBolsillo": "53d177e4-a53e-4a99-a71e-d42930aa49cb",
  "tipo": "GASTO",
  "fecha": "2026-11-16",
  "monto": 1000000.00,
  "categoria": "Pagos con tarjetas",
  "concepto": "Transferencia",
  "origenTipo": "GMAIL",
  "origenId": "a1b2c3:18f2...",
  "origenNombre": "banColombia"
}
```

```json
// BolsilloSolicitudDTO
{
  "nombre": "Viaje",
  "estado": true,
  "montoObjetivo": 5000000.00,
  "tipo": "AHORRO",
  "descripcion": "Fondo de vacaciones",
  "origenes": ["Bancolombia", "Nómina"]
}
```

Validación de movimiento (`SMovimientoContable.java:510-531`): fecha obligatoria; monto > 0,
máximo 2 decimales y `precision <= 19`; `idBolsillo` obligatorio; categoría obligatoria y ≤ 80
caracteres; concepto ≤ 160; `origenId` ≤ 180. El tipo manual debe ser uno de los cuatro bases
(`SMovimientoContable.java:539-548`).

### 6.4 DTOs de salida

```json
// MovimientoContableDTO
{
  "idMovimiento": "aa087189-446b-4438-b2c2-26fd5139da7f",
  "idBolsillo": null,
  "nombreBolsillo": null,
  "fecha": "2026-11-16",
  "monto": 1000000.00,
  "tipo": "TRANSFERENCIA",
  "categoria": "Transferencia",
  "concepto": "Transferencia",
  "origenTipo": "GMAIL",
  "origenId": "a1b2c3:18f2...",
  "origenNombre": "banColombia",
  "estadoRevision": "CONFIRMADO"
}
```

```json
// ResumenGlobalContabilidadDTO — noviembre 2026 con el tipo TRANSFERENCIA persistido
{
  "ingresos": 2985989.00,
  "gastos": 2382337.00,
  "ahorros": 200000.00,
  "transferencias": 1000000.00,
  "saldoAhorros": 200000.00,
  "porRevisar": 5,
  "montoPorRevisar": 0.00
}
```

```json
// ResumenMovimientoBolsilloDTO
{
  "idBolsillo": "53d177e4-a53e-4a99-a71e-d42930aa49cb",
  "nombreBolsillo": "Gastos",
  "cantidad": 84,
  "ingresos": 0.00,
  "gastos": 3642212.00,
  "ahorros": 0.00,
  "otros": 0.00,
  "saldo": 0.00
}
```

Este resumen por bolsillo **no filtra por mes**: sus importes son los movimientos acumulados. El
«Total del mes actual» que muestra el detalle en la web se calcula aparte. Tras la reclasificación,
el envío de `$ 1.000.000` a cuenta propia es global (`id_bolsillo = NULL`), por lo que el detalle
del bolsillo `Gastos` ya no lo incluye y su total baja de `$ 2.464.212` a `$ 1.464.212`. El
«Balance del mes» global sigue siendo `$ 603.652`; la diferencia restante frente al detalle se debe
a que el resumen global incluye movimientos de todos los bolsillos y globales, no solo los de
`Gastos`.

`SuscripcionRecurrenteDTO` expone `id`, `nombre`, `categoria`, `montoPromedio`, `frecuencia`,
`intervaloDias`, `ultimoCobro`, `proximoCobro` y `cobrosDetectados`
(`SuscripcionRecurrenteDTO.java:7-17`).

### 6.5 Endpoints del pipeline que afectan a contabilidad

Base `/api/gmail` (`CGmail.java:76`). Todos exigen sesión; los de cuenta exigen además que la fuente
`CORREO` sea propia y esté conectada.

| Método | Ruta | Respuesta / efecto |
|--------|------|--------------------|
| `POST` | `/callback` | `401`/`403`/`400`/`409`/`502`/`503` según OAuth; crea o reutiliza la fuente y registra watch. |
| `GET` | `/cuentas` | Cuentas Gmail del usuario. |
| `GET` | `/cuentas/{cuentaId}/estado` | Estado de la conexión. |
| `GET` | `/cuentas/{cuentaId}/mensajes/{mensajeId}/contenido` | Contenido descifrado de un correo cacheado. |
| `POST` | `/cuentas/{cuentaId}/watch` | Registra el watch y encola sincronización inicial. |
| `POST` | `/webhook/pubsub` | `202` al encolar; valida OIDC. |
| `POST` | `/cuentas/{cuentaId}/suscripciones/importar-lote` | `202 {loteId,total}`; encola la importación asíncrona. |
| `GET` | `/cuentas/{cuentaId}/suscripciones/lotes/{loteId}` | Progreso y resultados por mensaje. |
| `GET` | `/movimientos/notificaciones` | Alertas de movimientos creados desde Gmail. |
| `POST` | `/movimientos/notificaciones/confirmar` | `204`; descarta alertas (máx. 100 ids), sin tocar movimientos. |
| `GET`, `POST`, `DELETE` | `/cuentas/{cuentaId}/papelera/...` | Papelera: mover/restaurar, configurar, lista blanca, vista previa y limpieza. |

Fuentes: `CGmail.java:140-668,579-610`, `CPapeleraGmail.java:62-323`,
`specs/040-papelera-correos/contrato-api.md`. La papelera usa `{"message":"..."}` y flags en los
`409`; el resto de la contabilidad usa `{"mensaje":"..."}` o `RespuestaModalDTO`.

---

## 7. Cálculos

### 7.1 Filtros comunes

- **Usuario**: se toma del JWT; nunca del body. `validarUsuario` falla si el usuario no existe.
- **Bolsillos**: si no se pasa `idBolsillo`, se usan todos los bolsillos propios; si se pasa, debe
  ser propio. Cuando el usuario no tiene bolsillos se usa `List.of("")` para que la consulta JPQL
  no falle con una lista vacía.
- **Estado**: los totales excluyen `POR_REVISAR` (`IMovimientoContable.java:27,96,102,109`). El
  listado solo filtra por estado si se envía `estadoRevision`.
- **Transferencias**: `resumenGlobal` las incluye; `resumirPorMes` (por bolsillo) las excluye por
  defecto. (`IMovimientoContable.java:107-122`.)

### 7.2 Saldo disponible de un bolsillo

Consulta nativa `calcularSaldoDisponible` (`IMovimientoContable.java:81-92`):

```
+ Ingresos del bolsillo
+ Transferencias que llegan al bolsillo
+ Devoluciones: GASTO del bolsillo cuyo origen es BOLSILLO y apunta a él mismo
- Gastos del bolsillo cuyo origen no es otro bolsillo (o que apuntan a él mismo)
- Salidas: cualquier movimiento con origen_tipo=BOLSILLO y origen_id=este bolsillo, hacia otro bolsillo
```

Solo cuentan filas confirmadas (`estado_revision IS NULL` o distinto de `POR_REVISAR`) y se puede
excluir un `idMovimiento` (para recalcular al editar). Devuelve `COALESCE(SUM(...), 0)` y se usa para
validar que un gasto/transferencia no supere el saldo; `validarSaldoDisponible` no valida si el tipo
es `INGRESO` ni si falta el bolsillo de origen (`SMovimientoContable.java:490-498`).

**La query no contempla movimientos de tipo `AHORRO`**, por lo que `ahorros > 0` con
`saldoAhorros = 0` es un estado posible: en la prueba de integración `ahorros = 15` pero
`saldoAhorros = 70`, importe que viene de las operaciones de ingreso/transferencia.
(`IMovimientoContable.java:81-92`, `PersistenciaClasificacionTest.java:302-322`.)

### 7.3 Resumen por bolsillo (`GET /movimiento/resumen`)

`SMovimientoContable.resumir` inicializa un acumulador por bolsillo propio, suma `COUNT` y `SUM` por
tipo con `resumirPorBolsillos` (que excluye `POR_REVISAR`), y calcula `saldo` con la consulta anterior,
con `null` convertido a cero. `INGRESO`, `GASTO` y `AHORRO` alimentan su acumulador; cualquier otro
tipo —**incluida `TRANSFERENCIA`**— cae en `otros`. (`SMovimientoContable.java:148-178`,
`IMovimientoContable.java:94-98`.)

### 7.4 Resumen global (`GET /movimiento/resumen-global`)

`SMovimientoContable.resumenGlobal` (`SMovimientoContable.java:180-233`):

1. Obtiene los bolsillos propios y sus ids.
2. Consulta `resumirPorMesIncluyendoGlobales(..., true)` —que incluye bolsillos propios y globales
   del usuario y excluye `POR_REVISAR`—, filtra filas por `año` y `mes` del parámetro en Java y
   acumula por `tipo` en `ingresos`, `gastos`, `ahorros` y `transferencias`; los tipos no
   reconocidos se ignoran. (`IMovimientoContable.java:24-31`, `SMovimientoContable.java:196-208`.)
3. **Transferencias a cuentas propias**: no hay corrección en el resumen. La consulta agrupa las
   filas según su `tipo` persistido. Los envíos Gmail a la cuenta configurada ya están grabados como
   `TRANSFERENCIA` globales (por la importación o por la migración `076`), por lo que aportan a
   `transferencias` y no a `gastos`. El criterio de destino propio se aplica al importar con el regex
   `(?i)a\s+la\s+cuenta\s*\*?\s*(\d{4,})` contra
   `finapp.contabilidad.cuentas-propias-transferencia` (por defecto `25310487123`, separable por
   comas).
4. `saldoAhorros` suma `calcularSaldoDisponible` de los bolsillos de tipo `AHORRO`, excepto el que
   se llama `Por revisar`.
5. `porRevisar` cuenta filas `POR_REVISAR` de bolsillos propios **y globales**.
6. `montoPorRevisar` suma solo las filas `POR_REVISAR` de bolsillos propios, excluyendo
   `TRANSFERENCIA` y bolsillos de tipo `AHORRO` del mes pedido.

El **balance del mes** que muestra la interfaz no es un campo del DTO —`ResumenGlobalContabilidadDTO`
no define `balance`—: el frontend calcula `ingresos - gastos` directamente. En noviembre 2026:
`2.985.989 - 2.382.337 = 603.652`.

Ejemplo tomado de `SMovimientoContableTest.java`:

| Movimiento | Importe | Cuenta destino | Tipo persistido | Efecto en el resumen |
|------------|---------|----------------|-----------------|----------------------|
| `mov-1` | 1.000.000 | `*25310487123` (propia) | `TRANSFERENCIA` global | Suma a `transferencias` |
| `mov-2` | 50.000 | `*3107378636` (tercero) | `GASTO` | Sigue sumando a `gastos` |

Los `gastos` originales de `3.382.337` quedan en `2.382.337` y `transferencias` es `1.000.000`
porque la fila `mov-1` se guardó con el tipo correcto. El detalle del bolsillo `Gastos` baja a
`1.464.212`: `mov-1` es global y ya no pertenece a ese bolsillo.

> Nota de portabilidad: la consulta usa `SUBSTRING(..., LOCATE(':', ...) + 1)` en lugar de
> `SUBSTRING_INDEX` para funcionar igual en MySQL y en H2 de pruebas.

### 7.5 Detección de suscripciones recurrentes

`detectarSuscripcionesRecurrentes` agrupa gastos confirmados por `origenNombre` (o `concepto`),
normalizado sin acentos ni caracteres no alfanuméricos. Requiere al menos 2 cobros; calcula el
intervalo medio, lo acerca a `{7,14,30,60,90,180,365}` días con tolerancia
`max(4, round(periodo*0.18))`, exige que **todos** los intervalos cumplan y devuelve promedio,
frecuencia legible, último cobro y próximo cobro. (`SMovimientoContable.java:90-139`.)

---

## 8. Flujos de negocio

### 8.1 Registro manual

`POST /movimiento` → valida DTO (fecha, monto > 0 con `scale <= 2` y `precision <= 19`, bolsillo,
categoría ≤ 80, concepto ≤ 160, `origenId` ≤ 180) → rechaza Gmail duplicado por
`(GMAIL, origenId)` → exige bolsillo propio activo → resuelve tipo (el de la solicitud si viene,
si no el del bolsillo) → resuelve origen → valida saldo → guarda con `estadoRevision = null` y
vincula tarjeta si el origen es `CUENTA`. El bolsillo debe ser `INGRESO`, `GASTO` o `AHORRO`, pero
como el `tipo` explícito prevalece, es posible registrar un `GASTO` en un bolsillo `AHORRO` legacy.
(`SMovimientoContable.java:252-255,306-344,504-531`, `SMovimientoContableLegacyPocketTest.java:33-57`.)

### 8.2 Actualización y confirmación

- `PUT /movimiento/{id}` **ignora `solicitud.tipo()`** y deduce el tipo del bolsillo destino;
  rechaza bolsillos inactivos distintos del actual y los tipos `POR_REVISAR`/`OTROS`, valida saldo
  excluyendo el propio movimiento y, si estaba `POR_REVISAR`, crea una regla de filtro de tipo
  `REMITENTE` con `origenNombre` —si no existía— y lo marca `CONFIRMADO`.
  (`SMovimientoContable.java:438-471`, `SReglaFiltro.java:25-48`.)
- `POST /movimiento/{id}/confirmar-global` solo admite movimientos Gmail `POR_REVISAR` de tipo
  `GASTO`/`INGRESO`; les quita el bolsillo, fija `idUsuario` y los marca `CONFIRMADO`.
  (`SMovimientoContable.java:275-293`.)

### 8.3 Importación automática desde Gmail

`crearAutomaticamenteDesdeGmail` (`SMovimientoContable.java:346-402`):

- Si la clasificación pide revisión, usa el bolsillo `Por revisar` y estado `POR_REVISAR`.
- Si hay `idBolsillo` clasificado, lo exige activo, del mismo tipo y con la misma categoría; si no,
  va a revisión.
- Si solo hay tipo y hay **un único** bolsillo compatible, lo usa; si hay varios y el tipo es
  `GASTO`/`INGRESO`, guarda como **movimiento global confirmado**; si hay varios y es otro tipo, va
  a revisión.
- Intenta vincular tarjeta por sufijo de PAN del correo; solo si la coincidencia es única.
- Deduplica por `(GMAIL, origenId)` con `existsByOrigenTipoAndOrigenId`.

### 8.4 Sugerencia por revisar y borrado

`POST /movimiento/gmail/por-revisar` crea la sugerencia en el bolsillo interno. En la web, al
confirmar «Eliminar», el cliente llama primero a mover el correo a la Papelera de Gmail usando la
referencia `cuentaId:mensajeId` y solo después invoca `DELETE /movimiento/{id}`; si Gmail falla, el
movimiento se conserva. Esa secuencia es responsabilidad del frontend, no del endpoint de borrado.

---

## 9. Errores esperados

### 9.1 Formas de cuerpo

| Origen | Forma |
|--------|-------|
| Controladores de movimientos/bolsillos | `{"tipo":"error","mensaje":"...","campo":"...","datos":null}` |
| Interceptor de sesión | Igual que arriba, con mensajes fijos de inicio de sesión |
| `ManejoErroresApi` | `{"mensaje":"..."}` |
| Validación de origen HTTP | `403` sin cuerpo |
| Controladores Gmail | En varias operaciones, `{"message":"..."}` (ver spec `040-papelera-correos`) |

### 9.2 Catálogo

| Código | Cuándo | Mensaje / forma habitual |
|--------|--------|--------------------------|
| `400` | `estadoRevision` no es un enum válido en `GET /movimiento` | `campo = "tipo"` con el mensaje de `IllegalArgumentException` |
| `400` | `mes` con formato inválido | `"El mes debe tener formato YYYY-MM."`, `campo = "mes"` |
| `400` | DTO inválido al crear/actualizar | Mensajes de `validar`/`tipoManual`: fecha, monto, categoría, concepto, tipo, origen, saldo insuficiente |
| `400` | Bolsillo sin tipo, inactivo, interno o de tipo no permitido | `"Selecciona un bolsillo."`, `"Activa el bolsillo..."`, `"Asigna el movimiento a un bolsillo de tipo GASTO, INGRESO o AHORRO."` |
| `401` | Sin sesión / sesión caducada | `"Inicia sesión para continuar."` / `"Tu sesión expiró o se cerró. Inicia sesión de nuevo."`; fallback de controlador: `"Inicia sesión para consultar tus movimientos."` / `"…tus bolsillos."` |
| `403` | Origen HTTP no permitido en escritura | Sin cuerpo |
| `404` | Movimiento o bolsillo ajeno/inexistente | `"No se encontró el movimiento."`, `"No se encontró el bolsillo seleccionado."` |
| `404` | Ruta inexistente | `{"mensaje":"El recurso solicitado no existe."}` |
| `405` | Método HTTP no permitido | `{"mensaje":"El método HTTP no está permitido para este recurso."}` |
| `409` | Correo Gmail ya importado | `"Este correo ya fue importado como movimiento."` |
| `409` | `IllegalStateException` en crear/actualizar | `RespuestaModalDTO.error(error.getMessage(), null)` |
| `409` | Borrar bolsillo interno/fijo/con movimientos | `"No puedes eliminar ..."` (`CBolsillo` usa `409` para `IllegalStateException`) |
| `500` | Excepción no controlada | `{"mensaje":"No se pudo completar la operación. Inténtalo de nuevo más tarde."}`; si la respuesta ya estaba comprometida, cuerpo `null` |

Errores de Gmail (documentados en `specs/040-papelera-correos/contrato-api.md:27-41`): `409` con
flags `requires_scope_authorization`, `requires_reconnect`, `mail_unavailable`, `limpieza_en_curso`,
`limite_ejecucion_alcanzado` o `conteo_cambiado`; `410` si el correo fue purgado; `429` por cuota;
`502`/`503` si Gmail no responde. Su cuerpo usa `message` en inglés.

### 9.3 Mensajes exactos y huecos de manejo

Mensajes de negocio frecuentes (`SMovimientoContable.java:74-77,306-308,490-531,539-599`,
`SBolsillo.java:169-198,234-254`):

- Filtro/tipo: `"El filtro debe ser Todos o un tipo de movimiento válido."`,
  `"Selecciona un tipo de movimiento válido: GASTO, INGRESO, AHORRO o TRANSFERENCIA."`,
  `"El tipo de movimiento debe ser GASTO, INGRESO, AHORRO o TRANSFERENCIA."`,
  `"El bolsillo no tiene un tipo de movimiento configurado."`.
- DTO: `"Completa los datos del movimiento."`, `"El identificador del origen supera el máximo
  permitido."`, `"Ingresa una fecha válida."`, `"El monto debe ser positivo y tener máximo dos
  decimales."`, `"Selecciona el bolsillo del movimiento."`, `"La categoría es obligatoria y no puede
  superar 80 caracteres."`, `"El concepto no puede superar 160 caracteres."`.
- Gmail: `"La sugerencia debe provenir de un correo Gmail."`, `"El movimiento Gmail debe ser un
  GASTO o INGRESO."`, `"Solo se pueden confirmar globalmente movimientos Gmail por revisar."`,
  `"Este correo ya fue importado como movimiento."`.
- Origen/saldo: `"Una transferencia requiere origen en otro bolsillo."`, `"El bolsillo de origen
  debe ser distinto del bolsillo destino."`, `"Activa el bolsillo de origen para usarlo."`, `"El
  bolsillo de origen debe ser de tipo Ingreso o Ahorro."`, `"El bolsillo de Ahorro debe tener saldo
  disponible para usarlo como origen."`, `"Selecciona de dónde salió el dinero."`, `"No se encontró
  la cuenta seleccionada."`, `"Saldo insuficiente en «…». Disponible: … COP."`.
- Bolsillos: `"Los bolsillos del usuario solo pueden ser de tipo AHORRO."`, `"El bolsillo «Por
  revisar» es interno y no se puede editar desde esta operación."`, `"Los bolsillos fijos
  «Ingresos» y «Gastos» no se pueden editar."`, `"No puedes eliminar el bolsillo interno «Por
  revisar»."`, `"No puedes eliminar los bolsillos fijos «Ingresos» y «Gastos»."`, `"No puedes
  eliminar un bolsillo que ya tiene movimientos."`.

Huecos conocidos: `/movimiento/resumen`, `/movimiento/recurrentes` y `/movimiento/correos` no
capturan excepciones; `confirmar-global` no captura `IllegalStateException`; un JSON malformado y
un `ids` ausente caen en el `500` genérico; `ResponseStatusException` conserva su estado y motivo.
(`CMovimientoContable.java:52-85,120-131`, `ManejoErroresApi.java:26-62`.)

### 9.4 Errores del pipeline Gmail

| Código | Situación | Forma |
|--------|-----------|-------|
| `400` | Falta código OAuth o datos Pub/Sub mal formados | Mensaje de error del controlador |
| `401` | Sin sesión o token OIDC inválido en el webhook | `{"tipo":"error","mensaje":...}` / error de autenticación |
| `403` | `Origin` o `X-Requested-With: XmlHttpRequest` inválidos en el callback | Sin cuerpo o mensaje de origen |
| `409` | Scope insuficiente, reconexión necesaria, cuenta distinta de la esperada, correo no disponible, limpieza en curso, límite alcanzado o conteo cambiado | `{"message":...}` + flag (`requires_scope_authorization`, `requires_reconnect`, `mail_unavailable`, `limpieza_en_curso`, `limite_ejecucion_alcanzado`, `conteo_cambiado`) |
| `410` | Correo purgado en papelera | `{"message":...}` |
| `429` | Cuota de Google agotada | `{"message":...}` |
| `502` / `503` | Canje OAuth rechazado, falta de credenciales renovables o Gmail no disponible | `{"message":...}` |
| Por mensaje | `IMPORTADO`, `POR_REVISAR`, `DUPLICADO`, `ERROR`; el worker usa `IGNORADO`, `PENDIENTE_BOLSILLO`, `REGISTRADO`, `DUPLICADO`, `ERROR` | Resultado del lote / estado de `gmail_mensaje` |

El rate limit de `SIntentosRateLimit` pertenece a login/registro, **no** al pipeline Gmail; los
`429` de Gmail son cuota de Google. (`SIntentosRateLimit.java:8-19`, `CPapeleraGmail.java:357-360,422-427`.)

> «Errores permitidos» en este documento significa **errores de negocio esperados y manejados**, no
> errores que deban ignorarse. Ningún fallo de persistencia o de Gmail debe presentarse como éxito.

---

## 10. Configuración relevante

Solo nombres y valores por defecto **no secretos**. Los secretos viven en `.env` / variables de
entorno y no se versionan en este documento.

| Propiedad | Default | Uso |
|-----------|---------|-----|
| `finapp.contabilidad.cuentas-propias-transferencia` / `CUENTAS_PROPIAS_TRANSFERENCIA` | `25310487123` | Sufijos (separados por coma) considerados cuentas propias al reclasificar envíos Gmail como `TRANSFERENCIA`. Mantener sincronizado con `sql/076_reclasificar_transferencias_cuenta_propia.sql`. |
| `finapp.cors.allowed-origins` / `WEB_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173,http://localhost:5500` | Orígenes web permitidos en CORS y escrituras. |
| `finapp.jwt.secret` / `FINAPP_JWT_SECRET` | — | Firma JWT. |
| `finapp.jwt.expiration-seconds`, `finapp.jwt.inactivity-seconds`, `finapp.jwt.cookie-secure`, `finapp.jwt.cookie.same-site` | `28800`, `1800`, `true`, `Lax` | Sesión y cookie `FINAPP_AUTH`. |
| `finapp.docs.publico` / `FINAPP_DOCS_PUBLICO` | `true` | Permite `/docs` y `/api-docs` sin sesión (con spec reducida). |
| `spring.jpa.hibernate.ddl-auto` | `update` | Evolución automática del esquema. |
| `spring.jpa.open-in-view` | `true` | Mantiene la sesión JPA durante la serialización de entidades con relaciones diferidas. |
| `finapp.google.entradas-intervalo-ms` / `GOOGLE_ENTRADAS_INTERVALO_MS` | `10000` | Frecuencia del worker de entrada Gmail. |
| `finapp.google.papelera-intervalo-ms` / `GOOGLE_PAPELERA_INTERVALO_MS` | `600000` | Frecuencia de la regla automática de papelera. |
| `finapp.google.sync-interval-ms` | `300000` | Ciclo de sincronización de Gmail; dentro del ciclo se procesan los movimientos pendientes. |
| `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` | `true` | Habilita explícitamente OpenAPI y Swagger UI. |
| `springdoc.swagger-ui.path`, `springdoc.api-docs.path` | `/docs`, `/api-docs` | Documentación interactiva. |

Fuente: `application.properties:1-59`, `CGmail.java:696-723`.

El executor `gmailImportExecutor` es de un solo hilo (`corePoolSize = maxPoolSize = 1`) con cola
de 10 tareas y prefijo `gmail-import-`; no espera tareas al apagar. La importación de lotes
responde `202` al encolar y guarda el progreso en un `ConcurrentHashMap` en memoria, que se pierde
al reiniciar; bajo carga puede responderse `503` por rechazo de la tarea.
(`GmailImportacionAsyncConfig.java:7-20`, `SImportacionLoteGmail.java:21-40,175-179`,
`CGmail.java:551-557`.)

---

## 11. Integración con finApp-web

El consumidor de esta API es la feature `contabilidad` de `finApp-web` (React/Vite). La URL base se
resuelve con `VITE_API_BASE_URL` (vacía por defecto, es decir, mismo origen) y las rutas se arman en
un registro centralizado. (`finApp-web/src/config/api.config.js:11,31-39,108-136`.)

### 11.1 Llamadas que hace la web

| Método | Ruta | Uso en la UI |
|--------|------|--------------|
| `GET` | `/bolsillo`, `POST` `/bolsillo`, `PUT|DELETE` `/bolsillo/{idBolsillo}` | Listado y CRUD de bolsillos |
| `GET` | `/movimiento?tipo=&idBolsillo=&estadoRevision=` | Listado normal y bandeja de revisión |
| `POST` | `/movimiento` | Crear movimiento |
| `POST` | `/movimiento/gmail/por-revisar` | Crear sugerencia desde correo |
| `POST` | `/movimiento/{id}/confirmar-global` | Confirmar Gmail como movimiento global |
| `GET` | `/movimiento/correos?ids=...` | Saber qué correos ya están importados |
| `PUT|DELETE` | `/movimiento/{id}` | Editar/confirmar y borrar |
| `GET` | `/movimiento/resumen`, `/movimiento/resumen-global?mes=YYYY-MM` | Cards y balance |
| `GET` | `/movimiento/recurrentes` | Declarado en el servicio; la UI contable aún no lo usa |
| `POST` | `/api/gmail/cuentas/{cuentaId}/mensajes/{mensajeId}/papelera` | Mover a Trash antes de borrar un pendiente |

Fuentes: `finApp-web/src/features/contabilidad/services/bolsillosService.js:11-25`,
`movimientosService.js:23-89`, `finApp-web/src/config/api.config.js:70`,
`finApp-web/src/services/gmailServicio.js:335-345`.

### 11.2 Sesión y errores en el cliente

Todas las peticiones usan `credentials: "include"` para enviar la cookie `FINAPP_AUTH`. Un `401`
dispara un único refresco (`POST /usuario/refresh`) con control *single-flight* y `navigator.locks`
entre pestañas; si falla se emite `finapp:sesion-requerida`. Hay renovación proactiva a los 13
minutos y umbral de inactividad de 25 minutos. El error lanzado al caller toma `mensaje`, `message`
o `error` del cuerpo — admite las tres convenciones descritas en la sección 9 — con fallback
`Error HTTP {status}`. (`finApp-web/src/services/httpServicio.js:12-17,69-77,110-126,149-249,266-305`.)

El hook `useContabilidad` usa flags `vigente` para no actualizar estado tras desmontar, muestra
mensajes por operación y limpia los avisos de éxito a los 4 segundos; sin usuario redirige a `/` al
intentar crear movimientos. (`finApp-web/src/features/contabilidad/hooks/useContabilidad.js:58-89,111-223,257-261`.)

### 11.3 Interpretación de las respuestas

- **Envelopes tolerados**: el cliente acepta un array directo, `.data` o `.content`; la bandeja de
  revisión añade `.datos`. Conviene fijar uno solo en el contrato.
  (`useContabilidad.js:22-25`, `components/modals/DetalleBolsilloModal.jsx:85-88`,
  `ModalMovimientosPorRevisar.jsx:20-24`.)
- **Resumen global**: solo consume `ingresos`, `gastos` y `porRevisar`. El balance visible es
  `ingresos - gastos` calculado en el cliente a partir de los importes ya agrupados por el backend
  según el `tipo` persistido. Los campos `ahorros`, `transferencias` y `saldoAhorros` aparecen en
  fixtures de test pero todavía no en la UI.
  (`components/cards/BolsillosFijosCard.jsx:40-43`, `components/modals/DetalleBalanceModal.jsx:26-29`,
  `pages/ContabilidadPage.jsx:176`.)
- **Resúmenes por bolsillo**: se indexan por `idBolsillo` y la card elige `gastos`, `ingresos`,
  `ahorros` u `otros` según el tipo. `saldoDisponible` se deriva en cliente y solo habilita
  bolsillos `AHORRO` como origen de transferencia. (`useContabilidad.js:167-174`,
  `components/cards/BolsilloCard.jsx:10-14,28`, `utils/contabilidadHelpers.js:55-60`.)
- **Moneda y fecha**: `Intl.NumberFormat("es-CO", COP, maximumFractionDigits: 0)` produce
  `$ 12.000` / `-$ 5.500`; las fechas se formatean en `es-CO` con `timeZone: "UTC"` y un
  `YYYY-MM-DD` se convierte a `T00:00:00Z`. (`utils/contabilidadHelpers.js:20-31,62-67`.)
- **Agrupación**: `claveMesMovimiento` produce `YYYY-MM`; `agruparPorAnioYMes` suma montos **sin
  invertir signos**, ordena años y meses descendente y marca el mes más reciente. El detalle del
  bolsillo abre por defecto ese periodo y muestra totales por año y mes.
  (`utils/contabilidadHelpers.js:158-223`, `DetalleBolsilloModal.jsx:122-150,182-195,388-430`.)

Rutas relativas de esta subsección: `finApp-web/src/features/contabilidad/`.

### 11.4 Reglas que decide el frontend

- **Bolsillos fijos**: `esBolsilloFijo` exige nombre normalizado `ingresos`/`gastos` **y** tipo
  acorde; se agrupan en una sola card dividida, se marcan en el formulario, no exponen
  editar/eliminar y son destino por defecto al «devolver» un movimiento.
  (`utils/contabilidadHelpers.js:111-123`, `components/cards/BolsillosContablesCards.jsx:24-37`,
  `components/modals/ModalCrearMovimientoContable.jsx:275-283`, `pages/ContabilidadPage.jsx:155-167,263-296`.)
- **«Total del mes actual»**: se calcula en el cliente sobre movimientos del bolsillo —como destino
  **u origen** `origenTipo=BOLSILLO`— cuya clave de mes es la local, sumando importes sin signo;
  **no** lee el resumen global del bolsillo. (`DetalleBolsilloModal.jsx:96-102,152-166,263-272`.)
- **Transferencias a cuenta propia**: la web no ofrece crear el tipo `TRANSFERENCIA`; el `tipo`
  resulta del bolsillo destino y admite origen `CUENTA` o `BOLSILLO`. En el flujo Gmail el cliente
  envía `GASTO` y el backend decide si el destino es propio; la respuesta ya trae
  `tipo: TRANSFERENCIA` y `idBolsillo: null`, así que no hay compensación en el cliente.
  (`ModalCrearMovimientoContable.jsx:122-126,160-162,199-210`,
  `components/modals/DetalleBalanceModal.jsx:26-29,96-100`.)
- **Por revisar**: se excluye del listado normal por `estadoRevision`; la bandeja consulta dos
  variantes (`?estadoRevision=POR_REVISAR` y `?tipo=POR_REVISAR`) y deduplica por `idMovimiento`.
  Confirmar un Gmail `GASTO`/`INGRESO` usa `confirmar-global`; si no es clasificable deriva a
  edición y, si hay un único bolsillo compatible, usa `PUT /movimiento/{id}`.
  (`useContabilidad.js:124-131`, `ModalMovimientosPorRevisar.jsx:56-92,105-174`.)
- **Borrado de un pendiente**: si `origenTipo=GMAIL` y `origenId=cuentaId:mensajeId`, se llama
  **primero** a la papelera de Gmail y solo después a `DELETE /movimiento/{id}`; si falla el
  trash, el movimiento se conserva para reintentar. La secuencia es responsabilidad del cliente.
  (`utils/contabilidadHelpers.js:94-109`, `useContabilidad.js:280-308`, `ContabilidadPage.jsx:380-389`.)

### 11.5 Supuestos que conviene confirmar

1. La bandeja depende de que el backend soporte tanto `tipo=POR_REVISAR` como
   `estadoRevision=POR_REVISAR`; la fuente de verdad debería ser una sola.
2. `porRevisar` se trata como **cantidad**, aunque el DTO también expone `montoPorRevisar`.
3. Si `GET /movimiento/resumen-global` falla, el hook guarda `null` sin avisar: la UI muestra
   ingresos/gastos en 0 y «Por revisar (0)». (`useContabilidad.js:208-210`.)
4. El separador `:` de `origenId` no se escapa y se envía `idBolsillo: null` en
   `POST /movimiento/gmail/por-revisar`.
5. `PUT /movimiento/{id}` es reemplazo total: editar o confirmar reenvía todos los campos, por lo
   que omitir `entidad`/`remitente`/`descripcion` podría borrarlos.
6. Trash + `DELETE` no es atómico: si falla la segunda llamada, el correo queda en papelera y el
   movimiento sigue vivo.
7. El `mes=YYYY-MM` se envía con hora local mientras las fechas se leen/agrupan en UTC: pueden
   aparecer desajustes en los límites de mes/año.

---

## 12. Observaciones y deuda conocida

1. **Dos convenciones de error conviven**: `mensaje` (global y controladores de contabilidad) y
   `message` (controladores Gmail). Un cliente robusto debe leer ambas.
2. **DTOs en camelCase** frente a la spec `040`, que usa `snake_case` para Papelera. No es un error
   de código, pero en contabilidad conviene no mezclar normas al documentar.
3. `MContabilidad.valor` es `double` y no interviene en los cálculos; los importes reales están en
   `movimiento_contable.monto`.
4. `montoPorRevisar` no incluye movimientos globales porque su consulta filtra por
   `bolsillo.idBolsillo IN :idsBolsillo`, mientras `porRevisar` sí los cuenta. Tampoco el listado
   con `idBolsillo` incluye globales, aunque sin ese filtro sí aparecen.
5. `calcularSaldoDisponible` ignora los movimientos de tipo `AHORRO`, por lo que `ahorros` y
   `saldoAhorros` no son magnitudes comparables.
6. El parser y el front siguen etiquetando «Transferiste …» como `GASTO`; la decisión de cuenta
   propia la toma el backend desde el fragmento del correo. Un cambio de sufijos en
   `CUENTAS_PROPIAS_TRANSFERENCIA` afecta a las importaciones nuevas, pero **no reescribe** el
   histórico: para eso hay que volver a aplicar/adaptar `sql/076_*.sql`.
7. No hay migraciones automáticas (Flyway/Liquibase); los cambios de esquema en `sql/` se aplican a
   mano en MySQL. `ddl-auto=update` crea columnas nuevas pero no reescribe datos legacy; el runner
   de backfill solo cubre MySQL/MariaDB.
8. `GET /bolsillo` muta datos (crea/reactiva fijos, consolida duplicados y repara legacy) aunque sea
   una consulta HTTP.
9. Asimetría de tipos: `crear` acepta un `tipo` explícito distinto del bolsillo, mientras
   `actualizar` ignora ese campo y lo deduce del destino.
10. `id_tarjeta` es referencia suave sin FK: pueden quedar identificadores huérfanos al eliminar una
    tarjeta.
11. La importación de Gmail es *fire-and-forget*: el lote y su progreso viven en memoria y se
    pierden al reiniciar.
12. `YearMonth.now()` para el resumen depende de la zona del JVM, mientras las fechas de correo se
    convierten a `America/Bogota`; es otra fuente potencial de desajuste de mes.
13. La deduplicación de Gmail es **global** por `(origen_tipo, origen_id)`, no por usuario: un
    mismo `cuentaId:mensajeId` no se puede importar dos veces aunque cambie de titular.
14. Los correos en `PENDIENTE_BOLSILLO` no se reprocesan solos: la consulta automática solo pide
    `movimiento_automatico_estado = PENDIENTE`.
15. Mover un correo a la papelera descarta su alerta contable pero **no borra** el movimiento; el
    borrado es una llamada independiente del frontend.
16. Los supuestos del consumidor web (envelopes, `tipo` frente a `estadoRevision`, borrado no
    atómico, zonas horarias) se enumeran en la sección 11.5 y deben resolverse en el contrato antes
    de considerar estable la integración.

---

## 13. Referencias rápidas

- Núcleo: `Controller/CMovimientoContable.java`, `Services/SMovimientoContable.java`,
  `Repository/IMovimientoContable.java`, `Services/SBolsillo.java`.
- Modelos: `Model/MMovimientoContable.java`, `Model/MBolsillo.java`, `Model/MContabilidad.java`,
  `Model/EstadoRevisionMovimiento.java`.
- DTOs: `DTO/MovimientoContableDTO.java`, `DTO/MovimientoContableSolicitudDTO.java`,
  `DTO/ResumenGlobalContabilidadDTO.java`, `DTO/ResumenMovimientoBolsilloDTO.java`,
  `DTO/RespuestaModalDTO.java`.
- Errores y seguridad: `ManejoErroresApi.java`, `Services/UsuarioActivoInterceptor.java`,
  `ValidacionOrigenInterceptor.java`, `CorsConfig.java`.
- Orígenes: `Controller/CGmail.java`, `Controller/CPapeleraGmail.java`,
  `Services/SGmailMensajes.java`, `Services/SGmailEntradas.java`,
  `Services/SImportacionLoteGmail.java`, `Services/SClasificadorMovimiento.java`,
  `Services/ParserMovimientoBancolombia.java`, `Services/SReglaFiltro.java`,
  `Filtrado/`, `Model/MFuente.java`, `Model/MGmailMensaje.java`,
  `Model/MGmailEntradaPendiente.java`, `sql/058..075_*.sql`.
- Contrato de Papelera: `specs/040-papelera-correos/contrato-api.md`.
- Consumidor web: `finApp-web/src/config/api.config.js`, `finApp-web/src/services/httpServicio.js`,
  `finApp-web/src/features/contabilidad/{services,hooks,components,pages,utils}/`.
- Documentación de la API: `/docs` (Swagger UI) y `/api-docs` (OpenAPI).
