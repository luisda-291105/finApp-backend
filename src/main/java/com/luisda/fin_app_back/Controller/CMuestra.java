package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.DTO.*;
import com.luisda.fin_app_back.Services.SMuestra;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Muestras", description = "Endpoints de muestra y datos demostrativos.")
@RequestMapping("/api/muestras")
public class CMuestra {
    private final SMuestra muestras; private final SUsuarioActivo usuarioActivo;
    public CMuestra(SMuestra muestras, SUsuarioActivo usuarioActivo) { this.muestras = muestras; this.usuarioActivo = usuarioActivo; }
    @PostMapping public ResponseEntity<?> crear(@RequestBody MuestraCrearDTO dto, HttpServletRequest request) {
        try { var user = usuarioActivo.obtenerUsuario(request).orElseThrow(); return ResponseEntity.status(201).body(RespuestaModalDTO.exito("Muestra guardada.", muestras.crear(user.getIdUsuario(), dto))); }
        catch (java.util.NoSuchElementException e) { return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null)); }
        catch (IllegalStateException e) { return ResponseEntity.status(409).body(RespuestaModalDTO.error(e.getMessage(), null)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(RespuestaModalDTO.error(e.getMessage(), null)); }
        catch (DataIntegrityViolationException e) { return ResponseEntity.status(409).body(RespuestaModalDTO.error("Ya existe una muestra con ese tipo y título.", "titulo")); }
    }
    @GetMapping public ResponseEntity<?> listar(HttpServletRequest request) {
        var user = usuarioActivo.obtenerUsuario(request); if (user.isEmpty()) return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        return ResponseEntity.ok(RespuestaModalDTO.exito("Muestras cargadas.", muestras.listar(user.get().getIdUsuario())));
    }
    @GetMapping("/{id}") public ResponseEntity<?> buscar(@PathVariable Long id, HttpServletRequest request) {
        var user = usuarioActivo.obtenerUsuario(request); if (user.isEmpty()) return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        try { return ResponseEntity.ok(RespuestaModalDTO.exito("Muestra cargada.", muestras.buscar(user.get().getIdUsuario(), id))); }
        catch (java.util.NoSuchElementException e) { return ResponseEntity.status(404).body(RespuestaModalDTO.error(e.getMessage(), null)); }
    }
    @PutMapping("/orden") public ResponseEntity<?> reordenar(@RequestBody MuestraOrdenDTO dto, HttpServletRequest request) {
        var user = usuarioActivo.obtenerUsuario(request); if (user.isEmpty()) return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        try { muestras.reordenar(user.get().getIdUsuario(), dto == null ? null : dto.ids()); return ResponseEntity.ok(RespuestaModalDTO.exito("Orden de muestras actualizado.", null)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(RespuestaModalDTO.error(e.getMessage(), null)); }
    }
    @PutMapping("/{id}") public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody MuestraCrearDTO dto, HttpServletRequest request) {
        var user = usuarioActivo.obtenerUsuario(request); if (user.isEmpty()) return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        try { return ResponseEntity.ok(RespuestaModalDTO.exito("Muestra actualizada.", muestras.actualizar(user.get().getIdUsuario(), id, dto))); }
        catch (java.util.NoSuchElementException e) { return ResponseEntity.status(404).body(RespuestaModalDTO.error(e.getMessage(), null)); }
        catch (IllegalStateException e) { return ResponseEntity.status(409).body(RespuestaModalDTO.error(e.getMessage(), "titulo")); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(RespuestaModalDTO.error(e.getMessage(), null)); }
        catch (DataIntegrityViolationException e) { return ResponseEntity.status(409).body(RespuestaModalDTO.error("Ya existe una muestra con ese tipo y título.", "titulo")); }
    }
    @DeleteMapping("/{id}") public ResponseEntity<?> eliminar(@PathVariable Long id, HttpServletRequest request) {
        var user = usuarioActivo.obtenerUsuario(request); if (user.isEmpty()) return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        try { muestras.eliminar(user.get().getIdUsuario(), id); return ResponseEntity.ok(RespuestaModalDTO.exito("Muestra eliminada.", null)); }
        catch (java.util.NoSuchElementException e) { return ResponseEntity.status(404).body(RespuestaModalDTO.error(e.getMessage(), null)); }
    }
}
