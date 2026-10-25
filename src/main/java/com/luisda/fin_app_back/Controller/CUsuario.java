package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Services.SUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
@CrossOrigin(origins = "*")
public class CUsuario {
    @Autowired
    SUsuario SUsuario;

    /** crear un registro usuario */
    @PostMapping
    public ResponseEntity<?> adicionarUsuario(@RequestBody MUsuario MUsuario)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SUsuario.crear(MUsuario));
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

    /** consultar por contacto */
    @GetMapping("/contacto/{contacto}")
    public ResponseEntity<?> consultarPorContacto(@PathVariable String contacto) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.buscarPorContacto(contacto));
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
    public ResponseEntity<?> modificar(@PathVariable  String idUsuario , @RequestBody  MUsuario MUsuario) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SUsuario.modificar(MUsuario , idUsuario));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

}
