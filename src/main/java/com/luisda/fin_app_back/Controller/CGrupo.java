package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.Model.MGrupo;

import com.luisda.fin_app_back.Services.SGrupo;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * @uso               dar puntos de coneccion con la api
 * @capa              controlador
 * @responsabilidades endpoind para la web
 * @datos             usa SContacto + MContacto
 * @dependencias      SContacto, Spring Data JPA
 * @usadoPor          web
 */

@RestController
@Tag(name = "Grupos", description = "Creación y administración de grupos.")
@RequestMapping("/grupo")
public class CGrupo {

    private final SGrupo SGrupo;
    private final SUsuarioActivo usuarioActivo;

    public CGrupo(SGrupo SGrupo, SUsuarioActivo usuarioActivo) {
        this.SGrupo = SGrupo;
        this.usuarioActivo = usuarioActivo;
    }

    /** crear un registro  */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MGrupo MGrupo, HttpServletRequest solicitud)throws Exception{
        try {
            java.util.Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idUsuario.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SGrupo.crearPropio(idUsuario.get(), MGrupo.getNombre(), MGrupo.getDescripcion()));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consultar general (solo del usuario activo) */
    @GetMapping
    public ResponseEntity<?> consultaGeneral(HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idUsuario = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idUsuario.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.buscarPorUsuario(idUsuario.get()));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Consultar grupos del usuario. */
    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> buscarPorUsuario(@PathVariable String idUsuario, HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            if (!idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseEntity<String>("No tienes acceso a este usuario.", HttpStatus.FORBIDDEN));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.buscarPorUsuario(idUsuario));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /**
     * Consultar grupo por nombre.
     */
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<?> buscarPorNombre(@PathVariable String nombre, HttpServletRequest solicitud) throws Exception{
        if (usuarioActivo.obtenerIdUsuario(solicitud).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
        }
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.buscarPorNombre(nombre));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /**
     * Consultar grupo por id.
     */
    @GetMapping("/id/{idGrupo}")
    public ResponseEntity<?> buscarPorId(@PathVariable String idGrupo, HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            Optional<com.luisda.fin_app_back.Model.MGrupo> grupo = this.SGrupo.buscarPorId(idGrupo);
            if (grupo.isEmpty() || grupo.get().getUsuario() == null
                    || !idGrupo.equals(grupo.get().getUsuario().getIdUsuario())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseEntity<String>("No se encontró el grupo.", HttpStatus.NOT_FOUND));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(grupo.get());
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /**
     * eliminar grupo por id.
     */
    @DeleteMapping("/{idGrupo}")
    public ResponseEntity<?> eliminar(@PathVariable String idGrupo, @RequestParam String idUsuario, HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            if (idUsuario == null || !idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseEntity<String>("No tienes acceso a este grupo.", HttpStatus.FORBIDDEN));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.eliminar(idGrupo, idUsuario));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /**
     * modificar grupo por id.
     */
    @PutMapping("/{idGrupo}")
    public ResponseEntity<?> modificar(@RequestBody MGrupo MGrupo ,@PathVariable String idGrupo, @RequestParam String idUsuario , HttpServletRequest solicitud) throws Exception{
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            if (idUsuario == null || !idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseEntity<String>("No tienes acceso a este grupo.", HttpStatus.FORBIDDEN));
            }
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.modificar(MGrupo , idGrupo, idUsuario));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Vincula una fuente del usuario al grupo. */
    @PutMapping("/{idGrupo}/fuentes/{idFuente}")
    public ResponseEntity<?> vincularFuente(
            @PathVariable String idGrupo,
            @PathVariable String idFuente,
            @RequestParam String idUsuario,
            HttpServletRequest solicitud
    ) {
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            if (idUsuario == null || !idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseEntity<String>("No tienes acceso a este grupo.", HttpStatus.FORBIDDEN));
            }
            return ResponseEntity.ok(this.SGrupo.vincularFuente(idGrupo, idFuente, idUsuario));
        } catch (Exception error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    /** Desvincula del grupo una fuente, sin eliminar el registro del usuario. */
    @DeleteMapping("/{idGrupo}/fuentes/{idFuente}")
    public ResponseEntity<?> desvincularFuente(
            @PathVariable String idGrupo,
            @PathVariable String idFuente,
            @RequestParam String idUsuario,
            HttpServletRequest solicitud
    ) {
        try {
            java.util.Optional<String> idSesion = usuarioActivo.obtenerIdUsuario(solicitud);
            if (idSesion.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ResponseEntity<String>("No hay una sesión activa.", HttpStatus.UNAUTHORIZED));
            }
            if (idUsuario == null || !idUsuario.equals(idSesion.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseEntity<String>("No tienes acceso a este grupo.", HttpStatus.FORBIDDEN));
            }
            return ResponseEntity.ok(this.SGrupo.desvincularFuente(idGrupo, idFuente, idUsuario));
        } catch (Exception error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }


}
