package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * @uso Gestiona las operaciones disponibles de grupos
 * @capa Servicio
 * @responsabilidades CRUD y consulta por identificador del usuario creador
 * @datos Recibe y devuelve entidades MGrupo
 * @dependencias GrupoRepository, SValidadorOperaciones
 * @usadoPor Capas consumidoras de servicios
 */
@Service
public class SGrupo {
    private final IGrupo IGrupo;
    private final IUsuario IUsuario;

    public SGrupo(IGrupo iGrupo, IUsuario iUsuario) {
        IGrupo = iGrupo;
        IUsuario = iUsuario;
    }

    /**
     * Agrega un grupo
     */
    public MGrupo crear(MGrupo MGrupo) throws Exception {
        try {
            if (MGrupo.getUsuario() == null || MGrupo.getUsuario().getIdUsuario() == null) {
                throw new Exception("El grupo debe estar asociado a un usuario");
            }
            MGrupo.setUsuario(this.IUsuario.findById(MGrupo.getUsuario().getIdUsuario())
                    .orElseThrow(() -> new Exception("El usuario asociado no existe")));
            return this.IGrupo.save(MGrupo);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consultar grupos del usuario indicado. */
    public List<MGrupo> buscarPorUsuario(String idUsuario) throws Exception {
        return this.IGrupo.findByUsuario_IdUsuario(idUsuario);
    }

    /**
     * Consulta todos los grupos.
     */
    public List<MGrupo> buscarTodos() throws Exception {
        try {
            return this.IGrupo.findAll();
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /**
     * Consulta un grupo por nombre
     */
    public List<MGrupo> buscarPorNombre(String nombre) throws Exception {
        try {
            return this.IGrupo.buscarPorNombre(nombre);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /**
     * Consulta un grupo por identificador.
     */
    public Optional<MGrupo> buscarPorId(String idGrupo) throws Exception {
        try {
            return this.IGrupo.findById(idGrupo);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /**
     * Elimina el grupo identificado
     */
    public Boolean eliminar(String idGrupo) throws Exception {
        try {
            Optional<MGrupo> grupoEncontrado = this.IGrupo.findById(idGrupo);
            if (grupoEncontrado.isPresent()) {
                this.IGrupo.deleteById(idGrupo);
                return true;
            } else {
                throw new Exception("No se puede eliminar porque el grupo no esta registrado");
            }
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }


    /** Actualiza un usuario existente y comprueba que exista */
    public MGrupo modificar(MGrupo MGrupo, String idGrupo) throws Exception{
        try {
            Optional<MGrupo> grupoEncontrado = this.IGrupo.findById(idGrupo);
            if (grupoEncontrado.isPresent()){
                MGrupo nuevoGrupo = grupoEncontrado.get();
                nuevoGrupo.setIdGrupo(idGrupo);
                nuevoGrupo.setNombre(MGrupo.getNombre());
                nuevoGrupo.setDescripcion(MGrupo.getDescripcion());
                return  this.IGrupo.save(nuevoGrupo);
            }else {
                throw new Exception("No se puede modificar porque el grupo no registrado");
            }
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

}
