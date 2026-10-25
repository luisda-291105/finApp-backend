package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MContacto;

import com.luisda.fin_app_back.Services.SContacto;
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
@RequestMapping("/contacto")
@CrossOrigin(origins = "*")
public class CContacto {
    @Autowired
    SContacto SContacto;
    /** crear un registro usuario */
    @PostMapping
    public ResponseEntity<?> adicionarUsuario(@RequestBody MContacto MContacto)throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(this.SContacto.crear(MContacto));
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
                    .body(this.SContacto.buscarTodos());
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consulta individual por id */
    @GetMapping("/id/{idContacto}")
    public ResponseEntity<?> consultaPorId(@PathVariable String idContacto) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SContacto.buscarPorId(idContacto));
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
                    .body(this.SContacto.buscarPorNombre(nombre));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** consultar por nombre */
    @GetMapping("/nota/{nota}")
    public ResponseEntity<?> buscarPorNota(@PathVariable String nota) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SContacto.buscarPorNota(nota));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }


    /** consultar por contacto */
    @GetMapping("/contacto/{telefono}")
    public ResponseEntity<?> buscarPorTelefono(@PathVariable String telefono) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SContacto.buscarPorTelefono(telefono));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** eliminar por id */
    @DeleteMapping("/{idContacto}")
    public ResponseEntity<?> eliminar(@PathVariable String idContacto) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SContacto.eliminar(idContacto));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }

    /** modificar usuario por id */

    @PutMapping("/{idContacto}")
    public ResponseEntity<?> modificar(@PathVariable  String idContacto , @RequestBody MContacto MContacto) throws Exception{
        try {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(this.SContacto.modificar(MContacto , idContacto));
        } catch (Exception error){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error.getMessage());
        }
    }



}
