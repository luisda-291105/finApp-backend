package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/movimiento")
public class CMovimientoContable {
    private final SMovimientoContable movimientos;
    private final SUsuarioActivo usuarioActivo;

    public CMovimientoContable(SMovimientoContable movimientos, SUsuarioActivo usuarioActivo) {
        this.movimientos = movimientos;
        this.usuarioActivo = usuarioActivo;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String idBolsillo, @RequestParam(defaultValue = "TODOS") String tipo,
                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(movimientos.listar(usuario.get().getIdUsuario(), idBolsillo, tipo));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "tipo"));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    @GetMapping("/resumen")
    public ResponseEntity<?> resumir(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(movimientos.resumir(usuario.get().getIdUsuario()));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody MovimientoContableSolicitudDTO solicitud, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(movimientos.crear(usuario.get().getIdUsuario(), solicitud));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("Inicia sesión para consultar tus movimientos.", null));
    }
}
