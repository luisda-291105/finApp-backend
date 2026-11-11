package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MTarjeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;


import java.util.List;
import java.util.Optional;

/**
 * @uso               crud usuario
 * @capa              Repositorio
 * @responsabilidades CRUD y búsquedas por contacto y nombre
 * @datos             Recibe y devuelve entidades MUsuario
 * @dependencias      MUsuario, Spring Data JPA
 * @usadoPor          SUsuario
 */

public interface ITarjeta extends JpaRepository<MTarjeta,String> {
    List<MTarjeta> findByUsuario_IdUsuario(String idUsuario);
    @Query("SELECT t FROM MTarjeta t WHERE t.usuario.idUsuario = :idUsuario AND " +
            "(LOWER(t.titular) LIKE LOWER(CONCAT('%', :termino, '%')) OR LOWER(t.entidad) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
            "ORDER BY t.entidad ASC")
    List<MTarjeta> buscarPropias(@Param("idUsuario") String idUsuario, @Param("termino") String termino, Pageable pageable);
    Optional<MTarjeta> findByIdTarjetaAndUsuario_IdUsuario(String idTarjeta, String idUsuario);

    @Query("SELECT t FROM MTarjeta t WHERE t.titular = :titular ")
    List<MTarjeta> buscarPorTitular(
            @Param("titular") String titular
    );

    @Query("SELECT t FROM MTarjeta t WHERE t.entidad = :entidad ")
    List<MTarjeta> buscarPorEntidad(
            @Param("entidad") String entidad
    );
}
