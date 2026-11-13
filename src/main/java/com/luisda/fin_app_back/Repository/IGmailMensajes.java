package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IGmailMensajes extends JpaRepository<MGmailMensaje, String> {
    interface Resumen {
        String getGoogleMessageId();
        String getRemitente();
        String getDestinatario();
        String getAsunto();
        String getFragmento();
        String getEtiquetasJson();
        String getNombresEtiquetasJson();
        Instant getFechaInterna();
    }

    @Query("""
            SELECT m.googleMessageId AS googleMessageId, m.remitente AS remitente,
                   m.destinatario AS destinatario, m.asunto AS asunto,
                   m.fragmento AS fragmento, m.etiquetasJson AS etiquetasJson,
                   m.nombresEtiquetasJson AS nombresEtiquetasJson, m.fechaInterna AS fechaInterna
            FROM MGmailMensaje m
            WHERE m.cuenta.idFuente = :idFuente AND m.fechaInterna > :fecha
            ORDER BY m.fechaInterna DESC
            """)
    List<Resumen> findResumenesByCuentaAndFecha(@Param("idFuente") String idFuente,
                                                @Param("fecha") Instant fecha);

    List<MGmailMensaje> findByCuenta_IdFuenteAndFechaInternaAfterAndListUnsubscribeIsNotNullOrderByFechaInternaDesc(
            String idFuente, Instant fecha);

    List<MGmailMensaje> findByCuenta_IdFuenteAndFechaInternaAfterOrderByFechaInternaDesc(String idFuente, Instant fecha);
    List<MGmailMensaje> findTop500ByCuenta_IdFuenteAndFechaInternaAfterAndMovimientoAutomaticoEstadoOrderByFechaInternaAsc(
            String idFuente, Instant fecha, String movimientoAutomaticoEstado);
    List<MGmailMensaje> findByCuenta_Usuario_IdUsuarioAndMovimientoAlertaPendienteTrueOrderByFechaInternaDesc(String idUsuario);
    Optional<MGmailMensaje> findByIdAndCuenta_Usuario_IdUsuario(String id, String idUsuario);
    Optional<MGmailMensaje> findByCuenta_IdFuenteAndGoogleMessageId(String idFuente, String googleMessageId);

    boolean existsByCuenta_IdFuenteAndGoogleMessageId(String idFuente, String googleMessageId);
    @org.springframework.transaction.annotation.Transactional
    long deleteByCuenta_IdFuente(String idFuente);
    @org.springframework.transaction.annotation.Transactional
    long deleteByCuenta_IdFuenteAndGoogleMessageId(String idFuente, String googleMessageId);
    @org.springframework.transaction.annotation.Transactional
    long deleteByCuenta_IdFuenteAndFechaInternaBefore(String idFuente, Instant fecha);
}
