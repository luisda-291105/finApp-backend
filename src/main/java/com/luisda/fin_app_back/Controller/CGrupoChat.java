package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.DTO.GrupoMensajeDTO;
import com.luisda.fin_app_back.DTO.GrupoMensajeSolicitudDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.SGrupoChat;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * @uso               Chat con el asistente de IA acotado a un grupo
 * @capa              Controlador
 * @responsabilidades Consulta y envío de mensajes del asistente por grupo
 * @datos             Delega en SGrupoChat y valida la sesión del usuario
 * @dependencias      SGrupoChat, SUsuarioActivo, Spring Data JPA
 * @usadoPor          web
 */
@RestController
@Tag(name = "Grupos", description = "Creación y administración de grupos.")
@RequestMapping("/grupo/{idGrupo}/mensajes")
public class CGrupoChat {
    private final SGrupoChat chat;
    private final SUsuarioActivo usuarioActivo;

    public CGrupoChat(SGrupoChat chat, SUsuarioActivo usuarioActivo) {
        this.chat = chat;
        this.usuarioActivo = usuarioActivo;
    }

    /** Lista los mensajes del grupo, ordenados cronológicamente. */
    @GetMapping
    public ResponseEntity<?> listar(@PathVariable String idGrupo, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoMensajeDTO> mensajes = chat.listar(usuario.get().getIdUsuario(), idGrupo);
            return ResponseEntity.ok(mensajes);
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    /** Persiste el mensaje del usuario y devuelve la respuesta del asistente (ambos persistidos). */
    @PostMapping
    public ResponseEntity<?> enviar(@PathVariable String idGrupo,
                                    @RequestBody GrupoMensajeSolicitudDTO solicitud,
                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            List<GrupoMensajeDTO> respuesta =
                    chat.enviar(usuario.get().getIdUsuario(), idGrupo, solicitud);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    private ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
    }
}
