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

    List<MFuente> findByUsuario_IdUsuario(String idUsuario);

    @Query("SELECT COUNT(f) FROM MFuente f WHERE f.usuario.idUsuario = :idUsuario AND UPPER(f.tipo) = UPPER(:tipo) AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(:contenido))")
    long contarDuplicadas(@Param("idUsuario") String idUsuario, @Param("tipo") String tipo, @Param("contenido") String contenido);

    @Query("SELECT COUNT(f) FROM MFuente f WHERE f.usuario.idUsuario = :idUsuario AND f.idFuente <> :idFuente AND UPPER(f.tipo) = UPPER(:tipo) AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(:contenido))")
    long contarDuplicadasExceptoId(@Param("idUsuario") String idUsuario, @Param("idFuente") String idFuente, @Param("tipo") String tipo, @Param("contenido") String contenido);

    @Query("SELECT f FROM MFuente f WHERE f.tipo = :tipo")
    List<MFuente> buscarPorTipo (
           @Param("tipo") String tipoFuente
    );



}
