# Suscripciones (manuales y de Gmail)

## Modelo único

Todas las suscripciones viven en la tabla `suscripcion`. La columna `origen` define quién la creó:

| `origen` | Quién la registra | Campos que usa |
|-----------|-------------------|----------------|
| `MANUAL`  | El usuario, desde el CRUD `/suscripcion` | `nombre`, `categoria`, `monto`, `fechaCobro`, `importancia`, `activa` |
| `GMAIL`   | La sincronización de los correos (`List-Unsubscribe`) | `correoRemitente`, `categorias`, `cuentaGmailId`, `urlBaja`, `mailtoBaja`, `ultimaActividad` |

Las suscripciones de Gmail se administran desde la cuenta conectada (se limpian al desconectar la cuenta);
el CRUD solo edita las manuales. La lógica específica de Gmail vive aislada en `SSuscripcionGmail`
(guardado, consulta por cuenta y limpieza) y comparte la misma tabla con `SSuscripcion` (CRUD manual).

Migración de datos: `sql/071_unificar_suscripciones.sql`.

## Autenticación

Todas las rutas exigen sesión activa (cookie `FINAPP_AUTH` o `Authorization: Bearer <jwt>`).

- Sin sesión → `401 {"tipo":"error","mensaje":"Inicia sesión para consultar tus suscripciones.","campo":null,"datos":null}`.
- Además, sin sesión la ruta **no aparece** en `/api-docs` (la especificación sin sesión solo expone
  login, registro y asistente público).

El propietario se deriva del token: **nadie puede crear, leer, editar ni borrar suscripciones de otro usuario**
aunque知道了 el identificador.

## Endpoints del CRUD

Base: `/suscripcion`

| Método | Ruta | Descripción | Cuerpo (JSON) | Respuesta |
|--------|------|-------------|---------------|-----------|
| `POST` | `/suscripcion` | Registra una suscripción manual | `SuscripcionSolicitudDTO` | `201` → `SuscripcionDTO` |
| `GET` | `/suscripcion` | Lista las suscripciones del usuario (manuales primero, luego Gmail) | — | `200` → `[SuscripcionDTO]` |
| `GET` | `/suscripcion?origen=MANUAL` | Filtra por procedencia (`MANUAL` o `GMAIL`) | — | `200` → `[SuscripcionDTO]` |
| `GET` | `/suscripcion/{idSuscripcion}` | Consulta una suscripción propia | — | `200` → `SuscripcionDTO` |
| `PUT` | `/suscripcion/{idSuscripcion}` | Actualiza una suscripción manual | `SuscripcionSolicitudDTO` | `200` → `SuscripcionDTO` |
| `PATCH` | `/suscripcion/{idSuscripcion}/estado` | Activa (`true`) o pausa (`false`) una manual | `{"activa": false}` | `200` → `SuscripcionDTO` |
| `DELETE` | `/suscripcion/{idSuscripcion}` | Elimina una suscripción manual | — | `200` → `{"tipo":"exito","mensaje":"Suscripción eliminada.",...}` |

El endpoint ya existente `GET /api/gmail/suscripciones` sigue funcionando y ahora lee de la tabla `suscripcion`
(sin cambios en su contrato: devuelve `{suscripciones, erroresCuentas, cuentasConsultadas}`).

### `SuscripcionSolicitudDTO` (entrada)

```json
{
  "nombre": "Netflix",
  "monto": 44900,
  "fechaCobro": "2026-11-20",
  "categoria": "Entretenimiento",
  "importancia": "Alta",
  "activa": true
}
```

| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| `nombre` | `string` | Sí | 1–100 caracteres |
| `monto` | `number` | Sí | Mayor que 0, máximo `9999999999.99`, se guarda con 2 decimales |
| `fechaCobro` | `string` | Sí | Fecha `YYYY-MM-DD` |
| `categoria` | `string` | Sí | 1–60 caracteres |
| `importancia` | `string` | No | `"Alta"` o `"Baja"` (sin distinguir mayúsculas). Por defecto `"Alta"`. Se devuelve como `"ALTA"` / `"BAJA"` |
| `activa` | `boolean` | No | Al crear vale `true`; al editar, si se omite se conserva el valor actual |

### `SuscripcionDTO` (salida)

```json
{
  "id": "8b2e6f1c-1f2a-4a0e-9c1d-5f4e7a2b3c4d",
  "origen": "MANUAL",
  "nombre": "Netflix",
  "categoria": "Entretenimiento",
  "categorias": [],
  "monto": 44900.00,
  "fechaCobro": "2026-11-20",
  "importancia": "ALTA",
  "activa": true,
  "correoRemitente": null,
  "cuentaGmailId": null,
  "cuentaGmail": null,
  "urlBaja": null,
  "mailtoBaja": null,
  "bajaConUnClic": false,
  "ultimaActividad": null,
  "fechaCreacion": "2026-11-14T15:20:31Z",
  "fechaActualizacion": "2026-11-14T15:20:31Z"
}
```

En las suscripciones `GMAIL` los campos manuales vienen en `null` y se llenan
`correoRemitente`, `categorias`, `cuentaGmailId`, `cuentaGmail`, `urlBaja`, `mailtoBaja`,
`bajaConUnClic` y `ultimaActividad`.

### Errores

| Código | Cuándo | Cuerpo |
|--------|--------|--------|
| `400` | Datos inválidos | `{"tipo":"error","mensaje":"El monto debe ser mayor que cero.","campo":"monto","datos":null}` |
| `401` | Sin sesión | `{"tipo":"error","mensaje":"Inicia sesión para consultar tus suscripciones.",...}` |
| `404` | La suscripción no existe o es de otro usuario | `{"tipo":"error","mensaje":"No se encontró la suscripción.",...}` |
| `409` | Se intenta editar o borrar una suscripción de Gmail | `{"tipo":"error","mensaje":"Las suscripciones detectadas en Gmail se administran desde la cuenta de correo conectada.",...}` |

El campo `campo` indica qué input del formulario corregir (`nombre`, `monto`, `fechaCobro`, `categoria`,
`importancia`, `activa`, `origen`).

## Integración en la página de suscripciones (frontend)

### 1. `src/config/api.config.js`

```js
  suscripcion: {
    base: "/suscripcion",
    porId: "/suscripcion/id",          // GET / PUT / DELETE  /suscripcion/{id}
    estado: "/suscripcion/estado",     // PATCH /suscripcion/{id}/estado
  },
```

### 2. Servicio `src/features/suscripciones/services/suscripcionesService.js`

```js
import { getEndpoint, API_BASE_URL } from "../../../config/api.config";

const BASE = `${API_BASE_URL}${getEndpoint("suscripcion.base")}`;

async function pedir(ruta, metodo = "GET", cuerpo = null) {
  const respuesta = await fetch(ruta, {
    method: metodo,
    credentials: "include",
    headers: cuerpo ? { "Content-Type": "application/json" } : undefined,
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  const datos = respuesta.status === 204 ? null : await respuesta.json().catch(() => null);
  if (!respuesta.ok) throw new Error(datos?.mensaje || `Error HTTP ${respuesta.status}`);
  return datos;
}

export const listarSuscripciones = (origen) =>
  pedir(origen ? `${BASE}?origen=${encodeURIComponent(origen)}` : BASE);

export const crearSuscripcion = (datos) => pedir(BASE, "POST", datos);

export const actualizarSuscripcion = (id, datos) => pedir(`${BASE}/${encodeURIComponent(id)}`, "PUT", datos);

export const cambiarEstadoSuscripcion = (id, activa) =>
  pedir(`${BASE}/${encodeURIComponent(id)}/estado`, "PATCH", { activa });

export const eliminarSuscripcion = (id) => pedir(`${BASE}/${encodeURIComponent(id)}`, "DELETE");
```

### 3. Sustituir el estado local de `SuscripcionesPage.jsx`

```jsx
const [suscripcionesManuales, setSuscripcionesManuales] = useState([]);

const cargarManuales = useCallback(async () => {
  const lista = await listarSuscripciones("MANUAL");
  setSuscripcionesManuales(lista.map((item) => ({
    id: item.id,
    nombre: item.nombre,
    monto: Number(item.monto),
    fechaCobro: item.fechaCobro,
    categoria: item.categoria,
    importancia: item.importancia === "ALTA" ? "Alta" : "Baja",
  })));
}, []);

const guardarSuscripcionManual = async (evento) => {
  evento.preventDefault();
  const creada = await crearSuscripcion({
    nombre: suscripcionManual.nombre.trim(),
    monto: Number(montoNormalizado),
    fechaCobro: suscripcionManual.fechaCobro,
    categoria: suscripcionManual.categoria,
    importancia: suscripcionManual.importancia,
  });
  setSuscripcionesManuales((actuales) => [mapear(creada), ...actuales]);
  setSuscripcionManual(SUSCRIPCION_MANUAL_INICIAL);
  setModalManualAbierto(false);
};
```

`importancia` llega como `"ALTA"`/`"BAJA"`; el componente `SuscripcionesManualesSection` compara con
`"Alta"`, por eso el mapeo de una línea en el frontend.
