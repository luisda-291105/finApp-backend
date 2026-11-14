package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MSuscripcion;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @uso               Persistencia de todas las suscripciones (manuales y de Gmail)
 * @capa              Repositorio
 * @responsabilidades Guardar y consultar suscripciones por propietario, origen y cuenta de correo
 * @datos             Identificador del usuario, origen y entidad MSuscripcion
 * @dependencias      MSuscripcion, Spring Data JPA
 * @usadoPor          SSuscripcion, SSuscripcionGmail
 */
public interface ISuscripcion extends JpaRepository<MSuscripcion, String> {

    /** Todas las suscripciones del usuario con su cuenta de correo ya cargada. */
    @Query("SELECT s FROM MSuscripcion s LEFT JOIN FETCH s.cuenta WHERE s.usuario.idUsuario = :idUsuario")
    List<MSuscripcion> findDelUsuario(@Param("idUsuario") String idUsuario);

    /** Suscripciones del usuario filtradas por origen. */
    @Query("SELECT s FROM MSuscripcion s LEFT JOIN FETCH s.cuenta "
            + "WHERE s.usuario.idUsuario = :idUsuario AND s.origen = :origen")
    List<MSuscripcion> findDelUsuarioPorOrigen(@Param("idUsuario") String idUsuario,
                                               @Param("origen") OrigenSuscripcion origen);

    /** Busca una suscripción únicamente si pertenece al usuario indicado. */
    @Query("SELECT s FROM MSuscripcion s LEFT JOIN FETCH s.cuenta "
            + "WHERE s.id = :id AND s.usuario.idUsuario = :idUsuario")
    Optional<MSuscripcion> findPropia(@Param("id") String id, @Param("idUsuario") String idUsuario);

    /** Suscripciones detectadas en una cuenta de correo, de la más reciente a la más antigua. */
    List<MSuscripcion> findByCuenta_IdFuenteOrderByUltimaActividadDesc(String idFuente);

    /** Suscripción detectada en una cuenta de correo para un remitente concreto. */
    Optional<MSuscripcion> findByCuenta_IdFuenteAndCorreoRemitente(String idFuente, String correoRemitente);

    /** Elimina las suscripciones de Gmail de la cuenta indicada. */
    @Transactional
    long deleteByCuenta_IdFuente(String idFuente);
}
