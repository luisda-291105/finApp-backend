package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
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
    private final IFuentes IFuentes;

    public SGrupo(IGrupo iGrupo, IUsuario iUsuario, IFuentes iFuentes) {
        IGrupo = iGrupo;
        IUsuario = iUsuario;
        IFuentes = iFuentes;
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
            List<MFuente> fuentesValidadas = new ArrayList<>();
            for (MFuente fuenteSolicitada : MGrupo.getFuentes()) {
                MFuente fuente = this.IFuentes.findById(fuenteSolicitada.getIdFuente())
                        .orElseThrow(() -> new Exception("La fuente no está registrada"));
                if (fuente.getUsuario() == null || !MGrupo.getUsuario().getIdUsuario().equals(fuente.getUsuario().getIdUsuario())) {
                    throw new Exception("La fuente no pertenece al usuario indicado");
                }
                fuentesValidadas.add(fuente);
            }
            MGrupo.setFuentes(fuentesValidadas);
            return this.IGrupo.save(MGrupo);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consultar grupos del usuario indicado. */
    public List<MGrupo> buscarPorUsuario(String idUsuario) throws Exception {
        return this.IGrupo.findByUsuario_IdUsuario(idUsuario);
    }

    /** Vincula al grupo una fuente existente que pertenezca al mismo usuario. */
    @Transactional
    public MGrupo vincularFuente(String idGrupo, String idFuente, String idUsuario) throws Exception {
        MGrupo grupo = this.IGrupo.findById(idGrupo)
                .orElseThrow(() -> new Exception("El grupo no está registrado"));
        if (idUsuario == null || grupo.getUsuario() == null || !idUsuario.equals(grupo.getUsuario().getIdUsuario())) {
            throw new Exception("El grupo no pertenece al usuario indicado");
        }

        MFuente fuente = this.IFuentes.findById(idFuente)
                .orElseThrow(() -> new Exception("La fuente no está registrada"));
        if (fuente.getUsuario() == null || !idUsuario.equals(fuente.getUsuario().getIdUsuario())) {
            throw new Exception("La fuente no pertenece al usuario indicado");
        }

        boolean yaVinculada = grupo.getFuentes().stream()
                .anyMatch(item -> idFuente.equals(item.getIdFuente()));
        if (!yaVinculada) grupo.getFuentes().add(fuente);
        return this.IGrupo.save(grupo);
    }

    /** Desvincula del grupo una fuente, sin eliminar el registro del usuario. */
    @Transactional
    public MGrupo desvincularFuente(String idGrupo, String idFuente, String idUsuario) throws Exception {
        MGrupo grupo = this.IGrupo.findById(idGrupo)
                .orElseThrow(() -> new Exception("El grupo no está registrado"));
        if (idUsuario == null || grupo.getUsuario() == null || !idUsuario.equals(grupo.getUsuario().getIdUsuario())) {
            throw new Exception("El grupo no pertenece al usuario indicado");
        }

        grupo.getFuentes().removeIf(fuente -> idFuente.equals(fuente.getIdFuente()));
        return this.IGrupo.save(grupo);
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
    public Boolean eliminar(String idGrupo, String idUsuario) throws Exception {
        try {
            Optional<MGrupo> grupoEncontrado = this.IGrupo.findById(idGrupo);
            if (grupoEncontrado.isPresent() && grupoEncontrado.get().getUsuario().getIdUsuario().equals(idUsuario)) {
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
    public MGrupo modificar(MGrupo MGrupo, String idGrupo, String idUsuario) throws Exception{
        try {
            Optional<MGrupo> grupoEncontrado = this.IGrupo.findById(idGrupo);
            if (grupoEncontrado.isPresent() && grupoEncontrado.get().getUsuario().getIdUsuario().equals(idUsuario)){
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
