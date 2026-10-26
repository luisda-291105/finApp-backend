package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MGrupo;

import com.luisda.fin_app_back.Services.SGrupo;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/grupo")
@CrossOrigin(origins = "*")
public class CGrupo {
    @Autowired
    SGrupo SGrupo;

    /** crear un registro  */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MGrupo MGrupo)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SGrupo.crear(MGrupo));
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
                    .body(this.SGrupo.buscarTodos());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /**
     * Consultar grupo por usuario.

    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> buscarPorUsuario(@PathVariable String idUsuario) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.buscarPorUsuario(idUsuario));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    } */

    /**
     * Consultar grupo por nombre.
     */
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<?> buscarPorNombre(@PathVariable String nombre) throws Exception{
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
    public ResponseEntity<?> buscarPorId(@PathVariable String idGrupo) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.buscarPorId(idGrupo));
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
    public ResponseEntity<?> eliminar(@PathVariable String idGrupo) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.eliminar(idGrupo));
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
    public ResponseEntity<?> modificar(@RequestBody MGrupo MGrupo ,@PathVariable String idGrupo ) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SGrupo.modificar(MGrupo , idGrupo));
        }catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }




}
