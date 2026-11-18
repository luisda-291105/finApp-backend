package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GmailPapeleraDTO;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
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
import java.util.List;

/**
 * @uso               Lista la Papelera de una cuenta Gmail en vivo desde el TRASH de Gmail
 * @capa              Servicio
 * @responsabilidades Consultar `messages.list?q=in:trash` con paginación, obtener los metadatos de
 *                    cada mensaje (`internalDate` → `eliminacion_estimada_en`, `labelIds`) con
 *                    backoff y combinar el marcador local del movimiento automático
 * @datos             Respuestas de la API de Gmail (formato metadata) y filas de `gmail_mensaje`
 * @dependencias      IGmailMensajes, SGmailMensajes (credenciales/cifrado), IFuentes
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

    private final IGmailMensajes mensajes;
    private final SGmailMensajes servicioMensajes;
    private final IFuentes fuentes;
    private final RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SPapeleraGmail(IGmailMensajes mensajes, SGmailMensajes servicioMensajes, IFuentes fuentes,
                          RestClient.Builder constructorCliente) {
        this.mensajes = mensajes;
        this.servicioMensajes = servicioMensajes;
        this.fuentes = fuentes;
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

    private JsonNode respuestaJsonConReintentos(URI uri, String token) throws IOException {
        for (int intento = 0; intento < INTENTOS_GMAIL; intento++) {
            try {
                return respuestaJson(clienteHttp.get().uri(uri).headers(h -> h.setBearerAuth(token)), JsonNode.class);
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
    public static class CuentaGmailRequiereReconectarException extends IllegalStateException { }
}
