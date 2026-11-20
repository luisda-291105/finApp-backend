package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailPapeleraCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Acceso a las categorías de Gmail configuradas para el movimiento automático a la papelera. */
public interface IGmailPapeleraCategoria extends JpaRepository<MGmailPapeleraCategoria, String> {

    /** Categorías configuradas de una cuenta. */
    List<MGmailPapeleraCategoria> findByFuente_IdFuente(String idFuente);

    /** Categoría concreta de una cuenta. */
    Optional<MGmailPapeleraCategoria> findByFuente_IdFuenteAndCategoria(String idFuente, String categoria);
}
