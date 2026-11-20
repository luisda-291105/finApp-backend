package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGrupoMiembro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistencia de la membresía de los grupos. */
public interface IGrupoMiembro extends JpaRepository<MGrupoMiembro, String> {
    List<MGrupoMiembro> findByIdGrupo(String idGrupo);

    List<MGrupoMiembro> findByIdUsuario(String idUsuario);

    Optional<MGrupoMiembro> findByIdGrupoAndIdUsuario(String idGrupo, String idUsuario);

    boolean existsByIdGrupoAndIdUsuario(String idGrupo, String idUsuario);

    long deleteByIdGrupoAndIdUsuario(String idGrupo, String idUsuario);

    long deleteByIdGrupo(String idGrupo);
}
