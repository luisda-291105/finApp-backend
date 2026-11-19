package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GmailListaBlancaDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraConfiguracionDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraMovimientoDTO;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
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
import org.springframework.http.MediaType;
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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * @uso               Lista la Papelera de una cuenta Gmail en vivo, mueve/restaura correos y gestiona
 *                    su configuración por categoría y su lista blanca
 * @capa              Servicio
 * @responsabilidades Consultar `messages.list?q=in:trash` con paginación, obtener los metadatos de
 *                    cada mensaje (`internalDate` → `eliminacion_estimada_en`, `labelIds`) con
 *                    backoff, combinar el marcador local del movimiento automático, mover/restaurar
 *                    con `messages.modify` actualizando el estado local (RF-01..RF-05, RF-13..RF-20)
 *                    y leer/actualizar la configuración por categoría y la lista blanca (RF-33,
 *                    RF-34, RF-36, RF-37)
 * @datos             Respuestas de la API de Gmail (formato metadata), filas de `gmail_mensaje`,
 *                    configuración/categorías de papelera y lista blanca
 * @dependencias      IGmailMensajes, SGmailMensajes (credenciales/cifrado), IFuentes,
 *                    IGmailPapeleraConfiguracion, IGmailPapeleraCategoria, IGmailPapeleraListaBlanca
 * @usadoPor          CPapeleraGmail
 */
@Service
public class SPapeleraGmail {

    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_MENSAJES_GMAIL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final int DURACION_TOKEN_SEGUNDOS_POR_DEFECTO = 3600;
    private static final int MARGEN_RENOVACION_SEGUNDOS = 60;
    private static final int INTENTOS_GMAIL = 6;
    private static final long PAUSA_BASE_REINTENTO_GMAIL_MS = 15000;
    /** Gmail purga de forma definitiva los correos de la papelera a los 30 días. */
    private static final int DIAS_PARA_PURGA = 30;
    private static final String CONSULTA_PAPELERA = "in:trash";
    private static final String ETIQUETA_PAPELERA = "TRASH";
    /** Estados locales del correo respecto a la Papelera (columna `papelera_estado`). */
    private static final String ESTADO_PAPELERA = "EN_PAPELERA";
    private static final String ESTADO_BANDEJA = "EN_BANDEJA";

    /** Categorías configurables, en el orden que expone el contrato (sección 4). */
    private static final List<String> CATEGORIAS = List.of(
            "CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL", "CATEGORY_FORUMS", "CATEGORY_UPDATES", "SPAM");
    private static final Set<String> CATEGORIAS_CONOCIDAS = Set.copyOf(CATEGORIAS);
    /** Categoría que exige aceptar la advertencia para activarse (RF-34). */
    private static final String CATEGORIA_UPDATES = "CATEGORY_UPDATES";
    private static final int LONGITUD_MAXIMA_REMITENTE = 320;
    /** Formato mínimo de una dirección de correo; acepta también `Nombre <correo@dominio>`. */
    private static final Pattern PATRON_REMITENTE = Pattern.compile("[^@\\s<>]+@[^@\\s<>]+\\.[^@\\s<>]+");

    private final IGmailMensajes mensajes;
    private final SGmailMensajes servicioMensajes;
    private final IFuentes fuentes;
    private final IGmailPapeleraConfiguracion configuraciones;
    private final IGmailPapeleraCategoria categorias;
    private final IGmailPapeleraListaBlanca listaBlanca;
    private final RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SPapeleraGmail(IGmailMensajes mensajes, SGmailMensajes servicioMensajes, IFuentes fuentes,
                          IGmailPapeleraConfiguracion configuraciones, IGmailPapeleraCategoria categorias,
                          IGmailPapeleraListaBlanca listaBlanca, RestClient.Builder constructorCliente) {
        this.mensajes = mensajes;
        this.servicioMensajes = servicioMensajes;
        this.fuentes = fuentes;
        this.configuraciones = configuraciones;
        this.categorias = categorias;
        this.listaBlanca = listaBlanca;
        this.clienteHttp = constructorCliente.build();
    }

    /**
     * Lista una página de la Papelera de la cuenta: solo los correos que Gmail tiene hoy en TRASH,
     * incluidos los nunca sincronizados. Los mensajes que ya no están en TRASH se omiten.
     */
    public GmailPapeleraDTO listar(MFuente cuenta, String pageToken) throws IOException, GeneralSecurityException {
        String token = tokenAccesoActual(cuenta);
        var builder = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .queryParam("q", CONSULTA_PAPELERA);
        if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
        JsonNode pagina = respuestaJsonConReintentos(builder.build().encode().toUri(), token);

        List<GmailPapeleraDTO.CorreoPapelera> correos = new ArrayList<>();
        JsonNode lista = pagina == null ? null : pagina.path("messages");
        if (lista != null && lista.isArray()) {
            for (JsonNode resumen : lista) {
                String mensajeId = resumen.path("id").asText("");
                if (mensajeId.isBlank()) continue;
                GmailPapeleraDTO.CorreoPapelera correo = correoDePapelera(cuenta, token, mensajeId);
                if (correo != null) correos.add(correo);
            }
        }
        String siguiente = pagina == null ? "" : pagina.path("nextPageToken").asText("");
        return new GmailPapeleraDTO(cuenta.getIdFuente(), correos, correos.size(),
                siguiente.isBlank() ? null : siguiente);
    }

    /** Obtiene los metadatos del mensaje y arma el correo de papelera; null si ya no está en TRASH. */
    private GmailPapeleraDTO.CorreoPapelera correoDePapelera(MFuente cuenta, String token, String mensajeId)
            throws IOException {
        URI uri = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .pathSegment(mensajeId).queryParam("format", "metadata").build().encode().toUri();
        JsonNode mensaje = respuestaJsonConReintentos(uri, token);
        if (mensaje == null || !etiquetasContienen(mensaje.path("labelIds"), ETIQUETA_PAPELERA)) return null;

        JsonNode headers = mensaje.path("payload").path("headers");
        long internalDate = mensaje.path("internalDate").asLong(0);
        Instant fecha = internalDate > 0 ? Instant.ofEpochMilli(internalDate) : null;
        MGmailMensaje local = mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId)
                .orElse(null);
        return new GmailPapeleraDTO.CorreoPapelera(
                mensajeId,
                header(headers, "From"),
                header(headers, "Subject"),
                fecha == null ? null : DateTimeFormatter.ISO_INSTANT.format(fecha),
                fecha == null ? null : DateTimeFormatter.ISO_INSTANT.format(fecha.plus(DIAS_PARA_PURGA, ChronoUnit.DAYS)),
                local != null && local.isPapeleraMovidoAutomaticamente(),
                local == null ? null : local.getPapeleraMotivo(),
                local == null ? null : local.getPapeleraCategoria());
    }

    /**
     * Mueve un correo a la Papelera de Gmail (`addLabelIds:[TRASH]`). Es idempotente: si el correo
     * ya está en TRASH responde `EN_PAPELERA` sin efectos adicionales. Al mover descarta de forma
     * persistente la alerta de movimiento pendiente (RF-28) sin tocar los movimientos contables (RF-27).
     */
    public GmailPapeleraMovimientoDTO mover(MFuente cuenta, String mensajeId)
            throws IOException, GeneralSecurityException {
        String token = tokenAccesoActual(cuenta);
        JsonNode mensaje = metadatosDeMensaje(cuenta, token, mensajeId);
        if (etiquetasContienen(mensaje.path("labelIds"), ETIQUETA_PAPELERA)) {
            return new GmailPapeleraMovimientoDTO(mensajeId, ESTADO_PAPELERA, false);
        }
        modificarEtiquetas(token, mensajeId, Map.of("addLabelIds", List.of(ETIQUETA_PAPELERA)));
        servicioMensajes.descartarAlertaMovimiento(cuenta, mensajeId);
        mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId).ifPresent(local -> {
            local.setPapeleraEstado(ESTADO_PAPELERA);
            local.setPapeleraMovidoAutomaticamente(false);
            local.setPapeleraMotivo(null);
            local.setPapeleraCategoria(null);
            mensajes.save(local);
        });
        return new GmailPapeleraMovimientoDTO(mensajeId, ESTADO_PAPELERA, false);
    }

    /**
     * Restaura un correo desde la Papelera de Gmail (`removeLabelIds:[TRASH]`) y lo marca exento de
     * la regla automática (RF-40). Es idempotente: si el correo ya salió de la Papelera responde
     * `EN_BANDEJA` sin efectos adicionales; si la reconciliación ya lo quitó de la vista sin que
     * FinApp lo hubiera restaurado, informa `mail_unavailable` (RF-20).
     */
    public GmailPapeleraMovimientoDTO restaurar(MFuente cuenta, String mensajeId)
            throws IOException, GeneralSecurityException {
        String token = tokenAccesoActual(cuenta);
        JsonNode mensaje = metadatosDeMensaje(cuenta, token, mensajeId);
        MGmailMensaje local = mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId)
                .orElse(null);
        if (!etiquetasContienen(mensaje.path("labelIds"), ETIQUETA_PAPELERA)) {
            if (local != null && ESTADO_BANDEJA.equals(local.getPapeleraEstado())) {
                return new GmailPapeleraMovimientoDTO(mensajeId, ESTADO_BANDEJA, null);
            }
            throw new CorreoNoDisponibleException();
        }
        modificarEtiquetas(token, mensajeId, Map.of("removeLabelIds", List.of(ETIQUETA_PAPELERA)));
        MGmailMensaje entidad = local != null ? local : nuevoMensajeLocal(cuenta, mensaje);
        entidad.setPapeleraEstado(ESTADO_BANDEJA);
        entidad.setPapeleraExenta(true);
        mensajes.save(entidad);
        return new GmailPapeleraMovimientoDTO(mensajeId, ESTADO_BANDEJA, null);
    }

    /**
     * Configuración de la regla automática de la cuenta (RF-33): las cinco categorías con su estado,
     * la antigüedad mínima, el límite por ejecución y si la cuenta tiene el permiso ampliado. Si la
     * cuenta todavía no tiene configuración, se crea de forma perezosa con los valores por defecto.
     */
    public GmailPapeleraConfiguracionDTO configuracion(MFuente cuenta) {
        MGmailPapeleraConfiguracion config = cargarConfiguracion(cuenta);
        return construirRespuesta(cuenta, config, categoriasPorNombre(cuenta));
    }

    /**
     * Actualiza las categorías activas, la antigüedad mínima, el límite por ejecución y la aceptación
     * de advertencias (RF-33/RF-34/RF-37). Activar una categoría registra su instante de activación
     * (para que la regla aplique solo a correos recibidos desde ese momento); desactivarla lo borra.
     */
    public GmailPapeleraConfiguracionDTO actualizarConfiguracion(MFuente cuenta,
            GmailPapeleraConfiguracionDTO.Solicitud solicitud) {
        GmailPapeleraConfiguracionDTO.Solicitud peticion = solicitud == null
                ? GmailPapeleraConfiguracionDTO.Solicitud.vacia() : solicitud;
        List<String> activas = peticion.categoriasActivas() == null ? List.of() : peticion.categoriasActivas();

        for (String categoria : activas) {
            if (!CATEGORIAS_CONOCIDAS.contains(categoria))
                throw new IllegalArgumentException("Categoría de Gmail no válida: " + categoria + ".");
        }
        if (peticion.antiguedadMinimaDias() != null && peticion.antiguedadMinimaDias() < 0)
            throw new IllegalArgumentException("La antigüedad mínima debe ser un entero mayor o igual que 0.");
        if (peticion.limitePorEjecucion() != null && peticion.limitePorEjecucion() <= 0)
            throw new IllegalArgumentException("El límite por ejecución debe ser un entero mayor que 0.");
        if (activas.contains(CATEGORIA_UPDATES) && !Boolean.TRUE.equals(peticion.advertenciaAceptada()))
            throw new IllegalArgumentException("Debes aceptar la advertencia para activar CATEGORY_UPDATES.");
        if (!activas.isEmpty() && !cuenta.tienePermisoModificarGmail()) throw new RequiereScopeException();

        MGmailPapeleraConfiguracion config = cargarConfiguracion(cuenta);
        if (peticion.antiguedadMinimaDias() != null) config.setAntiguedadMinimaDias(peticion.antiguedadMinimaDias());
        if (peticion.limitePorEjecucion() != null) config.setLimitePorEjecucion(peticion.limitePorEjecucion());
        configuraciones.save(config);

        Map<String, MGmailPapeleraCategoria> filas = categoriasPorNombre(cuenta);
        for (String categoria : CATEGORIAS) {
            MGmailPapeleraCategoria fila = filas.get(categoria);
            if (fila == null) {
                fila = new MGmailPapeleraCategoria(UUID.randomUUID().toString(), cuenta, categoria);
                filas.put(categoria, fila);
            }
            boolean activa = activas.contains(categoria);
            fila.setAdvertenciaAceptada(CATEGORIA_UPDATES.equals(categoria)
                    && activa && Boolean.TRUE.equals(peticion.advertenciaAceptada()));
            if (activa && !fila.isActiva()) fila.setActivadaEn(Instant.now());
            if (!activa) fila.setActivadaEn(null);
            fila.setActiva(activa);
            categorias.save(fila);
        }
        return construirRespuesta(cuenta, config, filas);
    }

    /** Remitentes exentos de la regla automática de la cuenta (RF-36). */
    public GmailListaBlancaDTO listarListaBlanca(MFuente cuenta) {
        List<GmailListaBlancaDTO.Remitente> remitentes =
                listaBlanca.findByFuente_IdFuenteOrderByRemitenteAsc(cuenta.getIdFuente()).stream()
                        .map(fila -> new GmailListaBlancaDTO.Remitente(fila.getId(), fila.getRemitente()))
                        .toList();
        return new GmailListaBlancaDTO(remitentes);
    }

    /**
     * Añade un remitente a la lista blanca de la cuenta (RF-36). Normaliza y valida la dirección;
     * si el remitente ya está exento devuelve la fila existente sin duplicarla.
     */
    public GmailListaBlancaDTO.Remitente agregarRemitente(MFuente cuenta, String remitente) {
        String normalizado = normalizarRemitente(remitente);
        Optional<MGmailPapeleraListaBlanca> existente =
                listaBlanca.findByFuente_IdFuenteAndRemitente(cuenta.getIdFuente(), normalizado);
        if (existente.isPresent()) {
            return new GmailListaBlancaDTO.Remitente(existente.get().getId(), existente.get().getRemitente());
        }
        MGmailPapeleraListaBlanca fila = listaBlanca.save(
                new MGmailPapeleraListaBlanca(UUID.randomUUID().toString(), cuenta, normalizado));
        return new GmailListaBlancaDTO.Remitente(fila.getId(), fila.getRemitente());
    }

    /** Quita un remitente de la lista blanca de la cuenta; `404` si no está en la lista (RF-36). */
    public void eliminarRemitente(MFuente cuenta, String remitenteId) {
        MGmailPapeleraListaBlanca fila = listaBlanca
                .findByIdAndFuente_IdFuente(remitenteId, cuenta.getIdFuente())
                .orElseThrow(RemitenteNoEncontradoException::new);
        listaBlanca.delete(fila);
    }

    /** Configuración de la cuenta o una nueva con los valores por defecto si aún no existe. */
    private MGmailPapeleraConfiguracion cargarConfiguracion(MFuente cuenta) {
        return configuraciones.findByFuente_IdFuente(cuenta.getIdFuente())
                .orElseGet(() -> configuraciones.save(new MGmailPapeleraConfiguracion(cuenta)));
    }

    /** Filas de categoría configuradas de la cuenta, indexadas por nombre de categoría. */
    private Map<String, MGmailPapeleraCategoria> categoriasPorNombre(MFuente cuenta) {
        Map<String, MGmailPapeleraCategoria> filas = new HashMap<>();
        for (MGmailPapeleraCategoria fila : categorias.findByFuente_IdFuente(cuenta.getIdFuente())) {
            filas.put(fila.getCategoria(), fila);
        }
        return filas;
    }

    /** Arma la respuesta de configuración a partir del estado ya cargado/mutado. */
    private GmailPapeleraConfiguracionDTO construirRespuesta(MFuente cuenta, MGmailPapeleraConfiguracion config,
            Map<String, MGmailPapeleraCategoria> filas) {
        List<GmailPapeleraConfiguracionDTO.CategoriaConfigurada> categoriasDto = new ArrayList<>();
        for (String categoria : CATEGORIAS) {
            MGmailPapeleraCategoria fila = filas.get(categoria);
            categoriasDto.add(new GmailPapeleraConfiguracionDTO.CategoriaConfigurada(
                    categoria,
                    fila != null && fila.isActiva(),
                    CATEGORIA_UPDATES.equals(categoria),
                    fila != null && fila.isAdvertenciaAceptada()));
        }
        return new GmailPapeleraConfiguracionDTO(categoriasDto, config.getAntiguedadMinimaDias(),
                config.getLimitePorEjecucion(), cuenta.tienePermisoModificarGmail());
    }

    /** Normaliza y valida una dirección de correo; lanza `IllegalArgumentException` si no es válida. */
    private static String normalizarRemitente(String remitente) {
        if (remitente == null) throw new IllegalArgumentException("El remitente es obligatorio.");
        String normalizado = remitente.trim();
        if (normalizado.isEmpty() || normalizado.length() > LONGITUD_MAXIMA_REMITENTE
                || !PATRON_REMITENTE.matcher(normalizado).find())
            throw new IllegalArgumentException("El remitente no es una dirección de correo válida.");
        return normalizado;
    }

    /**
     * Obtiene los metadatos del mensaje. Si Gmail responde `404` (o no devuelve el mensaje) distingue
     * el correo purgado (`410`, eliminando los datos locales, RF-19) del mensaje de otra cuenta (`404`).
     */
    private JsonNode metadatosDeMensaje(MFuente cuenta, String token, String mensajeId) throws IOException {
        URI uri = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .pathSegment(mensajeId).queryParam("format", "metadata").build().encode().toUri();
        try {
            JsonNode mensaje = respuestaJsonConReintentos(uri, token);
            if (mensaje != null && !mensaje.path("id").asText("").isBlank()) return mensaje;
        } catch (HttpStatusCodeException error) {
            if (error.getStatusCode().value() != 404) throw error;
        }
        throw correoInexistente(cuenta, mensajeId);
    }

    /** Excepción adecuada cuando Gmail no reconoce el mensaje para la cuenta indicada. */
    private RuntimeException correoInexistente(MFuente cuenta, String mensajeId) {
        boolean deOtraCuenta = mensajes
                .findFirstByGoogleMessageIdAndCuenta_Usuario_IdUsuario(mensajeId, cuenta.getUsuario().getIdUsuario())
                .filter(otro -> !cuenta.getIdFuente().equals(otro.getCuenta().getIdFuente()))
                .isPresent();
        if (deOtraCuenta) return new CorreoNoEncontradoException();
        mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
        return new CorreoPurgadoException();
    }

    /** Crea la fila local mínima de un correo que nunca se sincronizó, para poder marcarlo exento. */
    private MGmailMensaje nuevoMensajeLocal(MFuente cuenta, JsonNode mensaje) {
        long internalDate = mensaje.path("internalDate").asLong(0);
        Instant fecha = internalDate > 0 ? Instant.ofEpochMilli(internalDate) : Instant.now();
        MGmailMensaje entidad = new MGmailMensaje(UUID.randomUUID().toString(), cuenta,
                mensaje.path("id").asText(""), fecha);
        JsonNode headers = mensaje.path("payload").path("headers");
        entidad.setRemitente(header(headers, "From"));
        entidad.setAsunto(header(headers, "Subject").isBlank() ? "(Sin asunto)" : header(headers, "Subject"));
        entidad.setFragmento(mensaje.path("snippet").asText(""));
        return entidad;
    }

    /** Ejecuta `messages.modify` con las etiquetas indicadas, con reintentos ante cuota. */
    private void modificarEtiquetas(String token, String mensajeId, Map<String, List<String>> cambios) throws IOException {
        URI uri = UriComponentsBuilder.fromUriString(URL_MENSAJES_GMAIL)
                .pathSegment(mensajeId, "modify").build().encode().toUri();
        ejecutarConReintentos(() -> clienteHttp.post().uri(uri).headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(cambios));
    }

    private JsonNode respuestaJsonConReintentos(URI uri, String token) throws IOException {
        return ejecutarConReintentos(() -> clienteHttp.get().uri(uri).headers(h -> h.setBearerAuth(token)));
    }

    /** Reintenta con espera progresiva ante cuota de Gmail (`429`/`403`), como el resto del flujo. */
    private JsonNode ejecutarConReintentos(Supplier<RestClient.RequestHeadersSpec<?>> solicitud) throws IOException {
        for (int intento = 0; intento < INTENTOS_GMAIL; intento++) {
            try {
                return respuestaJson(solicitud.get(), JsonNode.class);
            } catch (HttpStatusCodeException error) {
                int estado = error.getStatusCode().value();
                if (estado != 429 && estado != 403) throw error;
                if (intento == INTENTOS_GMAIL - 1) throw error;
                dormir(PAUSA_BASE_REINTENTO_GMAIL_MS * (1L << intento));
            }
        }
        throw new RestClientException("No se pudo consultar la papelera de Gmail tras agotar los reintentos.");
    }

    private void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Consulta de la papelera de Gmail interrumpida.", error);
        }
    }

    private <T> T respuestaJson(RestClient.RequestHeadersSpec<?> solicitud, Class<T> tipo) throws IOException {
        String contenido = solicitud.retrieve().body(String.class);
        if (contenido == null || contenido.isBlank()) return null;
        return objectMapper.readValue(contenido, tipo);
    }

    /** Devuelve o renueva el token de la cuenta Gmail propia indicada. */
    private String tokenAccesoActual(MFuente cuenta) throws IOException, GeneralSecurityException {
        SGmailMensajes.CredencialesOAuth credenciales = servicioMensajes.resolverCredenciales();
        if (cuenta.getExpiraEn() != null && cuenta.getExpiraEn().isAfter(Instant.now().plusSeconds(MARGEN_RENOVACION_SEGUNDOS))
                && cuenta.getAccessTokenCifrado() != null && !cuenta.getAccessTokenCifrado().isBlank()) {
            return servicioMensajes.descifrar(cuenta.getAccessTokenCifrado(), credenciales.clientSecret());
        }
        if (cuenta.getRefreshTokenCifrado() == null || cuenta.getRefreshTokenCifrado().isBlank())
            throw new CuentaGmailRequiereReconectarException();
        GoogleOAuthTokenDTO renovacion = renovarToken(
                servicioMensajes.descifrar(cuenta.getRefreshTokenCifrado(), credenciales.clientSecret()), credenciales);
        String token = renovacion == null || renovacion.accessToken() == null ? "" : renovacion.accessToken().trim();
        if (token.isBlank()) throw new CuentaGmailRequiereReconectarException();
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

    /** La cuenta necesita reconectarse: no hay token vigente ni refresh token utilizable (RF-26). */
    public static class CuentaGmailRequiereReconectarException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
    }

    /** El correo ya fue eliminado definitivamente por Gmail (purgado): `410` (RF-18/RF-19). */
    public static class CorreoPurgadoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** El correo ya no está disponible en la vista Papelera: `409 mail_unavailable` (RF-20). */
    public static class CorreoNoDisponibleException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** El mensaje no pertenece a la cuenta indicada: `404`. */
    public static class CorreoNoEncontradoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** Activar categorías exige el permiso ampliado de Gmail: `409 requires_scope_authorization`. */
    public static class RequiereScopeException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
    }

    /** El remitente no está en la lista blanca de la cuenta: `404`. */
    public static class RemitenteNoEncontradoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
