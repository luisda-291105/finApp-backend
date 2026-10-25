package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

@Repository
public interface IUsuario extends JpaRepository<MUsuario , String> {

    // consultas
    List<MUsuario> buscarPorNombre (String nombreUsuario)throws Exception;
    List<MUsuario> buscarPorContacto (String contacto)throws Exception;


    void delete(Optional<MUsuario> usuarioEncontrado);
}