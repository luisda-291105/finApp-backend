package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @uso               Operaciones de archivos contra la API v3 de Google Drive
 * @capa              Servicio
 * @responsabilidades Listar, leer, subir, organizar, papelera, permisos y lote ZIP usando el token
 *                    vigente de SAlmacenamientoTokens; reintenta 429/5xx y detecta 401/403 para reconexión
 * @datos             Metadatos y contenido solicitado; el token nunca sale del backend
 * @dependencias      RestClient (Drive API v3), SAlmacenamientoTokens, SCacheListadosAlmacenamiento
 * @usadoPor          CAlmacenamientoArchivos
 */
@Service
public class GoogleDriveArchivos implements SAlmacenamientoArchivos {

    public static final String MIME_CARPETA = "application/vnd.google-apps.folder";
    private static final String BASE = "https://www.googleapis.com/drive/v3/files";
    private static final String CAMPOS_ARCHIVO =
            "id,name,mimeType,size,modifiedTime,parents,shared,starred,webViewLink,trashed,capabilities(canDelete)";
    private static final Set<String> REINTENTABLES = Set.of("429", "500", "502", "503", "504");
    private static final long LIMITE_BYTES_ZIP = 512L * 1024 * 1024;

    private final SAlmacenamientoTokens tokens;
    private final SCacheListadosAlmacenamiento cache;
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient clienteHttp;

    public GoogleDriveArchivos(SAlmacenamientoTokens tokens, SCacheListadosAlmacenamiento cache,
                               RestClient.Builder constructorCliente) {
        this.tokens = tokens;
        this.cache = cache;
        this.clienteHttp = constructorCliente.build();
    }

    @Override public String proveedor() { return "GOOGLE_DRIVE"; }

    @Override
    public PaginaArchivos listar(MAlmacenamientoConexion conexion, String carpetaId, String vista, String busqueda,
                                 String orden, String pagina) throws GeneralSecurityException {
        String clave = (carpetaId == null ? "" : carpetaId) + "|" + (vista == null ? "" : vista) + "|"
                + (busqueda == null ? "" : busqueda) + "|" + (orden == null ? "" : orden) + "|" + (pagina == null ? "" : pagina);
        PaginaArchivos cacheada = cache.obtener(conexion, clave);
        if (cacheada != null) return cacheada;

        boolean papelera = "PAPELERA".equalsIgnoreCase(vista);
        String carpeta = carpetaId == null || carpetaId.isBlank() || "RAIZ".equalsIgnoreCase(carpetaId) || "root".equalsIgnoreCase(carpetaId)
                ? "root" : carpetaId;
        String vistaNormalizada = vista == null ? "" : vista.toUpperCase(Locale.ROOT);
        StringBuilder q = new StringBuilder();
        switch (vistaNormalizada) {
            case "PAPELERA" -> q.append("trashed=true");
            case "RECIENTES" -> q.append("trashed=false");
            case "COMPARTIDOS" -> q.append("sharedWithMe=true");
            case "DESTACADOS" -> q.append("starred=true and trashed=false");
            case "UNIDAD", "" -> {
                if (busqueda != null && !busqueda.isBlank()) {
                    q.append("name contains '").append(busqueda.replace("'", "\\'")).append("'");
                    q.append(" and trashed=false");
                } else {
                    q.append("'").append(carpeta).append("' in parents and trashed=false");
                }
            }
            default -> throw new IllegalArgumentException(
                    "Vista no soportada: " + vista + ". Usa unidad, recientes, compartidos, destacados o papelera.");
        }
        String ordenEfectivo = ordenOrdenada(orden);
        if ("RECIENTES".equals(vistaNormalizada)) ordenEfectivo = "modifiedTime desc";
        URI destino = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("q", q.toString())
                .queryParam("pageSize", 50)
                .queryParam("pageToken", pagina == null || pagina.isBlank() ? null : pagina)
                .queryParam("orderBy", ordenEfectivo)
                .queryParam("fields", "nextPageToken,files(" + CAMPOS_ARCHIVO + ")")
                .queryParam("supportsAllDrives", true)
                .queryParam("includeItemsFromAllDrives", true)
                .build()
                .encode()
                .toUri();
        JsonNode respuesta = ejecutar(conexion, "GET", destino, null, null);
        List<AlmacenamientoArchivoDTO> archivos = new ArrayList<>();
        for (JsonNode fila : respuesta.path("files")) archivos.add(aDto(fila));
        PaginaArchivos resultado = new PaginaArchivos(archivos, texto(respuesta.path("nextPageToken")), papelera ? null : carpeta);
        cache.guardar(conexion, clave, resultado);
        return resultado;
    }

    @Override
    public AlmacenamientoArchivoDTO metadatos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("fields", CAMPOS_ARCHIVO)
                .queryParam("supportsAllDrives", true)
                .build().encode().toUri();
        return aDto(ejecutar(conexion, "GET", destino, null, null));
    }

    @Override
    public RecursoContenido contenido(MAlmacenamientoConexion conexion, String fileId, boolean descargar)
            throws GeneralSecurityException {
        return flujoArchivo(conexion, fileId, descargar ? "attachment" : "inline");
    }

    @Override
    public RecursoContenido previsualizacion(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return flujoArchivo(conexion, fileId, "inline");
    }

    @Override
    public AlmacenamientoArchivoDTO crearCarpeta(MAlmacenamientoConexion conexion, String carpetaId, String nombre)
            throws GeneralSecurityException {
        String padre = carpetaId == null || carpetaId.isBlank() ? "root" : carpetaId;
        String cuerpo = "{\"name\":\"" + escapar(nombre) + "\",\"mimeType\":\"" + MIME_CARPETA
                + "\",\"parents\":[\"" + escapar(padre) + "\"]}";
        JsonNode respuesta = ejecutar(conexion, "POST",
                UriComponentsBuilder.fromUriString(BASE)
                        .queryParam("fields", CAMPOS_ARCHIVO)
                        .queryParam("supportsAllDrives", true)
                        .build().encode().toUri(),
                cuerpo, MediaType.APPLICATION_JSON);
        cache.invalidar(conexion);
        return aDto(respuesta);
    }

    @Override
    public AlmacenamientoArchivoDTO subir(MAlmacenamientoConexion conexion, String carpetaId, String nombreArchivo,
                                          String mime, byte[] contenido) throws GeneralSecurityException {
        String padre = carpetaId == null || carpetaId.isBlank() ? "root" : carpetaId;
        URI destino = UriComponentsBuilder.fromUriString("https://www.googleapis.com/upload/drive/v3/files")
                .queryParam("uploadType", "multipart")
                .queryParam("fields", CAMPOS_ARCHIVO)
                .queryParam("supportsAllDrives", true)
                .build().encode().toUri();
        MultipartBodyBuilder partes = new MultipartBodyBuilder();
        String metadatos = "{\"name\":\"" + escapar(nombreArchivo) + "\",\"parents\":[\""
                + escapar(padre) + "\"],\"mimeType\":\"" + escapar(mime) + "\"}";
        partes.part("metadatos", metadatos).contentType(MediaType.APPLICATION_JSON);
        partes.part("archivo", new ByteArrayResource(contenido) {
            @Override public String getFilename() { return nombreArchivo; }
        }).contentType(mime == null || mime.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime));
        JsonNode respuesta = ejecutarMultipart(conexion, destino, partes.build());
        cache.invalidar(conexion);
        return aDto(respuesta);
    }

    @Override
    public AlmacenamientoArchivoDTO actualizar(MAlmacenamientoConexion conexion, String fileId, String nombre,
                                               String idNuevoPadre, Boolean destacar) throws GeneralSecurityException {
        StringBuilder cuerpo = new StringBuilder("{");
        boolean primero = true;
        if (nombre != null && !nombre.isBlank()) {
            cuerpo.append("\"name\":\"").append(escapar(nombre)).append("\""); primero = false;
        }
        if (destacar != null) {
            if (!primero) cuerpo.append(",");
            cuerpo.append("\"starred\":").append(destacar); primero = false;
        }
        cuerpo.append("}");
        UriComponentsBuilder destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("fields", CAMPOS_ARCHIVO)
                .queryParam("supportsAllDrives", true);
        if (idNuevoPadre != null && !idNuevoPadre.isBlank()) {
            AlmacenamientoArchivoDTO actual = metadatos(conexion, fileId);
            String padreActual = actual.idPadre() == null ? "root" : actual.idPadre();
            destino.queryParam("addParents", idNuevoPadre)
                    .queryParam("removeParents", padreActual);
        }
        JsonNode respuesta = ejecutar(conexion, "PATCH", destino.build().encode().toUri(),
                cuerpo.toString(), MediaType.APPLICATION_JSON);
        cache.invalidar(conexion);
        return aDto(respuesta);
    }

    @Override
    public AlmacenamientoArchivoDTO copiar(MAlmacenamientoConexion conexion, String fileId, String idNuevoPadre)
            throws GeneralSecurityException {
        UriComponentsBuilder destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId + "/copy")
                .queryParam("fields", CAMPOS_ARCHIVO)
                .queryParam("supportsAllDrives", true);
        StringBuilder cuerpo = new StringBuilder("{");
        if (idNuevoPadre != null && !idNuevoPadre.isBlank()) cuerpo.append("\"parents\":[\"").append(escapar(idNuevoPadre)).append("\"]");
        cuerpo.append("}");
        JsonNode respuesta = ejecutar(conexion, "POST", destino.build().encode().toUri(),
                cuerpo.toString(), MediaType.APPLICATION_JSON);
        cache.invalidar(conexion);
        return aDto(respuesta);
    }

    @Override
    public void aPapelera(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("supportsAllDrives", true).build().encode().toUri();
        ejecutar(conexion, "PATCH", destino, "{\"trashed\":true}", MediaType.APPLICATION_JSON);
        cache.invalidar(conexion);
    }

    @Override
    public void restaurar(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("supportsAllDrives", true).build().encode().toUri();
        ejecutar(conexion, "PATCH", destino, "{\"trashed\":false}", MediaType.APPLICATION_JSON);
        cache.invalidar(conexion);
    }

    @Override
    public void eliminarDefinitivo(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("supportsAllDrives", true).build().encode().toUri();
        ejecutar(conexion, "DELETE", destino, null, null);
        cache.invalidar(conexion);
    }

    @Override
    public void vaciarPapelera(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("q", "trashed=true")
                .queryParam("pageSize", 100)
                .queryParam("fields", "nextPageToken,files(id)")
                .build().encode().toUri();
        List<String> ids = new ArrayList<>();
        String pagina = null;
        do {
            URI destinoPagina = pagina == null ? destino : UriComponentsBuilder.fromUriString(BASE)
                    .queryParam("q", "trashed=true").queryParam("pageSize", 100)
                    .queryParam("pageToken", pagina).queryParam("fields", "nextPageToken,files(id)")
                    .build().encode().toUri();
            JsonNode respuesta = ejecutar(conexion, "GET", destinoPagina, null, null);
            for (JsonNode fila : respuesta.path("files")) {
                String id = texto(fila.path("id"));
                if (!id.isBlank()) ids.add(id);
            }
            pagina = texto(respuesta.path("nextPageToken"));
        } while (pagina != null && !pagina.isBlank());
        for (String id : ids) {
            URI destinoBorrar = UriComponentsBuilder.fromUriString(BASE + "/" + id)
                    .queryParam("supportsAllDrives", true).build().encode().toUri();
            ejecutar(conexion, "DELETE", destinoBorrar, null, null);
        }
        cache.invalidar(conexion);
    }

    @Override
    public List<AlmacenamientoPermisoDTO> listarPermisos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId + "/permissions")
                .queryParam("fields", "permissions(id,type,role,emailAddress,displayName)")
                .queryParam("supportsAllDrives", true)
                .build().encode().toUri();
        JsonNode respuesta = ejecutar(conexion, "GET", destino, null, null);
        List<AlmacenamientoPermisoDTO> permisos = new ArrayList<>();
        for (JsonNode fila : respuesta.path("permissions")) {
            permisos.add(new AlmacenamientoPermisoDTO(texto(fila.path("id")), texto(fila.path("type")),
                    texto(fila.path("role")), texto(fila.path("emailAddress")), texto(fila.path("displayName"))));
        }
        return permisos;
    }

    @Override
    public AlmacenamientoPermisoDTO compartir(MAlmacenamientoConexion conexion, String fileId, String rol, String tipo,
                                              String email) throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId + "/permissions")
                .queryParam("supportsAllDrives", true)
                .queryParam("sendNotificationEmail", email != null && !email.isBlank())
                .build().encode().toUri();
        StringBuilder cuerpo = new StringBuilder("{\"role\":\"")
                .append(rolValido(rol)).append("\",\"type\":\"").append(tipoValido(tipo)).append("\"");
        if (email != null && !email.isBlank() && !"anyone".equalsIgnoreCase(tipo)) {
            cuerpo.append(",\"emailAddress\":\"").append(escapar(email)).append("\"");
        }
        cuerpo.append("}");
        JsonNode respuesta = ejecutar(conexion, "POST", destino, cuerpo.toString(), MediaType.APPLICATION_JSON);
        return new AlmacenamientoPermisoDTO(texto(respuesta.path("id")), texto(respuesta.path("type")),
                texto(respuesta.path("role")), texto(respuesta.path("emailAddress")), texto(respuesta.path("displayName")));
    }

    @Override
    public void revocarPermiso(MAlmacenamientoConexion conexion, String fileId, String permisoId)
            throws GeneralSecurityException {
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId + "/permissions/" + permisoId)
                .queryParam("supportsAllDrives", true).build().encode().toUri();
        ejecutar(conexion, "DELETE", destino, null, null);
    }

    @Override
    public byte[] descargarLote(MAlmacenamientoConexion conexion, List<String> fileIds) throws GeneralSecurityException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (String id : fileIds == null ? List.<String>of() : fileIds) {
                if (id == null || id.isBlank()) continue;
                RecursoContenido recurso = flujoArchivo(conexion, id, "attachment");
                byte[] bytes = recurso.flujo().readAllBytes();
                total += bytes.length;
                if (total > LIMITE_BYTES_ZIP) {
                    throw new IllegalStateException("El lote supera el límite de descarga.");
                }
                zip.putNextEntry(new ZipEntry(nombreSeguro(recurso.nombreArchivo())));
                zip.write(bytes);
                zip.closeEntry();
            }
        } catch (GeneralSecurityException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo preparar la descarga en lote.", error);
        }
        return buffer.toByteArray();
    }

    // ---------- Internos ----------

    private RecursoContenido flujoArchivo(MAlmacenamientoConexion conexion, String fileId, String disposicion)
            throws GeneralSecurityException {
        AlmacenamientoArchivoDTO metadatos = metadatos(conexion, fileId);
        URI destino = UriComponentsBuilder.fromUriString(BASE + "/" + fileId)
                .queryParam("alt", "media")
                .queryParam("supportsAllDrives", true)
                .build().encode().toUri();
        byte[] bytes = ejecutarBytes(conexion, destino);
        String nombre = disposicion.equals("attachment")
                ? nombreSeguro(metadatos.nombre()) : metadatos.nombre();
        return new RecursoContenido(new ByteArrayInputStream(bytes),
                metadatos.tamanio() == null ? (long) bytes.length : metadatos.tamanio(),
                metadatos.mime() == null || metadatos.mime().isBlank() ? "application/octet-stream" : metadatos.mime(),
                nombre);
    }

    private JsonNode ejecutar(MAlmacenamientoConexion conexion, String metodo, URI destino, String cuerpo,
                              MediaType tipo) throws GeneralSecurityException {
        try {
            String token = tokens.tokenVigente(conexion);
            return cuerpoDeString(solicitud(token, metodo, destino, cuerpo, tipo).retrieve().body(String.class));
        } catch (HttpStatusCodeException error) {
            if (RECHAZADO.equals(String.valueOf(error.getStatusCode().value()))) {
                return trasRechazo(conexion, token ->
                        cuerpoDeString(solicitud(token, metodo, destino, cuerpo, tipo).retrieve().body(String.class)));
            }
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo contactar Google Drive.", error);
        }
    }

    private JsonNode ejecutarMultipart(MAlmacenamientoConexion conexion, URI destino,
                                       MultiValueMap<String, org.springframework.http.HttpEntity<?>> partes)
            throws GeneralSecurityException {
        try {
            String token = tokens.tokenVigente(conexion);
            return cuerpoDeString(solicitud(token, "POST", destino, null, null)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(String.class));
        } catch (HttpStatusCodeException error) {
            if (RECHAZADO.equals(String.valueOf(error.getStatusCode().value()))) {
                return trasRechazo(conexion, token ->
                        cuerpoDeString(solicitud(token, "POST", destino, null, null)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .body(partes)
                                .retrieve()
                                .body(String.class)));
            }
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo subir el archivo a Google Drive.", error);
        }
    }

    private byte[] ejecutarBytes(MAlmacenamientoConexion conexion, URI destino) throws GeneralSecurityException {
        try {
            String token = tokens.tokenVigente(conexion);
            return solicitud(token, "GET", destino, null, null).retrieve().body(byte[].class);
        } catch (HttpStatusCodeException error) {
            if (RECHAZADO.equals(String.valueOf(error.getStatusCode().value()))) {
                return trasRechazo(conexion, token ->
                        solicitud(token, "GET", destino, null, null).retrieve().body(byte[].class));
            }
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo leer el archivo desde Google Drive.", error);
        }
    }

    /** Constante del código 401 (token rechazado por el proveedor). */
    private static final String RECHAZADO = "401";

    @FunctionalInterface private interface Envio<T> { T enviar(String token) throws GeneralSecurityException; }

    /**
     * Ante un 401 del proveedor fuerza la renovación del token y reintenta una vez;
     * si vuelve a fallar marca REQUIERE_RECONEXION y responde con mensaje accionable.
     */
    private <T> T trasRechazo(MAlmacenamientoConexion conexion, Envio<T> envio) throws GeneralSecurityException {
        try {
            return envio.enviar(tokens.tokenVigente(conexion, true));
        } catch (RestClientException segundo) {
            tokens.marcarReconexion(conexion, "El proveedor rechazó el acceso tras renovar el token.");
            throw new ConexionRequiereReconexionException(
                    "La cuenta requiere reconexión para actualizar permisos o alcance del acceso.", segundo);
        } catch (ConexionRequiereReconexionException error) {
            tokens.marcarReconexion(conexion, null);
            throw error;
        }
    }

    private RestClient.RequestBodySpec solicitud(String token, String metodo, URI destino,
                                                 String cuerpo, MediaType tipo) {
        RestClient.RequestBodySpec especificacion = clienteHttp.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(destino)
                .headers(h -> h.setBearerAuth(token));
        if (cuerpo != null) especificacion = especificacion.contentType(tipo).body(cuerpo);
        return especificacion;
    }

    /** Traduce errores del proveedor: 401/403 → reconexión, 404 → recurso inexistente, 429 → cuota. */
    private RuntimeException errorConcreto(HttpStatusCodeException error) throws GeneralSecurityException {
        int estado = error.getStatusCode().value();
        if (estado == 401 || estado == 403) {
            // 403 (permisos/scope insuficiente) requiere autorización nueva del usuario.
            throw new ConexionRequiereReconexionException(
                    "La cuenta requiere reconexión para actualizar permisos o alcance del acceso.", error);
        }
        if (estado == 404) return new AlmacenamientoRecursoNoEncontrado("El archivo o carpeta no existe en el proveedor.");
        if (estado == 429) return new IllegalStateException("Se agotó la cuota del proveedor; inténtalo más tarde.");
        if (REINTENTABLES.contains(String.valueOf(estado))) return new IllegalStateException("Google Drive no respondió (" + estado + ").");
        return new IllegalStateException("Google Drive rechazó la operación (" + estado + ").");
    }

    /** Reintenta con backoff exponencial en 429/5xx; delega el resto a ejecutar. */
    JsonNode ejecutarConReintentos(MAlmacenamientoConexion conexion, String metodo, URI destino, String cuerpo,
                                   MediaType tipo) throws GeneralSecurityException {
        int intentos = 0;
        while (true) {
            try {
                return ejecutar(conexion, metodo, destino, cuerpo, tipo);
            } catch (IllegalStateException error) {
                if (!esReintentable(error) || intentos >= 2) throw error;
                intentos++;
                try { Thread.sleep((long) Math.pow(2, intentos) * 250); }
                catch (InterruptedException interrupcion) { Thread.currentThread().interrupt(); throw error; }
            }
        }
    }

    private boolean esReintentable(IllegalStateException error) {
        String mensaje = error.getMessage() == null ? "" : error.getMessage();
        return mensaje.matches(".*Google Drive no respondió \\(\\d+\\)\\.") || mensaje.contains("cuota");
    }

    private static AlmacenamientoArchivoDTO aDto(JsonNode fila) {
        String mime = texto(fila.path("mimeType"));
        boolean carpeta = MIME_CARPETA.equals(mime);
        return new AlmacenamientoArchivoDTO(
                texto(fila.path("id")),
                texto(fila.path("name")),
                carpeta ? AlmacenamientoArchivoDTO.TIPO_CARPETA : AlmacenamientoArchivoDTO.TIPO_ARCHIVO,
                mime,
                fila.path("size").isMissingNode() || fila.path("size").isNull() ? null : fila.path("size").asLong(),
                texto(fila.path("modifiedTime")),
                texto(fila.path("parents").path(0)),
                fila.path("shared").asBoolean(false),
                fila.path("capabilities").path("canDelete").asBoolean(false),
                fila.path("starred").asBoolean(false),
                texto(fila.path("webViewLink")));
    }

    private static String ordenOrdenada(String orden) {
        String valor = orden == null ? "" : orden.toUpperCase(Locale.ROOT).replace('_', ' ').trim();
        return switch (valor) {
            case "NOMBRE", "NOMBRE ASC" -> "name";
            case "NOMBRE DESC" -> "name desc";
            case "FECHA", "FECHA ASC", "MODIFICADO ASC" -> "modifiedTime asc";
            case "FECHA DESC", "MODIFICADO DESC" -> "modifiedTime desc";
            case "TAMANIO", "TAMANIO ASC" -> "size";
            case "TAMANIO DESC" -> "size desc";
            default -> "modifiedTime desc";
        };
    }

    private static String rolValido(String rol) {
        return switch (rol == null ? "" : rol.toUpperCase(Locale.ROOT)) {
            case "LECTOR" -> "reader";
            case "COMENTADOR" -> "commenter";
            case "EDITOR" -> "writer";
            case "PROPIETARIO" -> "owner";
            default -> "reader";
        };
    }

    private static String tipoValido(String tipo) {
        return switch (tipo == null ? "" : tipo.toUpperCase(Locale.ROOT)) {
            case "USUARIO" -> "user";
            case "GRUPO" -> "group";
            case "DOMINIO" -> "domain";
            case "ANYONE", "PUBLICO" -> "anyone";
            default -> "user";
        };
    }

    /** Sanea el nombre para Content-Disposition y entradas ZIP. */
    static String nombreSeguro(String nombre) {
        String limpio = nombre == null ? "archivo" : nombre.replaceAll("[\\r\\n\"\\\\]", "_").trim();
        return limpio.isBlank() ? "archivo" : limpio;
    }

    private static String escapar(String valor) {
        return valor == null ? "" : valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String texto(JsonNode nodo) {
        return nodo == null || nodo.isNull() ? "" : nodo.asText("");
    }

    private JsonNode cuerpoDeString(String contenido) {
        if (contenido == null || contenido.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(contenido);
        } catch (Exception error) {
            throw new IllegalStateException("Google Drive devolvió una respuesta no válida.", error);
        }
    }

    /** Autorización básica usada solo en pruebas de utilidades (no en peticiones reales). */
    static String base64(String valor) { return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8)); }
}
