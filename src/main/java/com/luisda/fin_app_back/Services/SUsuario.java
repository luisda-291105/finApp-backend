package com.luisda.fin_app_back.Services;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


/**
 * @uso               Gestiona las operaciones disponibles de usuarios
 * @capa              Servicio
 * @responsabilidades CRUD, búsquedas por contacto y nombre, y unicidad del contacto
 * @datos             Recibe y devuelve entidades MUsuario
 * @dependencias      UsuarioRepository, SValidadorOperaciones
 * @usadoPor          Capas consumidoras de servicios
 */
@Service
public abstract class SUsuario implements IUsuario{
    private final IUsuario IUsuario;

    // Constructor
    public SUsuario(IUsuario iUsuario) {
        IUsuario = iUsuario;
    }

    /** Agrega un usuario y comprueba que su contacto no esté registrado. */
    public MUsuario crearUsuario(MUsuario MUsuario) throws Exception{
        try {
            return this.IUsuario.save(MUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta todos los usuarios. */
    public List<MUsuario> buscarTodos() throws Exception{
        try {
            return this.IUsuario.findAll();
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por nombre */
    @Override
    public List<MUsuario> buscarPorNombre(String nombreUsuario) throws Exception{
        try {
            return this.IUsuario.buscarPorNombre(nombreUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por identificador. */
    public Optional<MUsuario> buscarPorIdUsuario(String idUsuario) throws Exception{
        try {
            return this.IUsuario.findById(idUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por contacto */
    @Override
    public List<MUsuario> buscarPorContacto(String contacto) throws Exception{
        try {
            return this.IUsuario.buscarPorContacto(contacto);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Elimina el usuario identificado */
    @Override
    public Boolean eliminarUsuario(String idUsuario) throws Exception{
        try {
            Optional<MUsuario> usuarioEncontrado = this.IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()){
                this.IUsuario.delete(usuarioEncontrado);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque usuario no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Actualiza un usuario existente y comprueba que exista */
    @Override
    public MUsuario modificarUsuario(MUsuario MUsuario , String idUsuario ) throws Exception{
        try {
            Optional<MUsuario> usuarioEncontrado = this.IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()){
                MUsuario usuarioNuevo=usuarioEncontrado.get();
                usuarioNuevo.setIdUsuario(idUsuario);
                usuarioNuevo.setNombre(MUsuario.getNombre());
                usuarioNuevo.setContacto(MUsuario.getContacto());
                usuarioNuevo.setContrasena(MUsuario.getContrasena());
                usuarioNuevo.setPropiedad(MUsuario.getPropiedad());
                return this.IUsuario.save(usuarioNuevo);
            }else {
                throw new Exception("No se puede modificar porque usuario no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }
}
