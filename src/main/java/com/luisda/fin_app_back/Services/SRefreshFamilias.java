package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Config.SesionConfig;
import com.luisda.fin_app_back.Model.MRefreshFamilia;
import com.luisda.fin_app_back.Repository.IRefreshFamilia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * @uso               Ciclo de vida de las familias de refresh tokens
 * @capa              Servicio
 * @responsabilidades Crear, rotar, revocar y purgar familias; detectors de reuso
 * @datos             MRefreshFamilia (solo hashes), SesionConfig
 * @dependencias      IRefreshFamilia, SesionConfig
 * @usadoPor          CUsuario
 */
@Service
public class SRefreshFamilias {
    private static final Logger LOGGER = LoggerFactory.getLogger(SRefreshFamilias.class);

    private final IRefreshFamilia familias;
    private final SesionConfig config;
    private final JwtTokenService tokens;

    public enum ResultadoReuso { VIGENTE, ANTERIOR_DENTRO_GRACIA, REUSO_DETECTADO, DESCONOCIDO }

    public SRefreshFamilias(IRefreshFamilia familias, SesionConfig config, JwtTokenService tokens) {
        this.familias = familias;
        this.config = config;
        this.tokens = tokens;
    }

    /**
     * Decide si el hash presentado corresponde al refresh vigente, al anterior dentro de la
     * gracia, o si supone un reuso detectado. Las comparaciones son en tiempo constante.
     */
    public ResultadoReuso evaluarReuso(MRefreshFamilia familia, String hashRefresh, String ip,
                                       String idUsuario, Instant ahora) {
        boolean vigente = tokens.coincideHash(familia.getHashActual(), hashRefresh);
        boolean anterior = tokens.coincideHash(familia.getHashAnterior(), hashRefresh);
        if (vigente) return ResultadoReuso.VIGENTE;
        if (!anterior) return ResultadoReuso.DESCONOCIDO;

        long graciaMs = config.reuseGraceSeconds() * 1000L;
        boolean dentroDeGracia = familia.getUsedAt() != null
                && (ahora.toEpochMilli() - familia.getUsedAt().toEpochMilli()) <= graciaMs;
        if (dentroDeGracia) {
            registrarReusoTolerado(familia.getFamilyId(), ip);
            return ResultadoReuso.ANTERIOR_DENTRO_GRACIA;
        }
        revocar(familia.getFamilyId(), ahora);
        registrarReuso(familia.getFamilyId(), idUsuario, ip);
        return ResultadoReuso.REUSO_DETECTADO;
    }

    public MRefreshFamilia buscar(String familyId) {
        if (familyId == null || familyId.isBlank()) return null;
        return familias.findById(familyId).orElse(null);
    }

    @Transactional
    public MRefreshFamilia crear(String familyId, String idUsuario, String hashRefresh,
                                 Instant ahora, Instant absoluto, long credentialVersion) {
        MRefreshFamilia familia = new MRefreshFamilia(familyId, idUsuario, hashRefresh, ahora,
                ahora, absoluto, credentialVersion);
        return familias.save(familia);
    }

    @Transactional
    public void rotar(MRefreshFamilia familia, String hashNuevo, Instant ahora) {
        familia.setHashAnterior(familia.getHashActual());
        familia.setHashActual(hashNuevo);
        familia.setUsedAt(ahora);
        familias.save(familia);
    }

    @Transactional
    public boolean revocar(String familyId, Instant ahora) {
        if (familyId == null || familyId.isBlank()) return false;
        MRefreshFamilia familia = familias.findById(familyId).orElse(null);
        if (familia == null || familia.estaRevocada()) return false;
        familia.setRevokedAt(ahora);
        familias.save(familia);
        return true;
    }

    /** Revoca todas las sesiones del usuario (cambio de contraseña, baja o desactivación). */
    @Transactional
    public int revocarTodasDelUsuario(String idUsuario, Instant ahora) {
        List<MRefreshFamilia> lista = familias.findByIdUsuario(idUsuario);
        int revocadas = 0;
        for (MRefreshFamilia familia : lista) {
            if (!familia.estaRevocada()) {
                familia.setRevokedAt(ahora);
                familias.save(familia);
                revocadas++;
            }
        }
        if (revocadas > 0) {
            LOGGER.info("Revocadas {} familias de refresh del usuario {}", revocadas, idUsuario);
        }
        return revocadas;
    }

    /** Evento de seguridad: nunca se registra el token, solo familyId e IP. */
    public void registrarReuso(String familyId, String idUsuario, String ip) {
        LOGGER.warn("SEGURIDAD: reuso de refresh fuera de la gracia. familyId={} usuario={} ip={}",
                familyId, idUsuario, ip);
    }

    public void registrarFallo(String familyId, String ip, String motivo) {
        LOGGER.warn("SEGURIDAD: refresh rechazado. familyId={} ip={} motivo={}", familyId, ip, motivo);
    }

    public void registrarReusoTolerado(String familyId, String ip) {
        LOGGER.info("SEGURIDAD: reuso de refresh dentro de la gracia, rotación idempotente. familyId={} ip={}",
                familyId, ip);
    }

    /**
     * Purga familias cuyo tope absoluto ya pasó (más la gracia, para conservar señales de reuso).
     * La retención cubre siempre el exp del refresh y el tope absoluto, no solo el del access.
     */
    @Scheduled(fixedDelayString = "${finapp.sesion.limpieza-intervalo-ms:3600000}",
            initialDelayString = "60000")
    @Transactional
    public void limpiarVencidas() {
        Instant corte = Instant.now().minusSeconds(config.reuseGraceSeconds());
        long borradas = familias.deleteByAbsoluteExpiresAtBefore(corte);
        if (borradas > 0) LOGGER.info("Limpieza de refreshes: {} familias vencidas eliminadas.", borradas);
    }
}
