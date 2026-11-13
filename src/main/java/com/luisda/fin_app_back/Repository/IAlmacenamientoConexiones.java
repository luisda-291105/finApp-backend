package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IAlmacenamientoConexiones extends JpaRepository<MAlmacenamientoConexion, String> {

    /** Devuelve únicamente las conexiones del usuario indicado. */
    List<MAlmacenamientoConexion> findByUsuario_IdUsuario(String idUsuario);

    /** Devuelve una conexión solo si pertenece al usuario indicado. */
    Optional<MAlmacenamientoConexion> findByIdConexionAndUsuario_IdUsuario(String idConexion, String idUsuario);
}
