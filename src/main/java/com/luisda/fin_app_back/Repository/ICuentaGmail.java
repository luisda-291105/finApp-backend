package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.CuentaGmail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ICuentaGmail extends JpaRepository<CuentaGmail, Long> {
    List<CuentaGmail> findByUsuarioIdOrderByCreadaEnAsc(String usuarioId);
    Optional<CuentaGmail> findByIdAndUsuarioId(Long id, String usuarioId);
    Optional<CuentaGmail> findByUsuarioIdAndEmailIgnoreCase(String usuarioId, String email);
}
