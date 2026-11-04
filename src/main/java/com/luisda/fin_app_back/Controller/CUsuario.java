package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Services.SUsuario;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import com.luisda.fin_app_back.Services.SVerificadorContrasena;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.UsuarioLoginDTO;
import com.luisda.fin_app_back.DTO.UsuarioActivoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * @uso               dar puntos de coneccion con la api
 * @capa              controlador
 * @responsabilidades endpoind para la web
 * @datos             usa SUsuario + MUsuario
 * @dependencias      SUsuario, Spring Data JPA
 * @usadoPor          web
 */

@RestController
@RequestMapping("/usuario")
public class CUsuario {
    @Autowired
    SUsuario SUsuario;

    private final SUsuarioActivo usuarioActivo;
    private final SVerificadorContrasena verificadorContrasena;

    public CUsuario(SUsuarioActivo usuarioActivo, SVerificadorContrasena verificadorContrasena) {
        this.usuarioActivo = usuarioActivo;
        this.verificadorContrasena = verificadorContrasena;
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody UsuarioLoginDTO credenciales, HttpServletRequest solicitud) {
        if (credenciales == null || credenciales.idUsuario() == null || credenciales.contrasena() == null) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error("Ingresa tu identificación o correo y contraseña.", "credenciales"));
        }
        try {
            MUsuario usuario = this.SUsuario.autenticar(credenciales.idUsuario(), credenciales.contrasena(), verificadorContrasena);
            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("La identificación, correo o contraseña no son correctos.", "credenciales"));
            }
            HttpSession sesionAnterior = solicitud.getSession(false);
            if (sesionAnterior != null) sesionAnterior.invalidate();
            HttpSession sesion = solicitud.getSession(true);
            sesion.setAttribute(SUsuarioActivo.CLAVE_SESION, usuario.getIdUsuario());
            return ResponseEntity.ok(RespuestaModalDTO.exito("Sesión iniciada.", new UsuarioActivoDTO(usuario.getIdUsuario(), usuario.getNombre(), usuario.getFechaCreacion())));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo iniciar sesión.", null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> cerrarSesion(HttpServletRequest solicitud) {
        HttpSession sesion = solicitud.getSession(false);
        if (sesion != null) sesion.invalidate();
        return ResponseEntity.ok(RespuestaModalDTO.exito("Sesión cerrada.", null));
    }

    @GetMapping("/activo")
    public ResponseEntity<?> usuarioActivo(HttpServletRequest solicitud) {
        return usuarioActivo.obtenerUsuario(solicitud)
                .<ResponseEntity<?>>map(usuario -> ResponseEntity.ok(RespuestaModalDTO.exito(
                        "Usuario activo.", new UsuarioActivoDTO(usuario.getIdUsuario(), usuario.getNombre(), usuario.getFechaCreacion()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(RespuestaModalDTO.error("No hay una sesión activa.", null)));
    }

    /** crear un registro  */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MUsuario MUsuario)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SUsuario.crear(MUsuario));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error(error.getMessage(), error.getMessage().toLowerCase().contains("correo") ? "correo" : "idUsuario"));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(RespuestaModalDTO.error(error.getMessage(), error.getMessage().toLowerCase().contains("correo") ? "correo" : "idUsuario"));
        } catch (DataIntegrityViolationException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RespuestaModalDTO.error("Ya existe una cuenta con esta identificación o correo.", "credenciales"));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consultar general */
    @GetMapping
    public ResponseEntity<?> consultaGeneral() throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarTodos());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consulta individual por id */
    @GetMapping("/id/{idUsuario}")
    public ResponseEntity<?> consultaPorId(@PathVariable String idUsuario) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarPorId(idUsuario));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consultar por nombre */
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<?> buscarPorNombre(@PathVariable String nombre) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarPorNombre(nombre));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** eliminar por id */
    @DeleteMapping("/{idUsuario}")
    public ResponseEntity<?> eliminar(@PathVariable String idUsuario) throws Exception{
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
            HttpSession sesion = solicitud.getSession(false);
            if (sesion != null && idUsuario.equals(sesion.getAttribute(SUsuarioActivo.CLAVE_SESION))) {
                sesion.setAttribute(SUsuarioActivo.CLAVE_SESION, actualizado.getIdUsuario());
            }
            return ResponseEntity.ok(RespuestaModalDTO.exito("Perfil actualizado.",
                    new UsuarioActivoDTO(actualizado.getIdUsuario(), actualizado.getNombre(), actualizado.getFechaCreacion())));
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
