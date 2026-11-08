package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.PeticionChat;
import com.luisda.fin_app_back.DTO.ConfirmacionAccionAsistente;
import com.luisda.fin_app_back.DTO.RespuestaChat;
import com.luisda.fin_app_back.Services.SAsistenteChat;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import com.luisda.fin_app_back.Services.ServicioChatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Separa el asistente público explicativo del asistente personal autenticado. */
@RestController
@Tag(name = "Asistente", description = "Consultas informativas de la aplicación y del usuario autenticado.")
@RequestMapping("/asistente")
public class CAsistente {
    private static final int MAX_SOLICITUDES_POR_MINUTO = 30;
    private static final ConcurrentHashMap<String, VentanaLimite> VENTANAS = new ConcurrentHashMap<>();
    private final SAsistenteChat servicio;
    private final SUsuarioActivo usuarioActivo;

    public CAsistente(SAsistenteChat servicio, SUsuarioActivo usuarioActivo) {
        this.servicio = servicio;
        this.usuarioActivo = usuarioActivo;
    }

    @PostMapping("/publico")
    public ResponseEntity<?> conversarPublico(@RequestBody PeticionChat peticion, HttpServletRequest request) {
        if (superoLimite("publico:" + request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("mensaje", "Alcanzaste el límite temporal del asistente. Inténtalo más tarde."));
        }
        try {
            return ResponseEntity.ok(servicio.responderPublico(peticion));
        } catch (ServicioChatException error) {
            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("mensaje", error.getMessage() != null ? error.getMessage() : "Error en el asistente");
            return ResponseEntity.status(error.getEstado()).body(cuerpo);
        } catch (Exception error) {
            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("mensaje", "No se pudo generar una respuesta. Inténtalo de nuevo.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cuerpo);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> conversar(@RequestBody PeticionChat peticion, HttpServletRequest request) {
        Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(request);
        if (idUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("mensaje", "Inicia sesión para usar tu asistente personal."));
        }
        if (superoLimite("usuario:" + idUsuario.get())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("mensaje", "Alcanzaste el límite temporal del asistente. Inténtalo más tarde."));
        }
        try {
            RespuestaChat respuesta = servicio.responderPublico(peticion);
            return ResponseEntity.ok(respuesta);
        } catch (ServicioChatException error) {
            System.err.println("[Asistente] ServicioChatException: " + error.getMessage());
            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("mensaje", error.getMessage() != null ? error.getMessage() : "Error en el asistente");
            return ResponseEntity.status(error.getEstado()).body(cuerpo);
        } catch (Exception error) {
            System.err.println("[Asistente] Error inesperado en /asistente/chat:");
            error.printStackTrace();
            Map<String, Object> cuerpo = new java.util.HashMap<>();
            cuerpo.put("mensaje", "Error interno en el asistente: " + error.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cuerpo);
        }
    }

    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@RequestBody ConfirmacionAccionAsistente confirmacion,
                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("mensaje", "El asistente solo responde preguntas informativas y no realiza operaciones."));
    }

    private boolean superoLimite(String direccion) {
        long ahora = Instant.now().toEpochMilli();
        VentanaLimite ventana = VENTANAS.compute(direccion, (clave, actual) -> {
            if (actual == null || ahora - actual.inicio() >= 60_000) {
                return new VentanaLimite(ahora, new AtomicInteger(1));
            }
            actual.solicitudes().incrementAndGet();
            return actual;
        });
        if (VENTANAS.size() > 2_000) {
            VENTANAS.entrySet().removeIf(entrada -> ahora - entrada.getValue().inicio() >= 60_000);
        }
        return ventana.solicitudes().get() > MAX_SOLICITUDES_POR_MINUTO;
    }

    private record VentanaLimite(long inicio, AtomicInteger solicitudes) {}
}
