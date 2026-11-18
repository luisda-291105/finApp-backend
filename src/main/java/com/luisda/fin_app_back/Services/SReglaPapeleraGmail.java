package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraEstadoDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraInicioDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraRespuestaDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MGmailPapeleraCategoria;
import com.luisda.fin_app_back.Model.MGmailPapeleraConfiguracion;
import com.luisda.fin_app_back.Model.MGmailPapeleraListaBlanca;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Repository.IGmailPapeleraCategoria;
import com.luisda.fin_app_back.Repository.IGmailPapeleraConfiguracion;
import com.luisda.fin_app_back.Repository.IGmailPapeleraListaBlanca;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @uso               Regla automática de papelera por categorías de Gmail y su tarea programada
 * @capa              Servicio
 * @responsabilidades Evaluar EN VIVO (`messages.list`) cada categoría activa de la cuenta, aplicar
 *                    las condiciones de elegibilidad (INBOX/SPAM, antigüedad mínima y activación),
 *                    TODAS las exclusiones de RF-35 y la lista blanca (RF-36), mover con
 *                    `messages.batchModify` en lotes ≤1000 con backoff respetando `limite_por_ejecucion`
 *                    (RF-40) y marcar cada correo movido con su motivo y categoría; el `@Scheduled`
 *                    configurable con bloqueo por cuenta evita solapes (CD-04)
 * @datos             Respuestas de la API de Gmail (list/metadata/threads/batchModify), configuración,
 *                    categorías y lista blanca de la papelera, filas de `gmail_mensaje`
 * @dependencias      IGmailPapeleraConfiguracion, IGmailPapeleraCategoria, IGmailPapeleraListaBlanca,
 *                    IGmailMensajes, SGmailMensajes (credenciales/cifrado/remitentes prioritarios),
 *                    SSuscripcionGmail, IFuentes
 * @usadoPor          Tarea programada de la papelera (y, en T7, vista previa y limpieza explícita)
 */
@Service
public class SReglaPapeleraGmail {

    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_MENSAJES_GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final String URL_HILOS_GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me/threads";
    private static final int DURACION_TOKEN_SEGUNDOS_POR_DEFECTO = 3600;
    private static final int MARGEN_RENOVACION_SEGUNDOS = 60;
    private static final int INTENTOS_GMAIL = 6;
    private static final long PAUSA_BASE_REINTENTO_GMAIL_MS = 15000;
    /** Límite documentado de Gmail por llamada a `batchModify`. */
    private static final int TAMANO_LOTE = 1000;

    /** Categorías evaluables por la vista previa y la limpieza explícita (sección 4 del contrato). */
    private static final Set<String> CATEGORIAS_CONOCIDAS = Set.of(
            "CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL", "CATEGORY_FORUMS", "CATEGORY_UPDATES", "SPAM");

    /** Motivos de exclusión en el orden del contrato (RF-35/RF-36). */
    private static final List<String> MOTIVOS_ORDEN = List.of(
            "STARRED", "IMPORTANT", "ETIQUETA_PERSONALIZADA", "HILO_CON_ENVIADOS", "REMITENTE_PRIORITARIO",
            "REMITENTE_SUSCRITO", "MOVIMIENTO_REGISTRADO", "ALERTA_PENDIENTE",
            "ESTADO_BOLSILLO_DUPLICADO_ERROR", "EXENTO", "LISTA_BLANCA");

    /** Motivo local del movimiento por limpieza explícita (RF-38). */
    private static final String MOTIVO_LIMPIEZA = "LIMPIEZA";
    private static final String ESTADO_LIMPIEZA_EN_PROCESO = "EN_PROCESO";
    private static final String ESTADO_LIMPIEZA_COMPLETADO = "COMPLETADO";
    private static final String ESTADO_LIMPIEZA_FALLIDO = "FALLIDO";

    private static final String ETIQUETA_PAPELERA = "TRASH";
    private static final String ETIQUETA_INBOX = "INBOX";
    private static final String ETIQUETA_SPAM = "SPAM";
    private static final String ETIQUETA_DESTACADO = "STARRED";
    private static final String ETIQUETA_IMPORTANTE = "IMPORTANT";
    private static final String ETIQUETA_ENVIADO = "SENT";
    private static final String CATEGORIA_SPAM = "SPAM";
    private static final String CATEGORIA_UPDATES = "CATEGORY_UPDATES";

    /** Estado local del correo tras el movimiento automático (columna `papelera_estado`). */
    private static final String ESTADO_PAPELERA = "EN_PAPELERA";
    /** Estados locales del correo que excluyen de la regla automática (RF-35). */
    private static final Set<String> ESTADOS_EXCLUYENTES =
            Set.of("PENDIENTE_BOLSILLO", "DUPLICADO", "ERROR");
    private static final String ESTADO_REGISTRADO = "REGISTRADO";

    /** Etiquetas del sistema de Gmail: cualquier etiqueta fuera de esta lista es personalizada. */
    private static final Set<String> ETIQUETAS_SISTEMA = Set.of(
            "INBOX", "SPAM", "TRASH", "UNREAD", "STARRED", "IMPORTANT", "SENT", "DRAFT", "CHAT",
            "CATEGORY_PERSONAL", "CATEGORY_SOCIAL", "CATEGORY_PROMOTIONS", "CATEGORY_UPDATES",
            "CATEGORY_FORUMS", "CATEGORY_PURCHASES", "CATEGORY_TRAVEL", "CATEGORY_FINANCE");

    /**
     * Refuerzo de la categoría de actualizaciones (RF-33): nunca se mueve un remitente bancario,
     * de recibos o de alertas financieras. Se compara contra la dirección extraída del `From`.
     */
    private static final List<String> PALABRAS_FINANCIERAS = List.of(
            "banco", "bank", "bancolombia", "davivienda", "colpatria", "bbva", "santander",
            "citibank", "itau", "scotiabank", "gnb", "avvillas", "bancoagrario", "bancamia",
            "nequi", "daviplata", "pse", "ach", "efecty", "factura", "facturacion", "recibo",
            "pago", "pagos", "cobro", "cobranza", "nomina", "extracto", "tarjeta", "credito",
            "debito", "hipotecario", "seguro", "aseguradora", "financiera", "inversion",
            "inversiones", "fiduciaria", "comisionista", "bolsa", "tributaria", "dian",
            "impuesto", "impuestos", "sarlaft");

    private static final Logger LOGGER = LoggerFactory.getLogger(SReglaPapeleraGmail.class);

    private final IGmailPapeleraConfiguracion configuraciones;
    private final IGmailPapeleraCategoria categorias;
    private final IGmailPapeleraListaBlanca listaBlanca;
    private final IGmailMensajes mensajes;
    private final SGmailMensajes servicioMensajes;
    private final SSuscripcionGmail suscripcionesGmail;
    private final IFuentes fuentes;
    private final SRegistroPapeleraGmail registro;
    private final Executor executor;
    private final RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Bloqueo en memoria por cuenta para no solapar dos ejecuciones de la regla (CD-04). */
    private final Set<String> cuentasEnEjecucion = ConcurrentHashMap.newKeySet();

    public SReglaPapeleraGmail(IGmailPapeleraConfiguracion configuraciones, IGmailPapeleraCategoria categorias,
                               IGmailPapeleraListaBlanca listaBlanca, IGmailMensajes mensajes,
                               SGmailMensajes servicioMensajes, SSuscripcionGmail suscripcionesGmail,
                               IFuentes fuentes, SRegistroPapeleraGmail registro,
                               @Qualifier("gmailImportExecutor") Executor executor,
                               RestClient.Builder constructorCliente) {
        this.configuraciones = configuraciones;
        this.categorias = categorias;
        this.listaBlanca = listaBlanca;
        this.mensajes = mensajes;
        this.servicioMensajes = servicioMensajes;
        this.suscripcionesGmail = suscripcionesGmail;
        this.fuentes = fuentes;
        this.registro = registro;
        this.executor = executor;
        this.clienteHttp = constructorCliente.build();
    }

    /** Resultado de una ejecución de la regla sobre una cuenta (RF-40). */
    public record ResultadoEjecucion(int movidos, boolean limiteEjecucionAlcanzado, int remanente) { }

    /** Candidato elegible para el movimiento automático, con la categoría que lo originó. */
    private record Candidato(String id, String categoria, JsonNode mensaje) { }

    /**
     * Tarea programada de la regla automática (CD-04). Configurable por propiedad; el bloqueo por
     * cuenta evita solapes entre ejecuciones y no procesa cuentas sin permiso ampliado.
     */
    @Scheduled(fixedDelayString = "${finapp.google.papelera-intervalo-ms:600000}", initialDelayString = "60000")
    public void procesarPapeleraProgramada() {
        ejecutarReglaPapelera();
    }

    /**
     * Recorre las cuentas Gmail con conexión y permiso ampliado y aplica la regla automática a cada
     * una, sin solapar dos ejecuciones de la misma cuenta. Devuelve cuántas cuentas se procesaron.
     */
    public int ejecutarReglaPapelera() {
        int procesadas = 0;
        for (MFuente cuenta : fuentes.findAll()) {
            if (!esCuentaGmail(cuenta) || !cuenta.tienePermisoModificarGmail() || !cuenta.tieneConexionGmail()) {
                continue;
            }
            if (!intentarBloquear(cuenta.getIdFuente())) continue;
            try {
                ejecutarCuenta(cuenta);
                procesadas++;
            } catch (Exception error) {
                LOGGER.warn("La regla de papelera falló para la cuenta {}", cuenta.getIdFuente(), error);
            } finally {
                liberar(cuenta.getIdFuente());
            }
        }
        return procesadas;
    }

    /**
     * Evalúa en vivo cada categoría activa de la cuenta y mueve a la papelera los correos que
     * cumplen la antigüedad, la activación de la categoría y todas las exclusiones (RF-33..RF-37).
     * Nunca mueve más de `limite_por_ejecucion` correos; el resto queda como remanente (RF-40).
     */
    public ResultadoEjecucion ejecutarCuenta(MFuente cuenta) throws IOException, GeneralSecurityException {
        if (cuenta == null || !cuenta.tienePermisoModificarGmail()) {
            return new ResultadoEjecucion(0, false, 0);
        }
        List<MGmailPapeleraCategoria> activas = categorias.findByFuente_IdFuente(cuenta.getIdFuente()).stream()
                .filter(MGmailPapeleraCategoria::isActiva)
                .toList();
        if (activas.isEmpty()) return new ResultadoEjecucion(0, false, 0);

        int antiguedad = antiguedadMinima(cuenta);
        int limite = limitePorEjecucion(cuenta);
        String token = tokenAccesoActual(cuenta);
        Set<String> remitentesExentos = remitentesListaBlanca(cuenta);

        List<Candidato> elegibles = new ArrayList<>();
        Set<String> idsVistos = new HashSet<>();
        for (MGmailPapeleraCategoria categoria : activas) {
            for (JsonNode item : listarMensajes(token, consultaCategoria(categoria.getCategoria(), antiguedad))) {
                String id = item.path("id").asText("");
                if (id.isBlank() || !idsVistos.add(id)) continue;
                String threadId = item.path("threadId").asText("");
                JsonNode mensaje = metadatosDeMensaje(token, id);
                if (mensaje == null) continue;
                MGmailMensaje local = mensajes
                        .findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), id).orElse(null);
                if (!esElegible(mensaje, categoria, antiguedad)) continue;
                if (motivoExclusion(cuenta, mensaje, threadId, categoria.getCategoria(), local,
                        remitentesExentos, token) != null) continue;
                elegibles.add(new Candidato(id, categoria.getCategoria(), mensaje));
            }
        }

        int movidos = 0;
        if (!elegibles.isEmpty()) {
            int aMover = Math.min(limite, elegibles.size());
            List<Candidato> seleccion = elegibles.subList(0, aMover);
            for (List<String> lote : partirEnLotes(seleccion.stream().map(Candidato::id).toList())) {
                batchModify(token, lote);
            }
            for (Candidato candidato : seleccion) {
                marcarMovido(cuenta, candidato);
                movidos++;
            }
        }
        int remanente = elegibles.size() - movidos;
        return new ResultadoEjecucion(movidos, remanente > 0, remanente);
    }

    /** Correos elegibles de la evaluación en vivo y conteo de excluidos por motivo (RF-35/RF-36). */
    private record ResultadoEvaluacion(List<SRegistroPapeleraGmail.CorreoElegible> elegibles,
                                       Map<String, Integer> excluidosPorMotivo) { }

    /**
     * Vista previa de la limpieza explícita (RF-39): evalúa en vivo las categorías solicitadas
     * aplicando TODAS las exclusiones (RF-35) y la lista blanca (RF-36), agrupa los elegibles por
     * categoría con sus remitentes y registra el `vista_previa_id` que fija los parámetros.
     */
    public VistaPreviaPapeleraRespuestaDTO vistaPrevia(MFuente cuenta, VistaPreviaPapeleraDTO solicitud)
            throws IOException, GeneralSecurityException {
        List<String> categoriasSolicitadas = solicitud == null || solicitud.categorias() == null
                ? List.of() : solicitud.categorias();
        int antiguedad = solicitud == null ? 0 : solicitud.antiguedadMinimaDias();
        ResultadoEvaluacion resultado = evaluarLimpieza(cuenta, categoriasSolicitadas, antiguedad);

        List<VistaPreviaPapeleraRespuestaDTO.CategoriaVistaPrevia> porCategoria =
                agruparPorCategoria(categoriasSolicitadas, resultado.elegibles());
        List<VistaPreviaPapeleraRespuestaDTO.ExclusionPorMotivo> excluidos = MOTIVOS_ORDEN.stream()
                .filter(motivo -> resultado.excluidosPorMotivo().getOrDefault(motivo, 0) > 0)
                .map(motivo -> new VistaPreviaPapeleraRespuestaDTO.ExclusionPorMotivo(
                        motivo, resultado.excluidosPorMotivo().get(motivo)))
                .toList();

        SRegistroPapeleraGmail.VistaPrevia vista = registro.registrarVistaPrevia(
                cuenta.getUsuario().getIdUsuario(), cuenta.getIdFuente(), categoriasSolicitadas, antiguedad,
                resultado.elegibles().size(), resultado.elegibles());
        return new VistaPreviaPapeleraRespuestaDTO(vista.id(), resultado.elegibles().size(), porCategoria, excluidos);
    }

    /**
     * Inicia la limpieza explícita de los correos ya recibidos (RF-37/RF-38). Exige un
     * `vista_previa_id` vigente, impone «una limpieza por cuenta» a la vez, recalcula el conteo en
     * vivo y, si difiere del confirmado, responde `409 conteo_cambiado` sin mover nada. Con el conteo
     * confirmado, lanza el movimiento en segundo plano y devuelve el lote.
     */
    public LimpiezaPapeleraInicioDTO iniciarLimpieza(MFuente cuenta, LimpiezaPapeleraDTO solicitud)
            throws IOException, GeneralSecurityException {
        String idUsuario = cuenta.getUsuario().getIdUsuario();
        String idCuenta = cuenta.getIdFuente();
        SRegistroPapeleraGmail.VistaPrevia vista = registro
                .obtenerVistaPrevia(solicitud.vistaPreviaId(), idUsuario, idCuenta)
                .orElseThrow(VistaPreviaNoEncontradaException::new);
        if (!registro.intentarBloquearLimpieza(idCuenta)) throw new LimpiezaEnCursoException();
        try {
            ResultadoEvaluacion resultado = evaluarLimpieza(cuenta, vista.categorias(), vista.antiguedadDias());
            int totalEnVivo = resultado.elegibles().size();
            int confirmado = solicitud.totalEstimado() == null ? -1 : solicitud.totalEstimado();
            if (totalEnVivo != confirmado) throw new ConteoCambiadoException(totalEnVivo);
            SRegistroPapeleraGmail.LoteLimpieza lote = registro.crearLote(idUsuario, idCuenta, totalEnVivo);
            executor.execute(() -> ejecutarLimpieza(cuenta, lote, resultado.elegibles()));
            return new LimpiezaPapeleraInicioDTO(lote.getId(), ESTADO_LIMPIEZA_EN_PROCESO);
        } catch (IOException | GeneralSecurityException | RuntimeException error) {
            registro.liberarLimpieza(idCuenta);
            throw error;
        }
    }

    /** Estado del lote de limpieza de la cuenta; vacío si el lote no existe o no es de la cuenta. */
    public Optional<LimpiezaPapeleraEstadoDTO> consultarLimpieza(MFuente cuenta, String loteId) {
        return registro.consultarLote(cuenta.getUsuario().getIdUsuario(), cuenta.getIdFuente(), loteId)
                .map(lote -> new LimpiezaPapeleraEstadoDTO(lote.getId(), lote.getEstado(), lote.getTotal(),
                        lote.getMovidos(), lote.getOmitidos(), lote.getFallidos(),
                        lote.isLimiteEjecucionAlcanzado()));
    }

    /** Evalúa en vivo las categorías aplicando las exclusiones y la lista blanca, sin activación. */
    private ResultadoEvaluacion evaluarLimpieza(MFuente cuenta, List<String> categoriasSolicitadas, int antiguedadDias)
            throws IOException, GeneralSecurityException {
        validarCategorias(categoriasSolicitadas);
        String token = tokenAccesoActual(cuenta);
        Set<String> remitentesExentos = remitentesListaBlanca(cuenta);
        List<SRegistroPapeleraGmail.CorreoElegible> elegibles = new ArrayList<>();
        Map<String, Integer> excluidos = new LinkedHashMap<>();
        Set<String> idsVistos = new HashSet<>();
        for (String categoria : categoriasSolicitadas) {
            for (JsonNode item : listarMensajes(token, consultaCategoria(categoria, antiguedadDias))) {
                String id = item.path("id").asText("");
                if (id.isBlank() || !idsVistos.add(id)) continue;
                String threadId = item.path("threadId").asText("");
                JsonNode mensaje = metadatosDeMensaje(token, id);
                if (mensaje == null) continue;
                if (!esElegiblePorDestinoYAntiguedad(mensaje, categoria, antiguedadDias)) continue;
                MGmailMensaje local = mensajes
                        .findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), id).orElse(null);
                String motivo = motivoExclusion(cuenta, mensaje, threadId, categoria, local, remitentesExentos, token);
                if (motivo != null) {
                    excluidos.merge(motivo, 1, Integer::sum);
                    continue;
                }
                JsonNode headers = mensaje.path("payload").path("headers");
                long internalDate = mensaje.path("internalDate").asLong(0);
                Instant fecha = internalDate > 0 ? Instant.ofEpochMilli(internalDate) : Instant.now();
                elegibles.add(new SRegistroPapeleraGmail.CorreoElegible(id, categoria,
                        SGmailMensajes.extraerDireccionRemitente(header(headers, "From")),
                        header(headers, "Subject"), mensaje.path("snippet").asText(""), fecha));
            }
        }
        return new ResultadoEvaluacion(elegibles, excluidos);
    }

    /** Agrupa los elegibles por categoría (en el orden solicitado) con sus remitentes y cantidades. */
    private static List<VistaPreviaPapeleraRespuestaDTO.CategoriaVistaPrevia> agruparPorCategoria(
            List<String> categorias, List<SRegistroPapeleraGmail.CorreoElegible> elegibles) {
        List<VistaPreviaPapeleraRespuestaDTO.CategoriaVistaPrevia> resultado = new ArrayList<>();
        for (String categoria : categorias) {
            List<SRegistroPapeleraGmail.CorreoElegible> deCategoria = elegibles.stream()
                    .filter(correo -> correo.categoria().equals(categoria)).toList();
            if (deCategoria.isEmpty()) continue;
            Map<String, Integer> porRemitente = new LinkedHashMap<>();
            for (SRegistroPapeleraGmail.CorreoElegible correo : deCategoria) {
                String remitente = correo.remitente() == null || correo.remitente().isBlank() ? "" : correo.remitente();
                porRemitente.merge(remitente, 1, Integer::sum);
            }
            List<VistaPreviaPapeleraRespuestaDTO.RemitenteVistaPrevia> remitentes = porRemitente.entrySet().stream()
                    .map(entrada -> new VistaPreviaPapeleraRespuestaDTO.RemitenteVistaPrevia(
                            entrada.getKey(), entrada.getValue()))
                    .toList();
            resultado.add(new VistaPreviaPapeleraRespuestaDTO.CategoriaVistaPrevia(
                    categoria, deCategoria.size(), remitentes));
        }
        return resultado;
    }

    /** Valida que las categorías solicitadas sean configurables; `400` si alguna no lo es. */
    private static void validarCategorias(List<String> categorias) {
        for (String categoria : categorias) {
            if (categoria == null || !CATEGORIAS_CONOCIDAS.contains(categoria))
                throw new IllegalArgumentException("Categoría de Gmail no válida: " + categoria + ".");
        }
    }

    /**
     * Mueve los correos elegibles en segundo plano con `batchModify` en lotes ≤ `tamanoLote()`,
     * respetando `limite_por_ejecucion` (el resto queda como remanente) y marcando cada movido con
     * motivo `LIMPIEZA`. Un fallo parcial deja los movidos en la papelera y los fallidos reintentables
     * sin duplicar movimientos (CD-04). Siempre libera el bloqueo de la cuenta al terminar.
     */
    private void ejecutarLimpieza(MFuente cuenta, SRegistroPapeleraGmail.LoteLimpieza lote,
                                  List<SRegistroPapeleraGmail.CorreoElegible> elegibles) {
        try {
            int limite = limitePorEjecucion(cuenta);
            int aMover = Math.min(limite, elegibles.size());
            List<SRegistroPapeleraGmail.CorreoElegible> seleccion = elegibles.subList(0, aMover);
            int omitidos = elegibles.size() - aMover;
            lote.setOmitidos(omitidos);
            lote.setLimiteEjecucionAlcanzado(omitidos > 0);

            String token = tokenAccesoActual(cuenta);
            int movidos = 0;
            int fallidos = 0;
            for (List<SRegistroPapeleraGmail.CorreoElegible> bloque : partirCorreos(seleccion, tamanoLote())) {
                try {
                    batchModify(token, bloque.stream().map(SRegistroPapeleraGmail.CorreoElegible::id).toList());
                    for (SRegistroPapeleraGmail.CorreoElegible correo : bloque) marcarMovidoLimpieza(cuenta, correo);
                    movidos += bloque.size();
                } catch (Exception error) {
                    fallidos += bloque.size();
                    LOGGER.warn("Falló un lote de la limpieza de papelera de la cuenta {}", cuenta.getIdFuente(), error);
                }
            }
            lote.setMovidos(movidos);
            lote.setFallidos(fallidos);
            lote.setEstado(ESTADO_LIMPIEZA_COMPLETADO);
        } catch (Exception error) {
            LOGGER.warn("La limpieza de papelera falló para la cuenta {}", cuenta.getIdFuente(), error);
            lote.setEstado(ESTADO_LIMPIEZA_FALLIDO);
        } finally {
            registro.liberarLimpieza(cuenta.getIdFuente());
        }
    }

    /** Marca localmente el correo movido por la limpieza, creando la fila mínima si no existía. */
    private void marcarMovidoLimpieza(MFuente cuenta, SRegistroPapeleraGmail.CorreoElegible correo) {
        MGmailMensaje entidad = mensajes
                .findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), correo.id())
                .orElseGet(() -> {
                    MGmailMensaje nueva = new MGmailMensaje(UUID.randomUUID().toString(), cuenta, correo.id(),
                            correo.fechaInterna() == null ? Instant.now() : correo.fechaInterna());
                    nueva.setRemitente(correo.remitente());
                    nueva.setAsunto(correo.asunto() == null || correo.asunto().isBlank()
                            ? "(Sin asunto)" : correo.asunto());
                    nueva.setFragmento(correo.fragmento());
                    return nueva;
                });
        entidad.setPapeleraEstado(ESTADO_PAPELERA);
        entidad.setPapeleraMovidoAutomaticamente(true);
        entidad.setPapeleraMotivo(MOTIVO_LIMPIEZA);
        entidad.setPapeleraCategoria(correo.categoria());
        mensajes.save(entidad);
    }

    /** Divide una lista en bloques de a lo sumo `tamano` elementos, sin modificar la original. */
    private static <T> List<List<T>> partirCorreos(List<T> elementos, int tamano) {
        List<List<T>> bloques = new ArrayList<>();
        for (int i = 0; i < elementos.size(); i += tamano) {
            bloques.add(new ArrayList<>(elementos.subList(i, Math.min(i + tamano, elementos.size()))));
        }
        return bloques;
    }

    /** Tamaño máximo de cada `batchModify` de la limpieza; sobrescribible en pruebas. */
    protected int tamanoLote() {
        return TAMANO_LOTE;
    }

    /** Cuenta Gmail propia (tipo CORREO) apta para la regla. */
    private static boolean esCuentaGmail(MFuente cuenta) {
        return cuenta != null && "CORREO".equalsIgnoreCase(cuenta.getTipo());
    }

    /** Consulta de Gmail de una categoría activa, restringida a correos que superan la antigüedad. */
    private static String consultaCategoria(String categoria, int antiguedadDias) {
        String olderThan = "older_than:" + antiguedadDias + "d";
        if (CATEGORIA_SPAM.equals(categoria)) return "in:spam -in:trash " + olderThan;
        return "category:" + categoria + " in:inbox -in:trash " + olderThan;
    }

    /** Antigüedad mínima configurada de la cuenta, o el valor por defecto si aún no hay configuración. */
    private int antiguedadMinima(MFuente cuenta) {
        return configuraciones.findByFuente_IdFuente(cuenta.getIdFuente())
                .map(MGmailPapeleraConfiguracion::getAntiguedadMinimaDias)
                .orElse(MGmailPapeleraConfiguracion.ANTIGUEDAD_MINIMA_DIAS_POR_DEFECTO);
    }

    /** Límite de correos por ejecución configurado de la cuenta, o el valor por defecto. */
    private int limitePorEjecucion(MFuente cuenta) {
        return configuraciones.findByFuente_IdFuente(cuenta.getIdFuente())
                .map(MGmailPapeleraConfiguracion::getLimitePorEjecucion)
                .orElse(MGmailPapeleraConfiguracion.LIMITE_POR_EJECUCION_POR_DEFECTO);
    }

    /** Remitentes de la lista blanca de la cuenta (RF-36), normalizados en minúsculas. */
    private Set<String> remitentesListaBlanca(MFuente cuenta) {
        return listaBlanca.findByFuente_IdFuenteOrderByRemitenteAsc(cuenta.getIdFuente()).stream()
                .map(fila -> fila.getRemitente() == null ? "" : fila.getRemitente().toLowerCase(Locale.ROOT))
                .filter(remitente -> !remitente.isBlank())
                .collect(Collectors.toSet());
    }

    /** Lista los ids de Gmail que cumplen la consulta, con paginación por `pageToken`. */
    private List<JsonNode> listarMensajes(String token, String consulta) throws IOException {
        List<JsonNode> encontrados = new ArrayList<>();
        String pageToken = null;
        do {
            var builder = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL).queryParam("q", consulta);
            if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
            JsonNode pagina = respuestaJsonConReintentos(builder.build().encode().toUri(), token);
            JsonNode lista = pagina == null ? null : pagina.path("messages");
            if (lista != null && lista.isArray()) for (JsonNode item : lista) encontrados.add(item);
            pageToken = pagina == null ? null : pagina.path("nextPageToken").asText("");
        } while (pageToken != null && !pageToken.isBlank());
        return encontrados;
    }

    /** Metadatos del mensaje (`format=metadata`); null si Gmail ya no lo reconoce para la cuenta. */
    private JsonNode metadatosDeMensaje(String token, String mensajeId) throws IOException {
        URI uri = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .pathSegment(mensajeId).queryParam("format", "metadata").build().encode().toUri();
        try {
            JsonNode mensaje = respuestaJsonConReintentos(uri, token);
            if (mensaje != null && !mensaje.path("id").asText("").isBlank()) return mensaje;
            return null;
        } catch (HttpStatusCodeException error) {
            if (error.getStatusCode().value() == 404) return null;
            throw error;
        }
    }

    /**
     * Elegibilidad de un correo para el movimiento automático: debe seguir en INBOX (o en SPAM, si
     * la categoría es SPAM), no estar ya en la papelera, superar la antigüedad mínima y haber
     * llegado después de la activación de la categoría (RF-33/RF-34/RF-37).
     */
    private static boolean esElegible(JsonNode mensaje, MGmailPapeleraCategoria categoria, int antiguedadDias) {
        if (!esElegiblePorDestinoYAntiguedad(mensaje, categoria.getCategoria(), antiguedadDias)) return false;
        Instant activacion = categoria.getActivadaEn();
        if (activacion == null) return false;
        return !Instant.ofEpochMilli(mensaje.path("internalDate").asLong(0)).isBefore(activacion);
    }

    /**
     * Elegibilidad por destino y antigüedad, sin la compuerta de activación: debe seguir en INBOX (o
     * en SPAM, si la categoría es SPAM), no estar ya en la papelera y superar la antigüedad mínima.
     * La usa la limpieza explícita, que actúa sobre correos ya recibidos (RF-37/RF-38).
     */
    private static boolean esElegiblePorDestinoYAntiguedad(JsonNode mensaje, String categoria, int antiguedadDias) {
        JsonNode etiquetas = mensaje.path("labelIds");
        if (etiquetasContienen(etiquetas, ETIQUETA_PAPELERA)) return false;
        boolean enDestino = CATEGORIA_SPAM.equals(categoria)
                ? etiquetasContienen(etiquetas, ETIQUETA_SPAM)
                : etiquetasContienen(etiquetas, ETIQUETA_INBOX);
        if (!enDestino) return false;
        long internalDate = mensaje.path("internalDate").asLong(0);
        if (internalDate <= 0) return false;
        Instant fecha = Instant.ofEpochMilli(internalDate);
        return fecha.isBefore(Instant.now().minus(Duration.ofDays(antiguedadDias)));
    }

    /**
     * Motivo de exclusión del correo (RF-35/RF-36) en el orden del contrato, o null si es elegible.
     * El refuerzo de UPDATES (RF-33) excluye a los remitentes bancarios, de recibos o financieros.
     */
    private String motivoExclusion(MFuente cuenta, JsonNode mensaje, String threadId, String categoria,
                                   MGmailMensaje local, Set<String> remitentesExentos, String token) throws IOException {
        JsonNode etiquetas = mensaje.path("labelIds");
        if (etiquetasContienen(etiquetas, ETIQUETA_DESTACADO)) return "STARRED";
        if (etiquetasContienen(etiquetas, ETIQUETA_IMPORTANTE)) return "IMPORTANT";
        if (tieneEtiquetaPersonalizada(etiquetas)) return "ETIQUETA_PERSONALIZADA";
        if (hiloConEnviados(token, threadId)) return "HILO_CON_ENVIADOS";
        String remitente = SGmailMensajes.extraerDireccionRemitente(header(mensaje.path("payload").path("headers"), "From"));
        if (esRemitentePrioritario(remitente)
                || (CATEGORIA_UPDATES.equals(categoria) && esRemitenteFinanciero(remitente))) return "REMITENTE_PRIORITARIO";
        if (!remitente.isBlank() && suscripcionesGmail.existeEnCuenta(cuenta.getIdFuente(), remitente))
            return "REMITENTE_SUSCRITO";
        if (local != null) {
            if (ESTADO_REGISTRADO.equals(local.getMovimientoAutomaticoEstado()) || local.getMovimientoAutomaticoId() != null)
                return "MOVIMIENTO_REGISTRADO";
            if (local.isMovimientoAlertaPendiente()) return "ALERTA_PENDIENTE";
            if (ESTADOS_EXCLUYENTES.contains(local.getMovimientoAutomaticoEstado()))
                return "ESTADO_BOLSILLO_DUPLICADO_ERROR";
            if (local.isPapeleraExenta()) return "EXENTO";
        }
        if (!remitente.isBlank() && remitentesExentos.contains(remitente)) return "LISTA_BLANCA";
        return null;
    }

    /** Un remitente prioritario nunca se mueve por la regla automática. */
    private static boolean esRemitentePrioritario(String remitente) {
        return remitente != null && !remitente.isBlank()
                && SGmailMensajes.REMITENTES_PRIORITARIOS.contains(remitente);
    }

    /** Refuerzo de UPDATES: remitentes bancarios, de recibos o de alertas financieras. */
    private static boolean esRemitenteFinanciero(String remitente) {
        if (remitente == null || remitente.isBlank()) return false;
        for (String palabra : PALABRAS_FINANCIERAS) if (remitente.contains(palabra)) return true;
        return false;
    }

    /** Etiqueta personalizada del usuario: cualquiera fuera del conjunto de etiquetas del sistema. */
    private static boolean tieneEtiquetaPersonalizada(JsonNode etiquetas) {
        if (etiquetas == null || !etiquetas.isArray()) return false;
        for (JsonNode item : etiquetas) if (!ETIQUETAS_SISTEMA.contains(item.asText())) return true;
        return false;
    }

    /** Detecta si el hilo del mensaje contiene algún mensaje enviado (RF-35). */
    private boolean hiloConEnviados(String token, String threadId) throws IOException {
        if (threadId == null || threadId.isBlank()) return false;
        URI uri = UriComponentsBuilder.fromUriString(URL_HILOS_GMAIL)
                .pathSegment(threadId).queryParam("format", "metadata").build().encode().toUri();
        JsonNode hilo;
        try {
            hilo = respuestaJsonConReintentos(uri, token);
        } catch (HttpStatusCodeException error) {
            if (error.getStatusCode().value() == 404) return false;
            throw error;
        }
        JsonNode mensajesHilo = hilo == null ? null : hilo.path("messages");
        if (mensajesHilo != null && mensajesHilo.isArray()) {
            for (JsonNode mensajeHilo : mensajesHilo) {
                if (etiquetasContienen(mensajeHilo.path("labelIds"), ETIQUETA_ENVIADO)) return true;
            }
        }
        return false;
    }

    /** Mueve a la papelera un lote de ids (≤1000) con `batchModify` y reintentos ante cuota. */
    private void batchModify(String token, List<String> ids) throws IOException {
        URI uri = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .pathSegment("batchModify").build().encode().toUri();
        Map<String, Object> cuerpo = Map.of("ids", ids, "addLabelIds", List.of(ETIQUETA_PAPELERA));
        ejecutarSinRespuesta(() -> clienteHttp.post().uri(uri).headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(cuerpo));
    }

    /** Marca localmente el correo movido por la regla, creando la fila mínima si no existía (RF-40). */
    private void marcarMovido(MFuente cuenta, Candidato candidato) {
        MGmailMensaje entidad = mensajes
                .findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), candidato.id())
                .orElseGet(() -> nuevoMensajeLocal(cuenta, candidato.mensaje()));
        entidad.setPapeleraEstado(ESTADO_PAPELERA);
        entidad.setPapeleraMovidoAutomaticamente(true);
        entidad.setPapeleraMotivo(candidato.categoria());
        entidad.setPapeleraCategoria(candidato.categoria());
        mensajes.save(entidad);
    }

    /** Crea la fila local mínima de un correo que nunca se sincronizó, para poder marcarlo. */
    private MGmailMensaje nuevoMensajeLocal(MFuente cuenta, JsonNode mensaje) {
        long internalDate = mensaje.path("internalDate").asLong(0);
        Instant fecha = internalDate > 0 ? Instant.ofEpochMilli(internalDate) : Instant.now();
        MGmailMensaje entidad = new MGmailMensaje(UUID.randomUUID().toString(), cuenta,
                mensaje.path("id").asText(""), fecha);
        JsonNode headers = mensaje.path("payload").path("headers");
        entidad.setRemitente(header(headers, "From"));
        String asunto = header(headers, "Subject");
        entidad.setAsunto(asunto.isBlank() ? "(Sin asunto)" : asunto);
        entidad.setFragmento(mensaje.path("snippet").asText(""));
        return entidad;
    }

    /** Divide una lista de ids en lotes de a lo sumo 1000 elementos (límite de `batchModify`). */
    static List<List<String>> partirEnLotes(List<String> ids) {
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += TAMANO_LOTE) {
            lotes.add(new ArrayList<>(ids.subList(i, Math.min(i + TAMANO_LOTE, ids.size()))));
        }
        return lotes;
    }

    /** Intenta tomar el bloqueo de la cuenta; false si ya hay una ejecución en curso (CD-04). */
    boolean intentarBloquear(String idFuente) {
        return cuentasEnEjecucion.add(idFuente);
    }

    /** Libera el bloqueo de la cuenta al terminar la ejecución. */
    void liberar(String idFuente) {
        cuentasEnEjecucion.remove(idFuente);
    }

    /** GET con reintentos ante cuota/errores del proveedor, devolviendo el JSON de la respuesta. */
    private JsonNode respuestaJsonConReintentos(URI uri, String token) throws IOException {
        for (int intento = 0; intento < INTENTOS_GMAIL; intento++) {
            try {
                return respuestaJson(clienteHttp.get().uri(uri).headers(h -> h.setBearerAuth(token)), JsonNode.class);
            } catch (HttpStatusCodeException error) {
                if (!esReintentable(error.getStatusCode().value())) throw error;
                if (intento == INTENTOS_GMAIL - 1) throw error;
                dormir(PAUSA_BASE_REINTENTO_GMAIL_MS * (1L << intento));
            }
        }
        throw new RestClientException("No se pudo consultar Gmail tras agotar los reintentos.");
    }

    /** Petición con reintentos que no necesita el cuerpo de la respuesta (p. ej. `batchModify`). */
    private void ejecutarSinRespuesta(Supplier<RestClient.RequestHeadersSpec<?>> solicitud) throws IOException {
        for (int intento = 0; intento < INTENTOS_GMAIL; intento++) {
            try {
                solicitud.get().retrieve().toBodilessEntity();
                return;
            } catch (HttpStatusCodeException error) {
                if (!esReintentable(error.getStatusCode().value())) throw error;
                if (intento == INTENTOS_GMAIL - 1) throw error;
                dormir(PAUSA_BASE_REINTENTO_GMAIL_MS * (1L << intento));
            }
        }
    }

    /** Estados de Gmail ante los que se reintenta con backoff (cuota y errores del proveedor). */
    private static boolean esReintentable(int estado) {
        return estado == 429 || estado == 403 || (estado >= 500 && estado <= 599);
    }

    /** Espera entre reintentos; sobrescribible en pruebas para no dormir de verdad. */
    protected void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Regla de papelera interrumpida.", error);
        }
    }

    private <T> T respuestaJson(RestClient.RequestHeadersSpec<?> solicitud, Class<T> tipo) throws IOException {
        String contenido = solicitud.retrieve().body(String.class);
        if (contenido == null || contenido.isBlank()) return null;
        return objectMapper.readValue(contenido, tipo);
    }

    /** Devuelve o renueva el token de la cuenta Gmail indicada. */
    private String tokenAccesoActual(MFuente cuenta) throws IOException, GeneralSecurityException {
        SGmailMensajes.CredencialesOAuth credenciales = servicioMensajes.resolverCredenciales();
        if (cuenta.getExpiraEn() != null && cuenta.getExpiraEn().isAfter(Instant.now().plusSeconds(MARGEN_RENOVACION_SEGUNDOS))
                && cuenta.getAccessTokenCifrado() != null && !cuenta.getAccessTokenCifrado().isBlank()) {
            return servicioMensajes.descifrar(cuenta.getAccessTokenCifrado(), credenciales.clientSecret());
        }
        if (cuenta.getRefreshTokenCifrado() == null || cuenta.getRefreshTokenCifrado().isBlank()) {
            throw new SPapeleraGmail.CuentaGmailRequiereReconectarException();
        }
        GoogleOAuthTokenDTO renovacion = renovarToken(
                servicioMensajes.descifrar(cuenta.getRefreshTokenCifrado(), credenciales.clientSecret()), credenciales);
        String token = renovacion == null || renovacion.accessToken() == null ? "" : renovacion.accessToken().trim();
        if (token.isBlank()) throw new SPapeleraGmail.CuentaGmailRequiereReconectarException();
        long duracion = renovacion.expiresIn() == null || renovacion.expiresIn() <= 0
                ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : renovacion.expiresIn();
        cuenta.setAccessTokenCifrado(servicioMensajes.cifrar(token, credenciales.clientSecret()));
        cuenta.setExpiraEn(Instant.now().plusSeconds(duracion));
        fuentes.save(cuenta);
        return token;
    }

    private GoogleOAuthTokenDTO renovarToken(String refreshToken, SGmailMensajes.CredencialesOAuth credenciales)
            throws IOException {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", credenciales.clientId());
        formulario.add("client_secret", credenciales.clientSecret());
        formulario.add("refresh_token", refreshToken);
        formulario.add("grant_type", "refresh_token");
        return respuestaJson(clienteHttp.post().uri(URL_TOKEN_GOOGLE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario), GoogleOAuthTokenDTO.class);
    }

    private static boolean etiquetasContienen(JsonNode etiquetas, String etiqueta) {
        if (etiquetas == null || !etiquetas.isArray()) return false;
        for (JsonNode item : etiquetas) if (etiqueta.equals(item.asText())) return true;
        return false;
    }

    private static String header(JsonNode headers, String nombre) {
        if (headers == null || !headers.isArray()) return "";
        for (JsonNode header : headers) {
            if (nombre.equalsIgnoreCase(header.path("name").asText())) return header.path("value").asText("");
        }
        return "";
    }

    /** El `vista_previa_id` no existe o expiró: `409` (RF-39). */
    public static class VistaPreviaNoEncontradaException extends RuntimeException { }

    /** Ya hay una limpieza en curso para la cuenta: `409 limpieza_en_curso` (RF-38). */
    public static class LimpiezaEnCursoException extends IllegalStateException { }

    /** El conteo real difiere del confirmado: `409 conteo_cambiado` con el nuevo conteo (RF-38). */
    public static class ConteoCambiadoException extends IllegalStateException {
        private final int nuevoConteo;

        public ConteoCambiadoException(int nuevoConteo) {
            this.nuevoConteo = nuevoConteo;
        }

        public int getNuevoConteo() { return nuevoConteo; }
    }
}
