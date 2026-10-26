package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public SFuente(IFuentes iFuentes) {
        IFuentes = iFuentes;
    }

    /** Agrega una fuente  */
    public MFuente crear(MFuente MFuente)throws Exception{
        try{
            return this.IFuentes.save(MFuente);
        } catch (Exception error) {
            throw new RuntimeException(error);
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

    /** Consulta una fuente por usuario
    public List<MFuente> buscarPorUsuario(String usuario) throws Exception{
        try {
            return this.IFuentes.buscarPorUsuario(usuario);
        }catch (Exception error) {
            throw new RuntimeException(error);
        }
    }*/

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
    public Boolean eliminar(String idFuente) throws  Exception {
        try {
            Optional<MFuente> fuenteEncontrada = this.IFuentes.findById(idFuente);
            if (fuenteEncontrada.isPresent()){
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
    public MFuente modificar(MFuente MFuente , String idFuente) throws Exception{
        try {
            Optional<MFuente> fuenteEncontrada = this.IFuentes.findById(idFuente);
            if (fuenteEncontrada.isPresent()){
                MFuente nuevaFuente = fuenteEncontrada.get();
                nuevaFuente.setIdFuente(idFuente);
                nuevaFuente.setTipo(MFuente.getTipo());
                nuevaFuente.setIdentificador(MFuente.getIdentificador());
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
