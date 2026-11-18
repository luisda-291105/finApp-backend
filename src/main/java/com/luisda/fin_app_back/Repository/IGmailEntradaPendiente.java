package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailEntradaPendiente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


/** Acceso a la cola de correos Gmail en espera de procesarse uno por uno. */
public interface IGmailEntradaPendiente extends JpaRepository<MGmailEntradaPendiente, String> {

    /** Entradas en espera, las más antiguas primero. */
    List<MGmailEntradaPendiente> findTop200ByEstadoOrderByFechaCreacionAsc(String estado);

    /** Entradas marcadas en error terminal (para inspección). */
    List<MGmailEntradaPendiente> findTop50ByEstadoOrderByFechaUltimoIntentoDesc(String estado);

    /** Indica si un mensaje ya está en la cola de la cuenta. */
    boolean existsByCuenta_IdFuenteAndGoogleMessageId(String idFuente, String googleMessageId);

    /** Elimina toda la cola de una cuenta (al desconectar o borrar la cuenta). */
    void deleteByCuenta_IdFuente(String idFuente);

    /** Cuenta las entradas de una cuenta en un estado concreto (para el panel de estado). */
    long countByCuenta_IdFuenteAndEstado(String idFuente, String estado);

    /** Últimas entradas en error terminal de una cuenta, la más reciente primero. */
    List<MGmailEntradaPendiente> findTop5ByCuenta_IdFuenteAndEstadoOrderByFechaUltimoIntentoDesc(
            String idFuente, String estado);
}
