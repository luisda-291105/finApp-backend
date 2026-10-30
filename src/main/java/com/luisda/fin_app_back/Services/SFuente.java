package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * @uso               Gestiona las operaciones CRUD disponibles de fuentes
 * @capa              Servicio
 * @responsabilidades Adicionar, eliminar, actualizar y consultar fuentes
 * @datos             Recibe y devuelve entidades MFuente
 * @dependencias      FuenteRepository, SValidadorOperaciones
 * @usadoPor          Capas consumidoras de servicios
 */
@Service
public class SFuente {
    private final IFuentes IFuentes;
    private final IUsuario IUsuario;
    private final IGrupo IGrupo;

    public SFuente(IFuentes iFuentes, IUsuario iUsuario, IGrupo iGrupo) {
        IFuentes = iFuentes;
        IUsuario = iUsuario;
        IGrupo = iGrupo;
    }

    /** Agrega una fuente  */
    public MFuente crear(MFuente MFuente)throws Exception{
        try{
            if (MFuente.getUsuario() == null || MFuente.getUsuario().getIdUsuario() == null) {
                throw new Exception("La fuente debe estar asociada a un usuario");
            }
            MFuente.setUsuario(this.IUsuario.findById(MFuente.getUsuario().getIdUsuario())
                    .orElseThrow(() -> new Exception("El usuario asociado no existe")));
            String tipo = MFuente.getTipo() == null ? "" : MFuente.getTipo().trim().toUpperCase(Locale.ROOT);
            String contenido = MFuente.getContenido() == null ? "" : MFuente.getContenido().trim();
            if (tipo.isEmpty() || contenido.isEmpty()) {
                throw new IllegalArgumentException("El tipo y el contenido de la fuente son obligatorios");
            }
            MFuente.setTipo(tipo);
            MFuente.setContenido(contenido);
            if (this.IFuentes.contarDuplicadas(MFuente.getUsuario().getIdUsuario(), tipo, contenido) > 0) {
                throw new IllegalArgumentException(tipo.equals("CORREO")
                        ? "Este correo ya está registrado en tu cuenta"
                        : "Esta fuente ya está registrada en tu cuenta");
            }
            return this.IFuentes.save(MFuente);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(error.getMessage(), error);
        }
    }

    /** Consulta todas las fuentes. */
    public List<MFuente> buscarTodos() throws  Exception{
        try {
            return this.IFuentes.findAll();
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    /** Consulta las fuentes del usuario indicado. */
    public List<MFuente> buscarPorUsuario(String idUsuario) {
        return this.IFuentes.findByUsuario_IdUsuario(idUsuario);
    }

    /**
     * Consulta todas las fuentes (uso administrativo).
     */
    /** Consulta una fuente por tipo */
    public List<MFuente> buscarPorTipo(String tipo) throws Exception{
        try {
            return this.IFuentes.buscarPorTipo(tipo);
        }catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    /** Consulta una fuente por identificador. */
    public Optional<MFuente> buscarPorId(String idFuente) throws Exception{
        try {
            return this.IFuentes.findById(idFuente);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    /** Consulta una fuente por contacto */
    @Transactional
    public Boolean eliminar(String idFuente, String idUsuario) throws  Exception {
        try {
            Optional<MFuente> fuenteEncontrada = this.IFuentes.findById(idFuente);
            if (fuenteEncontrada.isPresent()){
                MFuente fuente = fuenteEncontrada.get();
                if (idUsuario == null || fuente.getUsuario() == null || !idUsuario.equals(fuente.getUsuario().getIdUsuario())) {
                    throw new Exception("La fuente no pertenece al usuario indicado");
                }
                for (var grupo : this.IGrupo.findByFuentes_IdFuente(idFuente)) {
                    grupo.getFuentes().removeIf(item -> idFuente.equals(item.getIdFuente()));
                    this.IGrupo.save(grupo);
                }
                this.IFuentes.deleteById(idFuente);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque la fuente no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Actualiza un usuario existente y comprueba que exista */
    public MFuente modificar(MFuente MFuente , String idFuente, String idUsuario) throws Exception{
        try {
            Optional<MFuente> fuenteEncontrada = this.IFuentes.findById(idFuente);
            if (fuenteEncontrada.isPresent()){
                MFuente nuevaFuente = fuenteEncontrada.get();
                if (idUsuario == null || nuevaFuente.getUsuario() == null || !idUsuario.equals(nuevaFuente.getUsuario().getIdUsuario())) {
                    throw new Exception("La fuente no pertenece al usuario indicado");
                }
                String tipo = MFuente.getTipo() == null ? "" : MFuente.getTipo().trim().toUpperCase(Locale.ROOT);
                String contenido = MFuente.getContenido() == null ? "" : MFuente.getContenido().trim();
                if (tipo.isEmpty() || contenido.isEmpty()) {
                    throw new IllegalArgumentException("El tipo y el contenido de la fuente son obligatorios");
                }
                if (this.IFuentes.contarDuplicadasExceptoId(idUsuario, idFuente, tipo, contenido) > 0) {
                    throw new IllegalArgumentException(tipo.equals("CORREO")
                            ? "Este correo ya está registrado en tu cuenta"
                            : "Esta fuente ya está registrada en tu cuenta");
                }
                nuevaFuente.setIdFuente(idFuente);
                nuevaFuente.setTipo(tipo);
                nuevaFuente.setContenido(contenido);
                nuevaFuente.setEstado(MFuente.isEstado());
                return this.IFuentes.save(nuevaFuente);
            }else {
                throw new Exception("No se puede modificar porque la fuente no esta registrada");
            }
        }catch (Exception error) {
            throw new RuntimeException(error.getMessage());
        }
    }


}
