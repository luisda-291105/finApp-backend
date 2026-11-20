package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailPapeleraListaBlanca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Acceso a la lista blanca de remitentes exentos de la regla automática de papelera. */
public interface IGmailPapeleraListaBlanca extends JpaRepository<MGmailPapeleraListaBlanca, String> {

    /** Remitentes de la lista blanca de una cuenta, ordenados alfabéticamente. */
    List<MGmailPapeleraListaBlanca> findByFuente_IdFuenteOrderByRemitenteAsc(String idFuente);

    /** Remitente concreto de la lista blanca de una cuenta. */
    Optional<MGmailPapeleraListaBlanca> findByFuente_IdFuenteAndRemitente(String idFuente, String remitente);

    /** Fila concreta de la lista blanca por su identificador, restringida a la cuenta indicada. */
    Optional<MGmailPapeleraListaBlanca> findByIdAndFuente_IdFuente(String id, String idFuente);

    /** Indica si un remitente ya está en la lista blanca de la cuenta. */
    boolean existsByFuente_IdFuenteAndRemitente(String idFuente, String remitente);
}
