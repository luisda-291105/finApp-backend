package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.DTO.GrupoMensajeDTO;
import com.luisda.fin_app_back.DTO.GrupoMensajeSolicitudDTO;
import com.luisda.fin_app_back.DTO.GrupoMiembroDTO;
import com.luisda.fin_app_back.DTO.GrupoUsuarioResumenDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.AccesoGrupoDenegadoException;
import com.luisda.fin_app_back.Services.SGrupoChat;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @uso               Chat del grupo entre miembros existentes y administración de integrantes
 * @capa              Controlador
 * @responsabilidades Listar/enviar mensajes y listar/añadir/quitar integrantes por grupo
 * @datos             Delega en SGrupoChat y valida la sesión del usuario
 * @dependencias      SGrupoChat, SUsuarioActivo, Spring Data JPA
 * @usadoPor          web
 */
@RestController
@Tag(name = "Grupos", description = "Creación y administración de grupos.")
@RequestMapping("/grupo")
public class CGrupoChat {
    private final SGrupoChat chat;
    private final SUsuarioActivo usuarioActivo;

    public CGrupoChat(SGrupoChat chat, SUsuarioActivo usuarioActivo) {
        this.chat = chat;
        this.usuarioActivo = usuarioActivo;
    }

    // ── Mensajes ────────────────────────────────────────────────────────────────

    /** Lista los mensajes del grupo, en orden cronológico. */
    @GetMapping("/{idGrupo}/mensajes")
    public ResponseEntity<?> listar(@PathVariable String idGrupo, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoMensajeDTO> mensajes = chat.listarMensajes(usuario.get().getIdUsuario(), idGrupo);
            return ResponseEntity.ok(mensajes);
        } catch (NoSuchElementException error) {
            return noEncontrado(error);
        } catch (AccesoGrupoDenegadoException error) {
            return prohibido(error);
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    /** Persiste el mensaje del miembro y lo devuelve con la identidad del autor. */
    @PostMapping("/{idGrupo}/mensajes")
    public ResponseEntity<?> enviar(@PathVariable String idGrupo,
                                    @RequestBody GrupoMensajeSolicitudDTO solicitud,
                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            GrupoMensajeDTO mensaje =
                    chat.enviarMensaje(usuario.get().getIdUsuario(), idGrupo, solicitud);
            return ResponseEntity.status(HttpStatus.CREATED).body(mensaje);
        } catch (NoSuchElementException error) {
            return noEncontrado(error);
        } catch (AccesoGrupoDenegadoException error) {
            return prohibido(error);
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    // ── Miembros ────────────────────────────────────────────────────────────────

    /** Lista los integrantes del grupo (incluye al propietario). */
    @GetMapping("/{idGrupo}/miembros")
    public ResponseEntity<?> listarMiembros(@PathVariable String idGrupo, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoMiembroDTO> miembros = chat.listarMiembros(usuario.get().getIdUsuario(), idGrupo);
            return ResponseEntity.ok(miembros);
        } catch (NoSuchElementException error) {
            return noEncontrado(error);
        } catch (AccesoGrupoDenegadoException error) {
            return prohibido(error);
        }
    }

    /** Añade un integrante por identificador, correo, teléfono o nombre (uno solo; para varios, usar la búsqueda previa). */
    @PostMapping("/{idGrupo}/miembros")
    public ResponseEntity<?> agregarMiembro(@PathVariable String idGrupo,
                                            @RequestBody Map<String, String> solicitud,
                                            HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        String objetivo = solicitud == null ? null : solicitud.get("usuario");
        if (objetivo == null) objetivo = solicitud == null ? null : solicitud.get("idUsuario");
        try {
            List<GrupoMiembroDTO> miembros =
                    chat.agregarMiembro(usuario.get().getIdUsuario(), idGrupo, objetivo);
            return ResponseEntity.status(HttpStatus.CREATED).body(miembros);
        } catch (NoSuchElementException error) {
            return noEncontrado(error);
        } catch (AccesoGrupoDenegadoException error) {
            return prohibido(error);
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Quita un integrante del grupo (no el propietario). */
    @DeleteMapping("/{idGrupo}/miembros/{idUsuarioMiembro}")
    public ResponseEntity<?> eliminarMiembro(@PathVariable String idGrupo,
                                             @PathVariable String idUsuarioMiembro,
                                             HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoMiembroDTO> miembros =
                    chat.eliminarMiembro(usuario.get().getIdUsuario(), idGrupo, idUsuarioMiembro);
            return ResponseEntity.ok(miembros);
        } catch (NoSuchElementException error) {
            return noEncontrado(error);
        } catch (AccesoGrupoDenegadoException error) {
            return prohibido(error);
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    /** Busca usuarios registrados por fragmento (identificación, correo, teléfono o nombre). */
    @GetMapping("/usuarios/buscar")
    public ResponseEntity<?> buscarUsuarios(@RequestParam(required = false) String q,
                                            HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoUsuarioResumenDTO> resultados = chat.buscarUsuarios(q);
            return ResponseEntity.ok(resultados);
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────

    private ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
    }

    private ResponseEntity<?> noEncontrado(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(RespuestaModalDTO.error(error.getMessage(), null));
    }

    private ResponseEntity<?> prohibido(AccesoGrupoDenegadoException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RespuestaModalDTO.error(error.getMessage(), null));
    }

    private ResponseEntity<?> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(RespuestaModalDTO.error(error.getMessage(), null));
    }
}
