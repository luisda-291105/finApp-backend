package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.ImportacionLoteGmailDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Procesa mensajes Gmail en segundo plano y conserva únicamente el progreso y resultados en memoria.
 * Un reinicio de la aplicación pierde los lotes activos y su historial.
 */
@Service
public class SImportacionLoteGmail {
    public static final String REMITENTE_OFICIAL = "alertasynotificaciones@ayn.notificacionesbancolombia.com";
    public static final String REMITENTE_EXCLUIDO = "info@mercadeo.correobancolombia.com";

    private static final String ESTADO_IMPORTADO = "IMPORTADO";
    private static final String ESTADO_POR_REVISAR = "POR_REVISAR";
    private static final String ESTADO_DUPLICADO = "DUPLICADO";
    private static final String ESTADO_ERROR = "ERROR";
    private static final long RETENCION_LOTES_SEGUNDOS = 86_400;

    private final SClasificadorMovimiento clasificador;
    private final SMovimientoContable movimientos;
    private final ParserMovimientoBancolombia parser;
    private final Executor executor;
    private final Map<String, Lote> lotes = new ConcurrentHashMap<>();

    public SImportacionLoteGmail(SClasificadorMovimiento clasificador, SMovimientoContable movimientos,
                                 ParserMovimientoBancolombia parser,
                                 @Qualifier("gmailImportExecutor") Executor executor) {
        this.clasificador = clasificador;
        this.movimientos = movimientos;
        this.parser = parser;
        this.executor = executor;
    }

    public ImportacionLoteGmailDTO.Inicio iniciar(String idUsuario, String idCuenta, String remitenteSuscripcion,
                                                  List<ImportacionLoteGmailDTO.Mensaje> mensajes) {
        if (!esRemitenteImportable(remitenteSuscripcion)) {
            throw new IllegalArgumentException("El remitente no está autorizado para importar movimientos.");
        }
        List<ImportacionLoteGmailDTO.Mensaje> candidatos = mensajes == null ? List.of() : mensajes.stream()
                .filter(mensaje -> mensaje != null && esRemitenteImportable(mensaje.remitente()))
                .toList();
        String loteId = UUID.randomUUID().toString();
        Lote lote = new Lote(loteId, idUsuario, idCuenta, candidatos.size());
        lotes.put(loteId, lote);
        limpiarLotesExpirados();
        if (candidatos.isEmpty()) lote.terminado = true;
        else {
            try {
                executor.execute(() -> procesar(lote, candidatos));
            } catch (RuntimeException error) {
                lotes.remove(loteId, lote);
                throw error;
            }
        }
        return new ImportacionLoteGmailDTO.Inicio(loteId, candidatos.size());
    }

    public Optional<ImportacionLoteGmailDTO.Estado> consultar(String idUsuario, String idCuenta, String loteId) {
        Lote lote = lotes.get(loteId);
        if (lote == null || !lote.idUsuario.equals(idUsuario) || !lote.idCuenta.equals(idCuenta)) return Optional.empty();
        synchronized (lote) {
            List<ImportacionLoteGmailDTO.ResultadoMensaje> resultados = List.copyOf(lote.resultados);
            return Optional.of(new ImportacionLoteGmailDTO.Estado(lote.id, lote.total, lote.completados,
                    lote.terminado, contar(resultados, ESTADO_IMPORTADO), contar(resultados, ESTADO_POR_REVISAR),
                    contar(resultados, ESTADO_DUPLICADO), contar(resultados, ESTADO_ERROR), resultados));
        }
    }

    public static boolean esRemitenteImportable(String remitente) {
        return remitente != null && remitente.equalsIgnoreCase(REMITENTE_OFICIAL)
                && !REMITENTE_EXCLUIDO.equalsIgnoreCase(remitente);
    }

    private void procesar(Lote lote, List<ImportacionLoteGmailDTO.Mensaje> mensajesLote) {
        List<String> origenes = mensajesLote.stream().map(mensaje -> origenId(lote.idCuenta, mensaje.mensajeId())).toList();
        List<String> yaImportados;
        try {
            yaImportados = movimientos.correosImportados(lote.idUsuario, origenes);
        } catch (RuntimeException error) {
            mensajesLote.forEach(mensaje -> completar(lote, new ImportacionLoteGmailDTO.ResultadoMensaje(
                    mensaje.mensajeId(), mensaje.asunto(), ESTADO_ERROR, motivo(error))));
            terminar(lote);
            return;
        }
        java.util.Set<String> duplicados = java.util.Set.copyOf(yaImportados);
        for (ImportacionLoteGmailDTO.Mensaje mensaje : mensajesLote) {
            String origenId = origenId(lote.idCuenta, mensaje.mensajeId());
            if (duplicados.contains(origenId)) {
                completar(lote, resultado(mensaje, ESTADO_DUPLICADO, "Ya existe en Contabilidad."));
                continue;
            }
            if (mensaje.errorLectura() != null && !mensaje.errorLectura().isBlank()) {
                completar(lote, resultado(mensaje, ESTADO_ERROR, mensaje.errorLectura()));
                continue;
            }
            try {
                Optional<ParserMovimientoBancolombia.Movimiento> sugerencia =
                        parser.extraerDesdeRemitenteVerificado(mensaje.asunto(), mensaje.contenido());
                if (sugerencia.isEmpty()) {
                    completar(lote, resultado(mensaje, ESTADO_ERROR,
                            "No se pudo extraer una operación y un monto reconocibles."));
                    continue;
                }
                ParserMovimientoBancolombia.Movimiento movimiento = sugerencia.get();
                ResultadoClasificacion clasificacion = clasificador.clasificar(lote.idUsuario,
                        mensaje.remitente(), mensaje.asunto() + "\n" + mensaje.contenido());
                MovimientoContableDTO guardado = movimientos.crearAutomaticamenteDesdeGmail(lote.idUsuario,
                        movimiento.tipo(), mensaje.fecha(), movimiento.monto(), movimiento.categoria(),
                        movimiento.concepto(), origenId, mensaje.remitente(), clasificacion).orElseThrow();
                String estado = guardado.estadoRevision() == EstadoRevisionMovimiento.POR_REVISAR
                        ? ESTADO_POR_REVISAR : ESTADO_IMPORTADO;
                completar(lote, resultado(mensaje, estado,
                        estado.equals(ESTADO_POR_REVISAR) ? "Clasificación ambigua o bolsillo no único." : "Movimiento guardado."));
            } catch (RuntimeException error) {
                String detalle = motivo(error);
                completar(lote, detalle.toLowerCase(java.util.Locale.ROOT).contains("ya fue importado")
                        ? resultado(mensaje, ESTADO_DUPLICADO, "Ya existe en Contabilidad.")
                        : resultado(mensaje, ESTADO_ERROR, detalle));
            }
        }
        terminar(lote);
    }

    private static String origenId(String idCuenta, String idMensaje) {
        String origen = idCuenta + ":" + idMensaje;
        return origen.length() <= 180 ? origen : origen.substring(0, 180);
    }

    private static int contar(List<ImportacionLoteGmailDTO.ResultadoMensaje> resultados, String estado) {
        return (int) resultados.stream().filter(resultado -> estado.equals(resultado.estado())).count();
    }

    private static ImportacionLoteGmailDTO.ResultadoMensaje resultado(
            ImportacionLoteGmailDTO.Mensaje mensaje, String estado, String motivo) {
        return new ImportacionLoteGmailDTO.ResultadoMensaje(mensaje.mensajeId(), mensaje.asunto(), estado, motivo);
    }

    private static String motivo(RuntimeException error) {
        String mensaje = error.getMessage();
        return mensaje == null || mensaje.isBlank() ? error.getClass().getSimpleName() : mensaje;
    }

    private static void completar(Lote lote, ImportacionLoteGmailDTO.ResultadoMensaje resultado) {
        synchronized (lote) {
            lote.resultados.add(resultado);
            lote.completados++;
        }
    }

    private static void terminar(Lote lote) {
        synchronized (lote) {
            lote.resultados.sort(Comparator.comparing(ImportacionLoteGmailDTO.ResultadoMensaje::mensajeId));
            lote.terminado = true;
        }
    }

    private void limpiarLotesExpirados() {
        long corte = Instant.now().getEpochSecond() - RETENCION_LOTES_SEGUNDOS;
        lotes.entrySet().removeIf(entrada -> entrada.getValue().terminado
                && entrada.getValue().creadoEn.getEpochSecond() < corte);
    }

    private static final class Lote {
        private final String id;
        private final String idUsuario;
        private final String idCuenta;
        private final int total;
        private final Instant creadoEn = Instant.now();
        private final List<ImportacionLoteGmailDTO.ResultadoMensaje> resultados = new ArrayList<>();
        private volatile int completados;
        private volatile boolean terminado;

        private Lote(String id, String idUsuario, String idCuenta, int total) {
            this.id = id;
            this.idUsuario = idUsuario;
            this.idCuenta = idCuenta;
            this.total = total;
        }
    }
}
