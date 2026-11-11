package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGmailSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IGmailSuscripciones extends JpaRepository<MGmailSuscripcion, String> {
    List<MGmailSuscripcion> findByCuenta_IdFuenteOrderByUltimaActividadDesc(String idFuente);
    Optional<MGmailSuscripcion> findByCuenta_IdFuenteAndCorreoRemitente(String idFuente, String correoRemitente);
    long deleteByCuenta_IdFuente(String idFuente);
}
