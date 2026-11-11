package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.SBolsillo;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/** Endpoints de bolsillos asociados al usuario autenticado. */
@RestController
@Tag(name = "Bolsillos", description = "Operaciones de consulta y administración de bolsillos.")
@RequestMapping("/bolsillo")
public class CBolsillo {
    private final SBolsillo bolsillos;
    private final SUsuarioActivo usuarioActivo;

    public CBolsillo(SBolsillo bolsillos, SUsuarioActivo usuarioActivo) {
        this.bolsillos = bolsillos;
        this.usuarioActivo = usuarioActivo;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(bolsillos.listar(usuario.get().getIdUsuario()));
    }

    /** El bolsillo interno de revisión se consulta por una ruta separada del listado del usuario. */
    @GetMapping("/por-revisar")
    public ResponseEntity<?> obtenerPorRevisar(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        return bolsillos.obtenerPorRevisar(usuario.get().getIdUsuario())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RespuestaModalDTO.error("Todavía no existe un bolsillo Por revisar.", null)));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody BolsilloSolicitudDTO solicitud, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(bolsillos.crear(usuario.get().getIdUsuario(), solicitud));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "bolsillo"));
        }
    }

    @PutMapping("/{idBolsillo}")
    public ResponseEntity<?> actualizar(@PathVariable String idBolsillo, @RequestBody BolsilloSolicitudDTO solicitud,
                                        HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(bolsillos.actualizar(usuario.get().getIdUsuario(), idBolsillo, solicitud));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "bolsillo"));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    @DeleteMapping("/{idBolsillo}")
    public ResponseEntity<?> eliminar(@PathVariable String idBolsillo, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request);
        if (usuario.isEmpty()) return noAutorizado();
        try {
            bolsillos.eliminar(usuario.get().getIdUsuario(), idBolsillo);
            return ResponseEntity.ok(RespuestaModalDTO.exito("Bolsillo eliminado.", null));
        } catch (NoSuchElementException error) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(RespuestaModalDTO.error(error.getMessage(), null));
        }
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RespuestaModalDTO.error("Inicia sesión para consultar tus bolsillos.", null));
    }
}
