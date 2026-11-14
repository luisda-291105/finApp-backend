package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.SuscripcionEstadoDTO;
import com.luisda.fin_app_back.DTO.SuscripcionSolicitudDTO;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import com.luisda.fin_app_back.Services.SSuscripcion;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * @uso               Puntos de conexión de la API para las suscripciones
 * @capa              Controlador
 * @responsabilidades CRUD de suscripciones manuales y consulta unificada de suscripciones
 * @datos             Usa SSuscripcion con el usuario derivado de la sesión
 * @dependencias      SSuscripcion, SUsuarioActivo
 * @usadoPor          Web
 */
@RestController
@Tag(name = "Suscripciones", description = "Suscripciones manuales del usuario y consulta unificada con las detectadas en Gmail.")
@RequestMapping("/suscripcion")
public class CSuscripcion {

    private final SSuscripcion suscripciones;
    private final SUsuarioActivo usuarioActivo;

    public CSuscripcion(SSuscripcion suscripciones, SUsuarioActivo usuarioActivo) {
        this.suscripciones = suscripciones;
        this.usuarioActivo = usuarioActivo;
    }

    /** Registra una suscripción manual del usuario de la sesión. */
    @PostMapping
    @Operation(summary = "Registra una suscripción manual",
            description = "Crea una suscripción manual (nombre, monto, fecha de cobro, categoría e importancia) "
                    + "para el usuario de la sesión. Devuelve la suscripción creada con su identificador.")
    public ResponseEntity<?> crear(@RequestBody SuscripcionSolicitudDTO solicitud,
                                   HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(this.suscripciones.crear(idUsuario.get(), solicitud));
        } catch (SSuscripcion.DatosInvalidosException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), error.getCampo()));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Lista las suscripciones del usuario: manuales y detectadas en Gmail. */
    @GetMapping
    @Operation(summary = "Lista las suscripciones del usuario",
            description = "Devuelve primero las suscripciones manuales ordenadas por fecha de cobro y después las "
                    + "detectadas en Gmail ordenadas por actividad reciente. Use el filtro origen para limitar el resultado.")
    public ResponseEntity<?> listar(
            @Parameter(description = "Filtro opcional de procedencia: MANUAL o GMAIL")
            @RequestParam(required = false) String origen,
            HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(this.suscripciones.listar(idUsuario.get(), normalizarOrigen(origen)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "origen"));
        }
    }

    /** Obtiene una suscripción del usuario por identificador. */
    @GetMapping("/{idSuscripcion}")
    @Operation(summary = "Consulta una suscripción por identificador",
            description = "Devuelve la suscripción indicada si pertenece al usuario de la sesión.")
    public ResponseEntity<?> obtener(@PathVariable String idSuscripcion, HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(this.suscripciones.obtener(idUsuario.get(), idSuscripcion));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Actualiza una suscripción manual. */
    @PutMapping("/{idSuscripcion}")
    @Operation(summary = "Actualiza una suscripción manual",
            description = "Reemplaza los datos de una suscripción manual del usuario de la sesión. "
                    + "Las suscripciones detectadas en Gmail no se editan desde aquí.")
    public ResponseEntity<?> actualizar(@PathVariable String idSuscripcion,
                                        @RequestBody SuscripcionSolicitudDTO solicitud,
                                        HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(this.suscripciones.actualizar(idUsuario.get(), idSuscripcion, solicitud));
        } catch (SSuscripcion.DatosInvalidosException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), error.getCampo()));
        } catch (SSuscripcion.NoEditableException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Activa o pausa una suscripción manual. */
    @PatchMapping("/{idSuscripcion}/estado")
    @Operation(summary = "Activa o pausa una suscripción manual",
            description = "Cambia el estado de la suscripción: activa (true) o pausada (false).")
    public ResponseEntity<?> cambiarEstado(@PathVariable String idSuscripcion,
                                           @RequestBody SuscripcionEstadoDTO estado,
                                           HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(this.suscripciones.cambiarEstado(idUsuario.get(), idSuscripcion, estado.activa()));
        } catch (SSuscripcion.DatosInvalidosException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), error.getCampo()));
        } catch (SSuscripcion.NoEditableException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Elimina una suscripción manual. */
    @DeleteMapping("/{idSuscripcion}")
    @Operation(summary = "Elimina una suscripción manual",
            description = "Elimina la suscripción manual indicada. Las detectadas en Gmail se limpian al desconectar la cuenta.")
    public ResponseEntity<?> eliminar(@PathVariable String idSuscripcion, HttpServletRequest request) {
        Optional<String> idUsuario = this.usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            this.suscripciones.eliminar(idUsuario.get(), idSuscripcion);
            return ResponseEntity.ok(RespuestaModalDTO.exito("Suscripción eliminada.", null));
        } catch (SSuscripcion.NoEditableException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    private OrigenSuscripcion normalizarOrigen(String origen) {
        if (origen == null || origen.isBlank()) return null;
        try {
            return OrigenSuscripcion.valueOf(origen.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("El origen debe ser MANUAL o GMAIL.");
        }
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("Inicia sesión para consultar tus suscripciones.", null));
    }
}
