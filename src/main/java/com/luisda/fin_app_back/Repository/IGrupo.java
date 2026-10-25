package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    List<MGrupo> buscarPorUsuario(String idUsuario) throws Exception;
    List<MGrupo> buscarPorNombre(String nombre) throws Exception;

    void delete(Optional<MGrupo> grupoEncontrado);
}
