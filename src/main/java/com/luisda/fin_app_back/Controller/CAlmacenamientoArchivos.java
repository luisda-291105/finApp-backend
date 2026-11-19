package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.OrigenesWeb;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.AlmacenamientoProveedorNoSoportado;
import com.luisda.fin_app_back.Services.AlmacenamientoRecursoNoEncontrado;
import com.luisda.fin_app_back.Services.ConexionRequiereReconexionException;
import com.luisda.fin_app_back.Services.SAlmacenamientoArchivos;import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * @uso               Operaciones de archivos sobre una conexión de almacenamiento propia
 * @funciones         listar, metadatos, contenido, previsualizacion, crearCarpeta, subir, actualizar,
 *                    copiar, aPapelera, restaurar, vaciarPapelera, permisos y lote
 * @datos             El usuario se deriva siempre de la sesión; el token del proveedor nunca sale del backend
 * @eventos           Lectura y escritura de archivos en el proveedor (Drive)
 * @estados           Ninguno
 * @usadoPor          Frontend (vistas de almacenamiento)
 */
@RestController
@Tag(name = "AlmacenamientoArchivos", description = "Archivos de una conexión de almacenamiento en la nube.")
@RequestMapping("/api/almacenamiento/conexiones/{idConexion}/archivos")
public class CAlmacenamientoArchivos {

    private final SAlmacenamientoArchivos proveedor;
    private final SUsuarioActivo usuarioActivo;
    private final IAlmacenamientoConexiones repositorio;
    private final List<String> origenesPermitidos;

    public CAlmacenamientoArchivos(
            @org.springframework.beans.factory.annotation.Qualifier("sArchivosPorConexion") SAlmacenamientoArchivos proveedor,
            SUsuarioActivo usuarioActivo,
                                   IAlmacenamientoConexiones repositorio,
                                   @org.springframework.beans.factory.annotation.Value("${finapp.cors.allowed-origins:http://localhost:5173}") String origenes) {
        this.proveedor = proveedor;
        this.usuarioActivo = usuarioActivo;
        this.repositorio = repositorio;
        this.origenesPermitidos = OrigenesWeb.normalizarLista(origenes);
    }

    @GetMapping
    public ResponseEntity<?> listar(@PathVariable String idConexion,
                                    @RequestParam(required = false) String carpetaId,
                                    @RequestParam(required = false) String vista,
                                    @RequestParam(required = false) String busqueda,
                                    @RequestParam(required = false) String orden,
                                    @RequestParam(required = false) String ordenCampo,
                                    @RequestParam(required = false) String ordenDireccion,
                                    @RequestParam(required = false) String pagina,
                                    HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            String ordenEfectivo = ordenCampo != null && !ordenCampo.isBlank()
                    ? ordenCampo.toUpperCase() + "_" + (ordenDireccion == null ? "asc" : ordenDireccion.toUpperCase())
                    : orden;
            return ResponseEntity.ok(proveedor.listar(conexion, carpetaId, vista, busqueda, ordenEfectivo, pagina));
        } catch (Exception error) { return respuestaError(error); }
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<?> metadatos(@PathVariable String idConexion, @PathVariable String fileId,
                                       HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            return ResponseEntity.ok(proveedor.metadatos(conexion, fileId));
        } catch (Exception error) { return respuestaError(error); }
    }

    @GetMapping("/{fileId}/contenido")
    public ResponseEntity<?> contenido(@PathVariable String idConexion, @PathVariable String fileId,
                                       @RequestParam(defaultValue = "false") boolean descargar,
                                       HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            var recurso = proveedor.contenido(conexion, fileId, descargar);
            return flujo(recurso, descargar);
        } catch (Exception error) { return respuestaError(error); }
    }

    @GetMapping("/{fileId}/previsualizacion")
    public ResponseEntity<?> previsualizacion(@PathVariable String idConexion, @PathVariable String fileId,
                                              HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            return flujo(proveedor.previsualizacion(conexion, fileId), false);
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/carpeta")
    public ResponseEntity<?> crearCarpeta(@PathVariable String idConexion,
                                          @RequestBody SolicitudCarpeta solicitud, HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("mensaje","Indica el nombre de la carpeta."));
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(proveedor.crearCarpeta(conexion, solicitud.carpetaId(), solicitud.nombre()));
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/subir")
    public ResponseEntity<?> subir(@PathVariable String idConexion,
                                   @RequestParam(required = false) String idPadre,
                                   @RequestParam("archivo") MultipartFile archivo,
                                   HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            if (archivo == null || archivo.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("mensaje","Selecciona un archivo para subir."));
            }
            AlmacenamientoArchivoDTO creado = proveedor.subir(conexion, idPadre,
                    nombreSano(archivo.getOriginalFilename()), archivo.getContentType(), archivo.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (Exception error) { return respuestaError(error); }
    }

    @PatchMapping("/{fileId}")
    public ResponseEntity<?> actualizar(@PathVariable String idConexion, @PathVariable String fileId,
                                        @RequestBody SolicitudActualizar solicitud, HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            return ResponseEntity.ok(proveedor.actualizar(conexion, fileId,
                    solicitud == null ? null : solicitud.nombre(),
                    solicitud == null ? null : solicitud.idNuevoPadre(),
                    solicitud == null ? null : solicitud.destacar()));
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/{fileId}/copiar")
    public ResponseEntity<?> copiar(@PathVariable String idConexion, @PathVariable String fileId,
                                    @RequestBody(required = false) SolicitudCarpeta solicitud,
                                    HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(proveedor.copiar(conexion, fileId, solicitud == null ? null : solicitud.carpetaId()));
        } catch (Exception error) { return respuestaError(error); }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> aPapelera(@PathVariable String idConexion, @PathVariable String fileId,
                                       HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            proveedor.aPapelera(conexion, fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/{fileId}/restaurar")
    public ResponseEntity<?> restaurar(@PathVariable String idConexion, @PathVariable String fileId,
                                       HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            proveedor.restaurar(conexion, fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception error) { return respuestaError(error); }
    }

    @DeleteMapping("/{fileId}/permanente")
    public ResponseEntity<?> eliminarDefinitivo(@PathVariable String idConexion, @PathVariable String fileId,
                                                HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            proveedor.eliminarDefinitivo(conexion, fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception error) { return respuestaError(error); }
    }

    @DeleteMapping("/papelera")
    public ResponseEntity<?> vaciarPapelera(@PathVariable String idConexion, HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            proveedor.vaciarPapelera(conexion);
            return ResponseEntity.noContent().build();
        } catch (Exception error) { return respuestaError(error); }
    }

    @GetMapping("/{fileId}/permisos")
    public ResponseEntity<?> listarPermisos(@PathVariable String idConexion, @PathVariable String fileId,
                                            HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            return ResponseEntity.ok(proveedor.listarPermisos(conexion, fileId));
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/{fileId}/permisos")
    public ResponseEntity<?> compartir(@PathVariable String idConexion, @PathVariable String fileId,
                                       @RequestBody SolicitudPermiso solicitud, HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            if (solicitud == null || solicitud.rol() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje","Indica el rol de compartición."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(proveedor.compartir(conexion, fileId,
                    solicitud.rol(), solicitud.tipo(), solicitud.email()));
        } catch (Exception error) { return respuestaError(error); }
    }

    @DeleteMapping("/{fileId}/permisos/{permisoId}")
    public ResponseEntity<?> revocarPermiso(@PathVariable String idConexion, @PathVariable String fileId,
                                            @PathVariable String permisoId, HttpServletRequest request) {
        if (!origenValido(request)) return prohibido();
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            proveedor.revocarPermiso(conexion, fileId, permisoId);
            return ResponseEntity.noContent().build();
        } catch (Exception error) { return respuestaError(error); }
    }

    @PostMapping("/lote")
    public ResponseEntity<?> lote(@PathVariable String idConexion, @RequestBody SolicitudLote solicitud,
                                  HttpServletRequest request) {
        try {
            MAlmacenamientoConexion conexion = conexionPropia(request, idConexion);
            byte[] zip = proveedor.descargarLote(conexion, solicitud == null ? null : solicitud.ids());
            if (zip == null || zip.length == 0) {
                return ResponseEntity.badRequest().body(Map.of("mensaje","No se seleccionaron archivos válidos."));
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lote-archivos.zip\"")
                    .body(zip);
        } catch (Exception error) { return respuestaError(error); }
    }

    // ---------- Internos ----------

    /** Conexión válida solo si existe y pertenece al usuario de la sesión (404 si no, sin revelar existencia). */
    private MAlmacenamientoConexion conexionPropia(HttpServletRequest request, String idConexion) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) throw new SesionRequeridaException();
        MAlmacenamientoConexion conexion = repositorio.findByIdConexionAndUsuario_IdUsuario(idConexion, usuario.getIdUsuario())
                .orElseThrow(AlmacenamientoRecursoNoEncontrado::new);
        // Nextcloud se autentica con credencial de aplicación (no usa refresh_token); solo exige ACTIVA + credencial.
        boolean operable = "ACTIVA".equalsIgnoreCase(conexion.getEstado())
                || ("NEXTCLOUD".equals(conexion.getProveedor()) && conexion.getCredencialCifrada() != null
                        && !conexion.getCredencialCifrada().isBlank());
        if (!operable) {
            throw new ConexionRequiereReconexionException(
                    "La cuenta requiere reconexión para actualizar permisos o alcance del acceso.");
        }
        return conexion;
    }

    private ResponseEntity<?> flujo(SAlmacenamientoArchivos.RecursoContenido recurso, boolean descargar) {
        HttpHeaders cabeceras = new HttpHeaders();
        String nombre = recurso.nombreArchivo() == null ? "archivo" : recurso.nombreArchivo().trim().replaceAll("[\\r\\n\"]", "_");
        cabeceras.set(HttpHeaders.CONTENT_DISPOSITION,
                (descargar ? "attachment" : "inline") + "; filename=\"" + nombre + "\"; filename*=UTF-8''"
                        + java.net.URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok()
                .headers(cabeceras)
                .contentType(MediaType.parseMediaType(recurso.mime()))
                .body(new InputStreamResource(recurso.flujo()));
    }

    private static String nombreSano(String nombreOriginal) {
        String nombre = nombreOriginal == null ? "archivo" : nombreOriginal;
        int barra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
        if (barra >= 0) nombre = nombre.substring(barra + 1);
        return nombre.trim().replaceAll("[\\r\\n]", "_");
    }

    private boolean origenValido(HttpServletRequest request) {
        return OrigenesWeb.permitido(request.getHeader(HttpHeaders.ORIGIN), origenesPermitidos);
    }

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("mensaje","Origen no autorizado para esta operación."));
    }

    /** Traduce las excepciones del proveedor a estados HTTP con mensajes seguros (siempre con "mensaje"). */
    static ResponseEntity<?> respuestaError(Exception error) {
        if (error instanceof SesionRequeridaException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", "No hay una sesión activa."));
        }
        if (error instanceof ConexionRequiereReconexionException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "mensaje", error.getMessage() == null ? "La conexión necesita reconexión." : error.getMessage(),
                    "message", error.getMessage() == null ? "La conexión necesita reconexión." : error.getMessage(),
                    "codigo", "CONEXION_REQUIERE_RECONEXION"));
        }
        if (error instanceof AlmacenamientoRecursoNoEncontrado) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("mensaje", "El recurso solicitado no existe."));
        }
        if (error instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", error.getMessage()));
        }
        if (error instanceof AlmacenamientoProveedorNoSoportado) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", error.getMessage()));
        }
        if (error instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("mensaje", error.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "No se pudo completar la operación. Inténtalo de nuevo más tarde."));
    }

    static class SesionRequeridaException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public record SolicitudCarpeta(String carpetaId, String nombre) { }
    public record SolicitudActualizar(String nombre, String idNuevoPadre, Boolean destacar) { }
    public record SolicitudPermiso(String rol, String tipo, String email) { }
    public record SolicitudLote(List<String> ids) { }
}
