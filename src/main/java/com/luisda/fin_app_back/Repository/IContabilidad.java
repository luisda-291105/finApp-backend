package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MContabilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IContabilidad extends JpaRepository<MContabilidad, String> {
    Optional<MContabilidad> findByIdUsuario(String idUsuario);
}
