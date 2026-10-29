package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Repository.ITarjeta;
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
public class STarjeta {
    private final ITarjeta ITarjeta;
    private final IUsuario IUsuario;

    public STarjeta(ITarjeta ITarjeta, IUsuario IUsuario) {
        this.ITarjeta = ITarjeta;
        this.IUsuario = IUsuario;
    }

    /** Agrega un usuario  */
    public MTarjeta crear(MTarjeta MTarjeta) throws Exception{
        try {
            if (MTarjeta.getUsuario() == null || MTarjeta.getUsuario().getIdUsuario() == null) {
                throw new Exception("La tarjeta debe estar asociada a un usuario");
            }
            MTarjeta.setUsuario(this.IUsuario.findById(MTarjeta.getUsuario().getIdUsuario())
                    .orElseThrow(() -> new Exception("El usuario asociado no existe")));
            return this.ITarjeta.save(MTarjeta);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta todos los usuarios. */
    public List<MTarjeta> buscarTodos() throws Exception{
        try {
            return this.ITarjeta.findAll();
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    public List<MTarjeta> buscarPorUsuario(String idUsuario) throws Exception {
        return this.ITarjeta.findByUsuario_IdUsuario(idUsuario);
    }

    /** Consulta por titular. */
    public List<MTarjeta> buscarPorTitular(String titular) throws Exception{
        try {
            return this.ITarjeta.buscarPorTitular(titular);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta por entidad. */
    public List<MTarjeta> buscarPorEntidad(String entidad) throws Exception {
        try {
            return this.ITarjeta.buscarPorEntidad(entidad);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta por id. */
    public Optional<MTarjeta> buscarPorId(String idTarjeta) throws Exception {
        try {
            return this.ITarjeta.findById(idTarjeta);
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** eliminar por id. */
    public Boolean eliminar(String idTarjeta) throws Exception {
        try {
            Optional<MTarjeta> tarjetaEncontrada = this.ITarjeta.findById(idTarjeta);
            if (tarjetaEncontrada.isPresent()) {
                this.ITarjeta.deleteById(idTarjeta);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque la tarjeta no esta registrado");
            }
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** modificar por id. */
    public MTarjeta modificar(MTarjeta MTarjeta , String idTarjeta) throws Exception {
        try {
            Optional<MTarjeta> tarjetaEncontrada = this.ITarjeta.findById(idTarjeta);
            if (tarjetaEncontrada.isPresent()) {
                MTarjeta tarjetaNueva = tarjetaEncontrada.get();
                tarjetaNueva.setIdTarjeta(idTarjeta);
                tarjetaNueva.setTitular(MTarjeta.getTitular());
                tarjetaNueva.setNumero(MTarjeta.getNumero());
                tarjetaNueva.setExpiracion(MTarjeta.getExpiracion());
                tarjetaNueva.setCvv(MTarjeta.getCvv());
                tarjetaNueva.setEntidad(MTarjeta.getEntidad());
                tarjetaNueva.setColorTema(MTarjeta.getColorTema());
                return this.ITarjeta.save(tarjetaNueva);

            }else {
                throw new Exception("No se puede modificar porque la tarjeta no esta registrado");
            }
        } catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

}
