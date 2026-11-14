package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Services.SUsuario;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import com.luisda.fin_app_back.Services.SVerificadorContrasena;
import com.luisda.fin_app_back.Services.JwtTokenService;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.UsuarioLoginDTO;
import com.luisda.fin_app_back.DTO.UsuarioDTO;
import com.luisda.fin_app_back.DTO.UsuarioActivoDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * @uso               dar puntos de coneccion con la api
 * @capa              controlador
 * @responsabilidades endpoind para la web
 * @datos             usa SUsuario + MUsuario
 * @dependencias      SUsuario, Spring Data JPA
 * @usadoPor          web
 */

@RestController
@Tag(name = "Usuarios y sesión", description = "Registro, inicio y cierre de sesión JWT y administración del perfil.")
@RequestMapping("/usuario")
public class CUsuario {
    final SUsuario SUsuario;

    private final SUsuarioActivo usuarioActivo;
    private final SVerificadorContrasena verificadorContrasena;
    private final JwtTokenService tokens;

    public CUsuario(SUsuarioActivo usuarioActivo, SVerificadorContrasena verificadorContrasena,
                    SUsuario SUsuario, JwtTokenService tokens) {
        this.usuarioActivo = usuarioActivo;
        this.verificadorContrasena = verificadorContrasena;
        this.SUsuario = SUsuario;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody UsuarioLoginDTO credenciales, HttpServletRequest solicitud) {
        if (credenciales == null || credenciales.identificador() == null || credenciales.contrasena() == null) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error("Ingresa tu nombre, identificación, correo o teléfono y contraseña.", "credenciales"));
        }
        try {
            MUsuario usuario = this.SUsuario.autenticar(credenciales.identificador(), credenciales.contrasena(), verificadorContrasena);
            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("El usuario o la contraseña no son correctos.", "credenciales"));
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.emitir(usuario.getIdUsuario())).toString())
                    .body(RespuestaModalDTO.exito("Sesión iniciada.", dtoActivo(usuario)));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo iniciar sesión.", null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> cerrarSesion(HttpServletRequest solicitud) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.cookieExpirado().toString())
                .body(RespuestaModalDTO.exito("Sesión cerrada.", null));
    }

    @GetMapping("/activo")
    public ResponseEntity<?> usuarioActivo(HttpServletRequest solicitud) {
        return usuarioActivo.obtenerUsuario(solicitud)
                .<ResponseEntity<?>>map(usuario -> ResponseEntity.ok(RespuestaModalDTO.exito(
                        "Usuario activo.", dtoActivo(usuario))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("No hay una sesión activa.", null)));
    }

    private UsuarioActivoDTO dtoActivo(MUsuario usuario) {
        String correo = usuario.getCorreo();
        return new UsuarioActivoDTO(usuario.getIdUsuario(), usuario.getNombre(), usuario.getFechaCreacion(), correo, usuario.getTelefono());
    }

    /** crear un registro  */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MUsuario MUsuario)throws Exception{
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(UsuarioDTO.desde(this.SUsuario.crear(MUsuario)));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), campoCredencial(error.getMessage())));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(RespuestaModalDTO.error(error.getMessage(), campoCredencial(error.getMessage())));
        } catch (DataIntegrityViolationException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error("Ya existe una cuenta con esta identificación o correo.", "credenciales"));
        } catch (Exception error){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo crear la cuenta.", null));
        }
    }

    private String campoCredencial(String mensaje) {
        String normalizado = mensaje == null ? "" : mensaje.toLowerCase();
        if (normalizado.contains("correo")) return "correo";
        if (normalizado.contains("teléfono") || normalizado.contains("telefono")) return "telefono";
        return "idUsuario";
    }

    /** consultar general (solo autenticado) */
    @GetMapping
    public ResponseEntity<?> consultaGeneral(HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idUsuario.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarTodos().stream().map(UsuarioDTO::desde).toList());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudieron consultar los usuarios.", null));
        }
    }

    /** consulta individual por id (solo para el propio usuario autenticado) */
    @GetMapping("/id/{idUsuario}")
    public ResponseEntity<?> consultaPorId(@PathVariable String idUsuario, HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
            }
            if (!idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RespuestaModalDTO.error("No tienes acceso a este usuario.", null));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarPorId(idUsuario).map(UsuarioDTO::desde));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo consultar el usuario.", null));
        }
    }

    /** consultar por nombre (solo para el propio usuario autenticado) */
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<?> buscarPorNombre(@PathVariable String nombre, HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
            }
            List<MUsuario> usuarios = this.SUsuario.buscarPorNombre(nombre);
            boolean accesoConcedido = usuarios.stream().anyMatch(usuario -> usuario.getIdUsuario().equals(idSesion.get()));
            if (!accesoConcedido) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RespuestaModalDTO.error("No tienes acceso a este usuario.", null));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(usuarios.stream().map(UsuarioDTO::desde).toList());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudieron consultar los usuarios.", null));
        }
    }

    /** eliminar por id (solo el propio usuario autenticado) */
    @DeleteMapping("/{idUsuario}")
    public ResponseEntity<?> eliminar(@PathVariable String idUsuario, HttpServletRequest solicitud) throws Exception{
        java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
        if (idSesion.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(RespuestaModalDTO.error("No hay una sesión activa.", null));
        }
        if (!idUsuario.equals(idSesion.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(RespuestaModalDTO.error("No puedes eliminar esta cuenta.", null));
        }
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.eliminar(idUsuario));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** modificar usuario por id */
    @PutMapping("/{idUsuario}")
    public ResponseEntity<?> modificar(@PathVariable String idUsuario, @RequestBody MUsuario cambios,
                                       HttpServletRequest solicitud) throws Exception{
        try {
            MUsuario actualizado = this.SUsuario.modificar(cambios, idUsuario);
            ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
            if (idUsuario.equals(usuarioActivo.obtenerIdUsuario(solicitud).orElse(null))) {
                respuesta.header(HttpHeaders.SET_COOKIE,
                        tokens.cookie(tokens.emitir(actualizado.getIdUsuario())).toString());
            }
            return respuesta.body(RespuestaModalDTO.exito("Perfil actualizado.", UsuarioDTO.desde(actualizado)));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), "idUsuario"));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(RespuestaModalDTO.error(error.getMessage(), "perfil"));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo guardar el perfil.", null));
        }
    }

}
