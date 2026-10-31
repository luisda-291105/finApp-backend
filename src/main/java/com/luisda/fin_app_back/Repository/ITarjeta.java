package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MTarjeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @uso               crud usuario
 * @capa              Repositorio
 * @responsabilidades CRUD y búsquedas por contacto y nombre
 * @datos             Recibe y devuelve entidades MUsuario
 * @dependencias      MUsuario, Spring Data JPA
 * @usadoPor          SUsuario
 */
@Repository
public interface ITarjeta extends JpaRepository<MTarjeta,String> {
    List<MTarjeta> findByUsuario_IdUsuario(String idUsuario);

    @Query("SELECT t FROM MTarjeta t WHERE t.titular = :titular ")
    List<MTarjeta> buscarPorTitular(
            @Param("titular") String titular
    );

    @Query("SELECT t FROM MTarjeta t WHERE t.entidad = :entidad ")
    List<MTarjeta> buscarPorEntidad(
            @Param("entidad") String entidad
    );
}
