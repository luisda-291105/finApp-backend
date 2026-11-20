package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailEntradaPendiente;
import com.luisda.fin_app_back.Repository.IGmailEntradaPendiente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @uso               Gestiona la cola en espera de los correos entrantes de Gmail
 * @capa              Servicio
 * @responsabilidades Encolar cada mensaje entrante (o notificación de llegada), reclamar entradas
 *                    para procesarlas de a una y registrar reintentos con backoff
 * @datos             Filas de gmail_entrada_pendiente (estado, intentos, ultimo_error)
 * @dependencias      IGmailEntradaPendiente
 * @usadoPor          CGmail (webhook Pub/Sub, activación de watch, worker y sincronizaciones)
 */
@Service
public class SGmailEntradas {

    /** Intentos máximos antes de dejar la entrada en error terminal para inspección. */
    static final int MAX_INTENTOS = 5;
    /** Backoff base entre reintentos: 30 s, 60 s, 120 s, 240 s, 480 s. */
    private static final long BACKOFF_BASE_MS = 30000L;
    /** Límite del mensaje de error guardado por entrada. */
    private static final int LARGO_ERROR_MAXIMO = 2000;
    /** Antigüedad máxima de una entrada en PROCESANDO antes de devolverla a la cola. */
    private static final long PROCESANDO_ATASCADA_MS = 10 * 60 * 1000L;

    private final IGmailEntradaPendiente entradas;

    public SGmailEntradas(IGmailEntradaPendiente entradas) {
        this.entradas = entradas;
    }

    /**
     * Pone un correo entrante en espera, uno por mensaje. Si ya está en la cola de la
     * cuenta se ignora (dedup); el guardado posterior del mensaje es idempotente.
     */
    @Transactional
    public void encolarMensaje(MFuente cuenta, String googleMessageId) {
        if (cuenta == null || googleMessageId == null || googleMessageId.isBlank()) return;
        if (this.entradas.existsByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), googleMessageId)) return;
        MGmailEntradaPendiente entrada = nueva(cuenta);
        entrada.setGoogleMessageId(googleMessageId.trim());
        this.entradas.save(entrada);
    }

    /** Pone en espera una notificación de llegada (Pub/Sub) aún sin expandir a mensajes. */
    @Transactional
    public void encolarNotificacion(MFuente cuenta, String historyId) {
        if (cuenta == null) return;
        MGmailEntradaPendiente entrada = nueva(cuenta);
        entrada.setHistoryId(historyId == null || historyId.isBlank() ? null : historyId.trim());
        this.entradas.save(entrada);
    }

    /**
     * Entradas listas para procesar (en espera y con el backoff vencido), las más antiguas
     * primero. Devuelve a lo sumo {@code limite} entradas.
     */
    @Transactional(readOnly = true)
    public List<MGmailEntradaPendiente> pendientes(int limite) {
        List<MGmailEntradaPendiente> listas = new ArrayList<>();
        for (MGmailEntradaPendiente entrada : this.entradas
                .findTop200ByEstadoOrderByFechaCreacionAsc(MGmailEntradaPendiente.ESTADO_EN_ESPERA)) {
            if (!backoffVencido(entrada)) continue;
            listas.add(entrada);
            if (listas.size() >= limite) break;
        }
        return listas;
    }

    /** Marca la entrada como en procesamiento y anota el inicio del intento. */
    @Transactional
    public void reclamar(MGmailEntradaPendiente entrada) {
        entrada.setEstado(MGmailEntradaPendiente.ESTADO_PROCESANDO);
        entrada.setFechaUltimoIntento(Instant.now());
        this.entradas.save(entrada);
    }

    /**
     * Devuelve a la cola las entradas que quedaron en PROCESANDO por un reinicio o una caída
     * del backend. Solo se recuperan las que llevan más de diez minutos sin avanzar.
     */
    @Transactional
    public void recuperarAtascadas() {
        Instant limite = Instant.now().minusMillis(PROCESANDO_ATASCADA_MS);
        for (MGmailEntradaPendiente entrada : this.entradas
                .findTop200ByEstadoOrderByFechaCreacionAsc(MGmailEntradaPendiente.ESTADO_PROCESANDO)) {
            if (entrada.getFechaUltimoIntento() != null && entrada.getFechaUltimoIntento().isAfter(limite)) continue;
            entrada.setEstado(MGmailEntradaPendiente.ESTADO_EN_ESPERA);
            this.entradas.save(entrada);
        }
    }

    /** Elimina la entrada al quedar procesada. */
    @Transactional
    public void marcarProcesado(MGmailEntradaPendiente entrada) {
        this.entradas.delete(entrada);
    }

    /**
     * Registra un fallo: la entrada vuelve a espera con backoff, o queda en error
     * terminal al agotar los intentos para revisión manual.
     */
    @Transactional
    public void marcarError(MGmailEntradaPendiente entrada, String mensajeError) {
        entrada.setIntentos(entrada.getIntentos() + 1);
        entrada.setEstado(entrada.getIntentos() >= MAX_INTENTOS
                ? MGmailEntradaPendiente.ESTADO_ERROR : MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        entrada.setUltimoError(acortar(mensajeError));
        entrada.setFechaUltimoIntento(Instant.now());
        this.entradas.save(entrada);
    }

    /** Quita de la cola todo lo pendiente de una cuenta (al desconectar o borrar la cuenta). */
    @Transactional
    public void eliminarPorCuenta(String idFuente) {
        this.entradas.deleteByCuenta_IdFuente(idFuente);
    }

    /** Entradas que agotaron los reintentos y quedaron en error terminal. */
    @Transactional(readOnly = true)
    public List<MGmailEntradaPendiente> enError() {
        return this.entradas.findTop50ByEstadoOrderByFechaUltimoIntentoDesc(MGmailEntradaPendiente.ESTADO_ERROR);
    }

    /**
     * Estado de la cola de una cuenta: cuántos correos están en espera o procesándose,
     * cuántos quedaron en error terminal y el último error registrado.
     */
    @Transactional(readOnly = true)
    public EstadoCola estado(String idFuente) {
        long enEspera = this.entradas.countByCuenta_IdFuenteAndEstado(
                idFuente, MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        long procesando = this.entradas.countByCuenta_IdFuenteAndEstado(
                idFuente, MGmailEntradaPendiente.ESTADO_PROCESANDO);
        long errores = this.entradas.countByCuenta_IdFuenteAndEstado(
                idFuente, MGmailEntradaPendiente.ESTADO_ERROR);
        List<MGmailEntradaPendiente> conError = errores == 0 ? List.of()
                : this.entradas.findTop5ByCuenta_IdFuenteAndEstadoOrderByFechaUltimoIntentoDesc(
                        idFuente, MGmailEntradaPendiente.ESTADO_ERROR);
        String ultimoError = conError.isEmpty() ? null : conError.get(0).getUltimoError();
        Instant ultimoIntento = conError.isEmpty() ? null : conError.get(0).getFechaUltimoIntento();
        return new EstadoCola(enEspera, procesando, errores, ultimoError, ultimoIntento);
    }

    /** Resumen de la cola para una cuenta. */
    public record EstadoCola(long enEspera, long procesando, long errores,
                             String ultimoError, Instant ultimoIntento) {
        /** Indica si el worker todavía tiene correos por procesar. */
        public boolean ocupada() { return enEspera > 0 || procesando > 0; }
    }

    private MGmailEntradaPendiente nueva(MFuente cuenta) {
        MGmailEntradaPendiente entrada = new MGmailEntradaPendiente();
        entrada.setId(UUID.randomUUID().toString());
        entrada.setCuenta(cuenta);
        entrada.setEstado(MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        entrada.setIntentos(0);
        entrada.setFechaCreacion(Instant.now());
        return entrada;
    }

    private boolean backoffVencido(MGmailEntradaPendiente entrada) {
        if (entrada.getIntentos() <= 0 || entrada.getFechaUltimoIntento() == null) return true;
        long pausa = BACKOFF_BASE_MS * (1L << Math.min(entrada.getIntentos() - 1, 5));
        return entrada.getFechaUltimoIntento().plusMillis(pausa).isBefore(Instant.now());
    }

    private String acortar(String mensaje) {
        String limpio = mensaje == null || mensaje.isBlank() ? "Error desconocido." : mensaje;
        return limpio.length() > LARGO_ERROR_MAXIMO ? limpio.substring(0, LARGO_ERROR_MAXIMO) : limpio;
    }
}
