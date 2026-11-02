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
public  class SUsuario {
    private final IUsuario IUsuario;
    private final SVerificadorContrasena verificador;

    // Constructor
    public SUsuario(IUsuario iUsuario, SVerificadorContrasena verificador) {
        IUsuario = iUsuario;
        this.verificador = verificador;
    }

    /** Agrega un usuario  */
    public MUsuario crear(MUsuario MUsuario) throws Exception{
        try {
            if (MUsuario.getContrasena() == null || MUsuario.getContrasena().length() < 8) {
                throw new Exception("La contraseña debe tener al menos 8 caracteres");
            }
            MUsuario.setContrasena(verificador.codificar(MUsuario.getContrasena()));
            return this.IUsuario.save(MUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Valida credenciales y migra a PBKDF2 las contraseñas heredadas en texto plano. */
    public MUsuario autenticar(String idUsuario, String contrasena, SVerificadorContrasena codificador) {
        if (idUsuario == null || contrasena == null) return null;
        return IUsuario.findById(idUsuario.trim()).filter(usuario -> {
            String guardada = usuario.getContrasena();
            if (codificador.verificar(contrasena, guardada)) return true;
            if (guardada != null && !guardada.startsWith(SVerificadorContrasena.PREFIJO)
                    && java.security.MessageDigest.isEqual(guardada.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                usuario.setContrasena(codificador.codificar(contrasena));
                IUsuario.save(usuario);
                return true;
            }
            return false;
        }).orElse(null);
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
    public List<MUsuario> buscarPorNombre(String nombreUsuario) throws Exception{
        try {
            return this.IUsuario.buscarPorNombre(nombreUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por identificador. */
    public Optional<MUsuario> buscarPorId(String idUsuario) throws Exception{
        try {
            return this.IUsuario.findById(idUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Elimina el usuario identificado */
    public Boolean eliminar(String idUsuario) throws Exception{
        try {
            Optional<MUsuario> usuarioEncontrado = this.IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()){
                this.IUsuario.deleteById(idUsuario);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque usuario no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Actualiza un usuario existente y comprueba que exista */

    public MUsuario modificar(MUsuario MUsuario , String idUsuario ) throws Exception{
        try {
            Optional<MUsuario> usuarioEncontrado = this.IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()){
                MUsuario usuarioNuevo=usuarioEncontrado.get();
                usuarioNuevo.setIdUsuario(idUsuario);
                usuarioNuevo.setNombre(MUsuario.getNombre());
                if (MUsuario.getContrasena() != null && !MUsuario.getContrasena().isBlank()) {
                    if (MUsuario.getContrasena().length() < 8) throw new Exception("La contraseña debe tener al menos 8 caracteres");
                    usuarioNuevo.setContrasena(verificador.codificar(MUsuario.getContrasena()));
                }
                return this.IUsuario.save(usuarioNuevo);
            }else {
                throw new Exception("No se puede modificar porque usuario no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }
}
