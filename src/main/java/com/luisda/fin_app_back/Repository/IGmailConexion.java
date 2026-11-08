package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailConexion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IGmailConexion extends JpaRepository<MGmailConexion, String> {
    java.util.Optional<MGmailConexion> findByIdUsuario(String idUsuario);
}
