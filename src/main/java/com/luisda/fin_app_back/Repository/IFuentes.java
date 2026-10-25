package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MFuente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @uso               Persiste y consulta fuentes de usuarios
 * @capa              Repositorio
 * @responsabilidades CRUD básico de fuentes
 * @datos             Recibe y devuelve entidades MFuente
 * @dependencias      MFuente, Spring Data JPA
 * @usadoPor          Capas de servicio consumidoras
 */
@Repository
public interface IFuentes extends JpaRepository<MFuente , String> {
    List<MFuente> buscarPorTipo (String tipoFuente)throws Exception;
    List<MFuente> buscarPorUsuario(String usuario) throws Exception;

    void delete(Optional<MFuente> fuenteEncontrada);
}
