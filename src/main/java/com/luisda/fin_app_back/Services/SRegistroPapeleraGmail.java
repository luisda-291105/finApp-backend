package com.luisda.fin_app_back.Services;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @uso               Registro en memoria de vistas previas y lotes de limpieza de la papelera
 * @capa              Servicio
 * @responsabilidades Conservar las vistas previas (`vista_previa_id` + parámetros + `total_estimado`)
 *                    y los lotes de limpieza con su progreso, además del bloqueo «una limpieza por
 *                    cuenta» (RF-38/RF-39/CD-04)
 * @datos             Vistas previas, lotes de limpieza y cuentas con una limpieza en curso
 * @dependencias      Ninguna (estado en memoria)
 * @usadoPor          SReglaPapeleraGmail
 *
 * Un reinicio de la aplicación pierde las vistas previas y los lotes activos (patrón
 * `SImportacionLoteGmail`); el contrato solo exige que un `vista_previa_id` inexistente o expirado
 * responda `409`, lo que el TTL en memoria satisface sin añadir tablas no contempladas en CD-02.
 */
@Service
public class SRegistroPapeleraGmail {

    /** Vigencia de una vista previa antes de exigir una nueva (RF-39). */
    private static final long RETENCION_VISTAS_SEGUNDOS = 1_800;
    /** Retención del historial de lotes de limpieza terminados. */
    private static final long RETENCION_LOTES_SEGUNDOS = 86_400;

    private final Map<String, VistaPrevia> vistas = new ConcurrentHashMap<>();
    private final Map<String, LoteLimpieza> lotes = new ConcurrentHashMap<>();
    private final Set<String> limpiezasEnCurso = ConcurrentHashMap.newKeySet();

    /** Correo elegible evaluado en una vista previa, con los datos mínimos para marcarlo al mover. */
    public record CorreoElegible(String id, String categoria, String remitente, String asunto,
                                 String fragmento, Instant fechaInterna) { }

    /** Vista previa registrada: fija los parámetros evaluados y el total confirmable. */
    public record VistaPrevia(String id, String idUsuario, String idCuenta, List<String> categorias,
                              int antiguedadDias, int totalEstimado, List<CorreoElegible> elegibles,
                              Instant creadoEn) { }

    /** Registra una vista previa y devuelve su identificador y parámetros evaluados. */
    public VistaPrevia registrarVistaPrevia(String idUsuario, String idCuenta, List<String> categorias,
                                            int antiguedadDias, int totalEstimado,
                                            List<CorreoElegible> elegibles) {
        limpiarVistasExpiradas();
        VistaPrevia vista = new VistaPrevia(UUID.randomUUID().toString(), idUsuario, idCuenta,
                List.copyOf(categorias), antiguedadDias, totalEstimado, List.copyOf(elegibles), Instant.now());
        vistas.put(vista.id(), vista);
        return vista;
    }

    /** Vista previa vigente del usuario y la cuenta indicados; vacío si no existe o expiró. */
    public Optional<VistaPrevia> obtenerVistaPrevia(String id, String idUsuario, String idCuenta) {
        if (id == null || id.isBlank()) return Optional.empty();
        VistaPrevia vista = vistas.get(id);
        if (vista == null || !vista.idUsuario().equals(idUsuario) || !vista.idCuenta().equals(idCuenta)) {
            return Optional.empty();
        }
        if (vista.creadoEn().getEpochSecond() < Instant.now().getEpochSecond() - RETENCION_VISTAS_SEGUNDOS) {
            vistas.remove(id, vista);
            return Optional.empty();
        }
        return Optional.of(vista);
    }

    /** Toma el bloqueo «una limpieza por cuenta»; false si ya hay una limpieza en curso (RF-38). */
    public boolean intentarBloquearLimpieza(String idCuenta) {
        return limpiezasEnCurso.add(idCuenta);
    }

    /** Libera el bloqueo de limpieza de la cuenta al terminar (o fallar) la ejecución. */
    public void liberarLimpieza(String idCuenta) {
        limpiezasEnCurso.remove(idCuenta);
    }

    /** Crea un lote de limpieza en proceso con el total de correos elegibles. */
    public LoteLimpieza crearLote(String idUsuario, String idCuenta, int total) {
        limpiarLotesExpirados();
        LoteLimpieza lote = new LoteLimpieza(UUID.randomUUID().toString(), idUsuario, idCuenta, total);
        lotes.put(lote.getId(), lote);
        return lote;
    }

    /** Lote de limpieza del usuario y la cuenta indicados; vacío si no existe o es de otra cuenta. */
    public Optional<LoteLimpieza> consultarLote(String idUsuario, String idCuenta, String loteId) {
        if (loteId == null || loteId.isBlank()) return Optional.empty();
        LoteLimpieza lote = lotes.get(loteId);
        if (lote == null || !lote.getIdUsuario().equals(idUsuario) || !lote.getIdCuenta().equals(idCuenta)) {
            return Optional.empty();
        }
        return Optional.of(lote);
    }

    private void limpiarVistasExpiradas() {
        long corte = Instant.now().getEpochSecond() - RETENCION_VISTAS_SEGUNDOS;
        vistas.entrySet().removeIf(entrada -> entrada.getValue().creadoEn().getEpochSecond() < corte);
    }

    private void limpiarLotesExpirados() {
        long corte = Instant.now().getEpochSecond() - RETENCION_LOTES_SEGUNDOS;
        lotes.entrySet().removeIf(entrada -> entrada.getValue().isTerminado()
                && entrada.getValue().getCreadoEn().getEpochSecond() < corte);
    }

    /** Progreso mutable de una limpieza explícita, consultable mientras se ejecuta en segundo plano. */
    public static final class LoteLimpieza {
        private final String id;
        private final String idUsuario;
        private final String idCuenta;
        private final int total;
        private final Instant creadoEn = Instant.now();
        private volatile String estado = "EN_PROCESO";
        private volatile int movidos;
        private volatile int omitidos;
        private volatile int fallidos;
        private volatile boolean limiteEjecucionAlcanzado;

        private LoteLimpieza(String id, String idUsuario, String idCuenta, int total) {
            this.id = id;
            this.idUsuario = idUsuario;
            this.idCuenta = idCuenta;
            this.total = total;
        }

        public String getId() { return id; }
        public String getIdUsuario() { return idUsuario; }
        public String getIdCuenta() { return idCuenta; }
        public int getTotal() { return total; }
        public Instant getCreadoEn() { return creadoEn; }

        public String getEstado() { return estado; }
        public void setEstado(String estado) { this.estado = estado; }

        public int getMovidos() { return movidos; }
        public void setMovidos(int movidos) { this.movidos = movidos; }

        public int getOmitidos() { return omitidos; }
        public void setOmitidos(int omitidos) { this.omitidos = omitidos; }

        public int getFallidos() { return fallidos; }
        public void setFallidos(int fallidos) { this.fallidos = fallidos; }

        public boolean isLimiteEjecucionAlcanzado() { return limiteEjecucionAlcanzado; }
        public void setLimiteEjecucionAlcanzado(boolean limiteEjecucionAlcanzado) {
            this.limiteEjecucionAlcanzado = limiteEjecucionAlcanzado;
        }

        public boolean isTerminado() { return !"EN_PROCESO".equals(estado); }
    }
}
