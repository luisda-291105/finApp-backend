package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailPapeleraConfiguracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acceso a la configuración de la regla automática de papelera por cuenta Gmail. */
public interface IGmailPapeleraConfiguracion extends JpaRepository<MGmailPapeleraConfiguracion, String> {

    /** Configuración de una cuenta concreta. */
    Optional<MGmailPapeleraConfiguracion> findByFuente_IdFuente(String idFuente);
}
