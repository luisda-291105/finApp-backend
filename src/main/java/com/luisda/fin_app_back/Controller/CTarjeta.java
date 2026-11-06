package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.TarjetaDTO;
import com.luisda.fin_app_back.DTO.TarjetaSolicitudDTO;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Services.STarjeta;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/** Endpoints de tarjetas con autorización por usuario de sesión y respuestas sin CVV/PAN completo. */
@RestController
@RequestMapping("/tarjeta")
public class CTarjeta {
    private final STarjeta tarjetas;
    private final SUsuarioActivo usuarioActivo;

    public CTarjeta(STarjeta tarjetas, SUsuarioActivo usuarioActivo) {
        this.tarjetas = tarjetas;
        this.usuarioActivo = usuarioActivo;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(tarjetas.buscarPorUsuario(idUsuario.get()).stream().map(this::dto).toList());
    }

    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> listarPorUsuario(@PathVariable String idUsuario, HttpServletRequest solicitud) {
        Optional<String> idActivo = idUsuarioActivo(solicitud);
        if (idActivo.isEmpty()) return noAutorizado();
        if (!idActivo.get().equals(idUsuario)) return noEncontrado();
        return ResponseEntity.ok(tarjetas.buscarPorUsuario(idActivo.get()).stream().map(this::dto).toList());
    }

    @GetMapping("/id/{idTarjeta}")
    public ResponseEntity<?> detalle(@PathVariable String idTarjeta, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return tarjetas.buscarPorIdPropio(idTarjeta, idUsuario.get()).<ResponseEntity<?>>map(tarjeta -> ResponseEntity.ok(dto(tarjeta)))
                .orElseGet(this::noEncontrado);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody TarjetaSolicitudDTO solicitudTarjeta, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(dto(tarjetas.crear(idUsuario.get(), solicitudTarjeta)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "tarjeta"));
        }
    }

    @PutMapping("/{idTarjeta}")
    public ResponseEntity<?> actualizar(@PathVariable String idTarjeta, @RequestBody TarjetaSolicitudDTO solicitudTarjeta,
                                        HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(dto(tarjetas.actualizar(idTarjeta, idUsuario.get(), solicitudTarjeta)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "tarjeta"));
        } catch (IllegalStateException error) {
            return noEncontrado();
        }
    }

    @DeleteMapping("/{idTarjeta}")
    public ResponseEntity<?> eliminar(@PathVariable String idTarjeta, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        if (!tarjetas.eliminarPropia(idTarjeta, idUsuario.get())) return noEncontrado();
        return ResponseEntity.ok(RespuestaModalDTO.exito("Tarjeta eliminada.", null));
    }

    @GetMapping("/titular/{titular}")
    public ResponseEntity<?> buscarPorTitular(@PathVariable String titular, HttpServletRequest solicitud) {
        return buscarPropias(solicitud, tarjeta -> tarjeta.getTitular().equalsIgnoreCase(titular));
    }

    @GetMapping("/entidad/{entidad}")
    public ResponseEntity<?> buscarPorEntidad(@PathVariable String entidad, HttpServletRequest solicitud) {
        return buscarPropias(solicitud, tarjeta -> tarjeta.getEntidad().equalsIgnoreCase(entidad));
    }

    private ResponseEntity<?> buscarPropias(HttpServletRequest solicitud, java.util.function.Predicate<MTarjeta> filtro) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        List<TarjetaDTO> resultado = tarjetas.buscarPorUsuario(idUsuario.get()).stream().filter(filtro).map(this::dto).toList();
        return ResponseEntity.ok(resultado);
    }

    private Optional<String> idUsuarioActivo(HttpServletRequest solicitud) {
        return usuarioActivo.obtenerUsuario(solicitud).map(usuario -> usuario.getIdUsuario());
    }

    private TarjetaDTO dto(MTarjeta tarjeta) {
        return new TarjetaDTO(tarjeta.getIdTarjeta(), tarjeta.getTitular(), STarjeta.enmascararPan(tarjeta.getNumero()),
                tarjeta.getExpiracion(), tarjeta.getEntidad());
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RespuestaModalDTO.error("Inicia sesión para consultar tus tarjetas.", null));
    }

    private ResponseEntity<RespuestaModalDTO> noEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error("No se encontró la tarjeta.", null));
    }
}
