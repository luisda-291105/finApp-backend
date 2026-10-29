package com.luisda.fin_app_back.Controller;


import com.luisda.fin_app_back.Model.MTarjeta;

import com.luisda.fin_app_back.Services.STarjeta;
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
@RequestMapping("/tarjeta")
@CrossOrigin(origins = "*")
public class CTarjeta {

    @Autowired
    STarjeta STarjeta;

    /**
     * crear un registro
     */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MTarjeta MTarjeta) throws Exception {
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.STarjeta.crear(MTarjeta));
        } catch (Exception error) {
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
                    .body(this.STarjeta.buscarTodos());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> buscarPorUsuario(@PathVariable String idUsuario) throws Exception {
        try {
            return ResponseEntity.ok(this.STarjeta.buscarPorUsuario(idUsuario));
        } catch (Exception error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    /** consulta individual por titular */
    @GetMapping("/titular/{titular}")
    public ResponseEntity<?> consultaPorId(@PathVariable String titular) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.STarjeta.buscarPorTitular(titular));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consulta individual por entidad */
    @GetMapping("/entidad/{entidad}")
    public ResponseEntity<?> buscarPorEntidad(@PathVariable String entidad) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.STarjeta.buscarPorEntidad(entidad));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }


    /** consulta individual por id */
    @GetMapping("/id/{idTarjeta}")
    public ResponseEntity<?> buscarPorId(@PathVariable String idTarjeta) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.STarjeta.buscarPorId(idTarjeta));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** eliminar  por id */
    @DeleteMapping("/{idTarjeta}")
    public ResponseEntity<?> eliminar(@PathVariable String idTarjeta) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.STarjeta.eliminar(idTarjeta));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** modificar tarjeta por id */

    @PutMapping("/{idTarjeta}")
    public ResponseEntity<?> modificar(@PathVariable  String idTarjeta , @RequestBody MTarjeta MTarjeta) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.STarjeta.modificar(MTarjeta , idTarjeta));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

}
