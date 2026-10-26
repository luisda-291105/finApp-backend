package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Services.SFuente;

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
@RequestMapping("/fuente")
@CrossOrigin(origins = "*")
public class CFuente {

    @Autowired
    SFuente SFuente;

    /** crear un registro  */
    @PostMapping
    public ResponseEntity<?> adicionar(@RequestBody MFuente MFuente)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SFuente.crear(MFuente));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Consulta todas las fuentes. */
    @GetMapping
    public ResponseEntity<?> buscarTodos()throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.buscarTodos());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Consulta por usuario. */
    @GetMapping("/usuario/{usuario}")
    public ResponseEntity<?> buscarPorUsuario(@PathVariable String usuario)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.buscarPorUsuario(usuario));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Consulta por tipo. */
    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<?> buscarPorTipo(@PathVariable String tipo)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.buscarPorTipo(tipo));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** Consulta por id. */
    @GetMapping("/id/{idFuente}")
    public ResponseEntity<?> buscarPorId(@PathVariable String idFuente)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.buscarPorId(idFuente));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** eliminar por id. */
    @DeleteMapping("/{idFuente}")
    public ResponseEntity<?> eliminar(@PathVariable String idFuente)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.eliminar(idFuente));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** modificar por id. */
    @PutMapping("/{idFuente}")
    public ResponseEntity<?> modificar(@RequestBody MFuente MFuente,@PathVariable String idFuente)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SFuente.modificar(MFuente , idFuente));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }


}
