package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.SolicitudMensajeTelegramDTO;
import com.luisda.fin_app_back.Services.STelegram;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/** Endpoints privados para vincular la cuenta del usuario con el bot de Telegram. */
@RestController
@Tag(name = "Telegram", description = "Vínculo privado y mensajería con el bot de Telegram.")
@RequestMapping("/api/telegram")
public class CTelegram {
    private final STelegram telegram;
    private final SUsuarioActivo usuarioActivo;

    public CTelegram(STelegram telegram, SUsuarioActivo usuarioActivo) {
        this.telegram = telegram;
        this.usuarioActivo = usuarioActivo;
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estado(HttpServletRequest solicitud) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(RespuestaModalDTO.exito("Estado de Telegram.", telegram.estado(idUsuario.get())));
    }

    @GetMapping("/mensajes")
    public ResponseEntity<?> mensajes(HttpServletRequest solicitud) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(RespuestaModalDTO.exito("Mensajes de Telegram.", telegram.mensajes(idUsuario.get())));
    }

    @PostMapping("/enlace")
    public ResponseEntity<?> crearEnlace(HttpServletRequest solicitud) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(RespuestaModalDTO.exito("Abre el enlace y pulsa Iniciar en Telegram.", telegram.crearEnlace(idUsuario.get())));
        } catch (IllegalStateException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    @DeleteMapping("/enlace")
    public ResponseEntity<?> desvincular(HttpServletRequest solicitud) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        telegram.desvincular(idUsuario.get());
        return ResponseEntity.ok(RespuestaModalDTO.exito("Telegram se desvinculó de tu cuenta.", null));
    }

    @PostMapping("/mensajes")
    public ResponseEntity<?> enviarMensaje(@RequestBody SolicitudMensajeTelegramDTO mensaje, HttpServletRequest solicitud) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            telegram.enviarMensaje(idUsuario.get(), mensaje == null ? null : mensaje.texto(), mensaje == null ? null : mensaje.grupo());
            return ResponseEntity.ok(RespuestaModalDTO.exito("Mensaje enviado por Telegram.", null));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "texto"));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(error.getMessage().startsWith("La sesión") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("Inicia sesión para usar Telegram.", null));
    }
}
