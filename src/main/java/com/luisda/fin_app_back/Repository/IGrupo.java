package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * @uso               Consulta grupos creados por usuario
 * @capa              Repositorio
 * @responsabilidades findByIdUsuario
 * @datos             Recibe identificador de usuario y devuelve entidades MGrupo
 * @dependencias      MGrupo, Spring Data JPA
 * @usadoPor          Capas consumidoras de repositorios
 */

@Repository
public interface IGrupo extends JpaRepository<MGrupo, String>{

    List<MGrupo> findByUsuario_IdUsuario(String idUsuario);

    @Query("SELECT g FROM MGrupo g where g.nombre = :nombre")
    List<MGrupo> buscarPorNombre(
          @Param("nombre")  String nombre
    );

}
