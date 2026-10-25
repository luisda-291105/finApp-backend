package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MContacto;

import com.luisda.fin_app_back.Repository.IContacto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * @uso               Gestiona las operaciones disponibles de contactos
 * @capa              Servicio
 * @responsabilidades CRUD y consultas por usuario, nombre, teléfono y correo
 * @datos             Recibe y devuelve entidades MContacto
 * @dependencias      ContactoRepository, SValidadorOperaciones
 * @usadoPor          Capas consumidoras de servicios
 */

@Service
public class SContacto  {

    private  final  IContacto IContacto;

    public SContacto(IContacto iContacto) {
        IContacto = iContacto;
    }

    /** Agrega un contacto. */
    public  MContacto crear (MContacto MContacto) throws Exception{
        try {
            return this.IContacto.save(MContacto);
        }catch (Exception error){
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta todos los contactos. */
    public List<MContacto> buscarTodos()throws Exception{
        try {
            return this.IContacto.findAll();
        } catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Consulta un contacto por identificador. */
    public Optional<MContacto> buscarPorId(String idContacto)throws Exception{
        try {
            return  this.IContacto.findById(idContacto);
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Consulta un contacto por telefono*/
    public List<MContacto> buscarPorTelefono(String telefono) throws Exception{
        try {
            return this.IContacto.buscarPorTelefono(telefono);
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Consulta un contacto por nota*/
    public List<MContacto> buscarPorNota(String nota) throws Exception{
        try {
            return this.IContacto.buscarPorNota(nota);
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Consulta un contacto por nombre */
    public List<MContacto> buscarPorNombre(String nombreContacto)throws Exception{
        try {
            return  this.IContacto.buscarPorNombre(nombreContacto);
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Elimina el usuario identificado */
    public Boolean eliminar(String idContacto) throws  Exception{
        try {
            Optional<MContacto> contactoEncontrado = this.IContacto.findById(idContacto);
            if (contactoEncontrado.isPresent()){
                this.IContacto.deleteById(idContacto);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque contacto no registrado");
            }
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }

    /** Actualiza un usuario existente y comprueba que exista */
     public MContacto modificar(MContacto MContacto ,String idContacto) throws Exception{
         try {
             Optional<MContacto> contactoEncontrado = this.IContacto.findById(idContacto);
             if (contactoEncontrado.isPresent()){
                 MContacto contactoNuevo = contactoEncontrado.get();
                 contactoNuevo.setIdContacto(idContacto);
                 contactoNuevo.setNombre(MContacto.getNombre());
                 contactoNuevo.setTelefono(MContacto.getTelefono());
                 contactoNuevo.setNotas(MContacto.getNotas());
                 return this.IContacto.save(contactoNuevo);
             }else {
                 throw new Exception("No se puede modificar porque contacto no registrado");
             }
         }catch (Exception error) {
             throw new RuntimeException(error.getMessage());
         }
     }

}
