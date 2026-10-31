package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MUsuario;
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
public interface IUsuario extends JpaRepository<MUsuario , String> {

    // consultas
    @Query("SELECT u FROM MUsuario u WHERE u.nombre = :nombre")
    List<MUsuario> buscarPorNombre(
            @Param("nombre") String nombreUsuario
    );

}