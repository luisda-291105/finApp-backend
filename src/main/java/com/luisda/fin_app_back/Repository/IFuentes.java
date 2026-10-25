package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MFuente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


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

    @Query("SELECT f FROM MFuente f WHERE f.tipo = :tipo")
    List<MFuente> buscarPorTipo (
           @Param("tipo") String tipoFuente
    );

    @Query("SELECT f FROM MFuente f WHERE f.idUsuario = :usuario")
    List<MFuente> buscarPorUsuario(
           @Param("usuario") String usuario
    ) ;

}
