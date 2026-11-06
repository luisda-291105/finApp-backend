package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.ContactoDTO;
import com.luisda.fin_app_back.DTO.ContactoSolicitudDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Model.MContacto;
import com.luisda.fin_app_back.Services.SContacto;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/** Endpoints autenticados para administrar contactos propios. */
@RestController
@RequestMapping("/contacto")
public class CContacto {
    private final SContacto contactos;
    private final SUsuarioActivo usuarioActivo;

    public CContacto(SContacto contactos, SUsuarioActivo usuarioActivo) {
        this.contactos = contactos;
        this.usuarioActivo = usuarioActivo;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return ResponseEntity.ok(contactos.buscarPorUsuario(idUsuario.get()).stream().map(this::dto).toList());
    }

    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> listarPorUsuario(@PathVariable String idUsuario, HttpServletRequest solicitud) {
        Optional<String> idActivo = idUsuarioActivo(solicitud);
        if (idActivo.isEmpty()) return noAutorizado();
        if (!idActivo.get().equals(idUsuario)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(RespuestaModalDTO.error("No tienes permiso para consultar estos contactos.", null));
        return ResponseEntity.ok(contactos.buscarPorUsuario(idActivo.get()).stream().map(this::dto).toList());
    }

    @GetMapping("/id/{idContacto}")
    public ResponseEntity<?> detalle(@PathVariable String idContacto, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        return contactos.buscarPorIdPropio(idContacto, idUsuario.get()).<ResponseEntity<?>>map(contacto -> ResponseEntity.ok(dto(contacto)))
                .orElseGet(() -> noEncontrado());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ContactoSolicitudDTO solicitudContacto, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            MContacto creado = contactos.crear(idUsuario.get(), solicitudContacto);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto(creado));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "contacto"));
        }
    }

    @PutMapping("/{idContacto}")
    public ResponseEntity<?> actualizar(@PathVariable String idContacto, @RequestBody ContactoSolicitudDTO solicitudContacto,
                                        HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        try {
            return ResponseEntity.ok(dto(contactos.actualizar(idContacto, idUsuario.get(), solicitudContacto)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), "contacto"));
        } catch (IllegalStateException error) {
            return noEncontrado();
        }
    }

    @DeleteMapping("/{idContacto}")
    public ResponseEntity<?> eliminar(@PathVariable String idContacto, HttpServletRequest solicitud) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        if (!contactos.eliminarPropio(idContacto, idUsuario.get())) return noEncontrado();
        return ResponseEntity.ok(RespuestaModalDTO.exito("Contacto eliminado.", null));
    }

    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<?> buscarPorNombre(@PathVariable String nombre, HttpServletRequest solicitud) {
        return buscarEnContactosPropios(solicitud, contacto -> contacto.getNombre().equalsIgnoreCase(nombre));
    }

    @GetMapping("/nota/{nota}")
    public ResponseEntity<?> buscarPorNota(@PathVariable String nota, HttpServletRequest solicitud) {
        return buscarEnContactosPropios(solicitud, contacto -> contacto.getNotas() != null && contacto.getNotas().equalsIgnoreCase(nota));
    }

    @GetMapping("/telefono/{telefono}")
    public ResponseEntity<?> buscarPorTelefono(@PathVariable String telefono, HttpServletRequest solicitud) {
        return buscarEnContactosPropios(solicitud, contacto -> contacto.getTelefono().equals(telefono));
    }

    private ResponseEntity<?> buscarEnContactosPropios(HttpServletRequest solicitud, java.util.function.Predicate<MContacto> filtro) {
        Optional<String> idUsuario = idUsuarioActivo(solicitud);
        if (idUsuario.isEmpty()) return noAutorizado();
        List<ContactoDTO> encontrados = contactos.buscarPorUsuario(idUsuario.get()).stream().filter(filtro).map(this::dto).toList();
        return ResponseEntity.ok(encontrados);
    }

    private Optional<String> idUsuarioActivo(HttpServletRequest solicitud) {
        return usuarioActivo.obtenerUsuario(solicitud).map(usuario -> usuario.getIdUsuario());
    }

    private ContactoDTO dto(MContacto contacto) {
        return new ContactoDTO(contacto.getIdContacto(), contacto.getNombre(), contacto.getTelefono(), contacto.getNotas());
    }

    private ResponseEntity<RespuestaModalDTO> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RespuestaModalDTO.error("Inicia sesión para consultar tus contactos.", null));
    }

    private ResponseEntity<RespuestaModalDTO> noEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespuestaModalDTO.error("No se encontró el contacto.", null));
    }
}
