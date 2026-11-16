package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;

/**
 * @uso               Entrega y renueva el token de acceso de una conexión de almacenamiento
 * @capa              Servicio
 * @responsabilidades Descifrar el token vigente; si caducó, renovarlo con refresh_token, re-cifrarlo y persistirlo;
 *                    si la renovación falla, marcar la conexión REQUIERE_RECONEXION
 * @datos             Tokens solo en memoria y cifrados en base de datos; nunca en logs ni respuestas
 * @dependencias      RestClient (OAuth2 de Google/Microsoft), IAlmacenamientoConexiones, SAlmacenamientoCifrado
 * @usadoPor          GoogleDriveArchivos, SAlmacenamientoConexiones
 */
@Service
public class SAlmacenamientoTokens {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAlmacenamientoTokens.class);
    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final long MARGEN_RENOVACION_SEGUNDOS = 60;

    private final IAlmacenamientoConexiones conexiones;
    private final SAlmacenamientoCifrado cifrado;
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient clienteHttp;

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientIdGoogle;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecretGoogle;

    public SAlmacenamientoTokens(IAlmacenamientoConexiones conexiones, SAlmacenamientoCifrado cifrado,
                                 RestClient.Builder constructorCliente) {
        this.conexiones = conexiones;
        this.cifrado = cifrado;
        this.clienteHttp = constructorCliente.build();
    }

    /** Devuelve un access token vigente; renueva con refresh_token si está por caducar. */
    public String tokenVigente(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        return tokenVigente(conexion, false);
    }

    /** Igual que tokenVigente, con forzarRenovacion=true descarta el token guardado y pide uno nuevo. */
    public String tokenVigente(MAlmacenamientoConexion conexion, boolean forzarRenovacion) throws GeneralSecurityException {
        if (conexion == null) throw new ConexionRequiereReconexionException("La conexión no existe.");
        boolean porCaducar = forzarRenovacion
                || conexion.getExpiraEn() == null
                || !conexion.getExpiraEn().isAfter(Instant.now().plusSeconds(MARGEN_RENOVACION_SEGUNDOS));
        if (!porCaducar && conexion.getAccessTokenCifrado() != null && !conexion.getAccessTokenCifrado().isBlank()) {
            return cifrado.descifrar(conexion.getAccessTokenCifrado());
        }
        String refreshToken = conexion.getRefreshTokenCifrado() == null || conexion.getRefreshTokenCifrado().isBlank()
                ? null : cifrado.descifrar(conexion.getRefreshTokenCifrado());
        if (refreshToken == null) {
            marcarReconexion(conexion, "La conexión no tiene un token de renovación.");
            throw new ConexionRequiereReconexionException("La conexión necesita reconexión con el proveedor.");
        }
        return renovar(conexion, refreshToken);
    }

    /** Renueva el access token, re-cifra el material y lo persiste en la conexión. */
    String renovar(MAlmacenamientoConexion conexion, String refreshToken) throws GeneralSecurityException {
        try {
            JsonNode respuesta = respuestaRenovacion(refreshToken);
            String accessToken = respuesta.path("access_token").asText("");
            if (accessToken.isBlank()) throw new IllegalStateException("El proveedor no devolvió un token de acceso.");
            long expiraEn = respuesta.path("expires_in").asLong(3600);
            conexion.setAccessTokenCifrado(cifrado.cifrar(accessToken));
            String nuevoRefresh = respuesta.path("refresh_token").asText("");
            if (!nuevoRefresh.isBlank()) conexion.setRefreshTokenCifrado(cifrado.cifrar(nuevoRefresh));
            conexion.setExpiraEn(Instant.now().plusSeconds(expiraEn));
            conexion.setActualizadoEn(Instant.now());
            conexiones.save(conexion);
            return accessToken;
        } catch (RestClientResponseException | IllegalStateException error) {
            LOGGER.warn("No se pudo renovar el token de la conexión {}: {}", conexion.getIdConexion(), error.getMessage());
            marcarReconexion(conexion, null);
            throw new ConexionRequiereReconexionException(
                    "No se pudo renovar el acceso al proveedor; reconecta la cuenta.", error);
        }
    }

    /** Marca la conexión como REQUIERE_RECONEXION y la persiste (visible en /conexiones para el frontend). */
    public void marcarReconexion(MAlmacenamientoConexion conexion, String mensaje) {
        conexion.setEstado("REQUIERE_RECONEXION");
        if (mensaje != null) LOGGER.warn("Conexión {} marcada REQUIERE_RECONEXION: {}", conexion.getIdConexion(), mensaje);
        conexiones.save(conexion);
    }

    private JsonNode respuestaRenovacion(String refreshToken) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("refresh_token", refreshToken);
        formulario.add("client_id", clientIdGoogle == null ? "" : clientIdGoogle.trim());
        formulario.add("client_secret", clientSecretGoogle == null ? "" : clientSecretGoogle.trim());
        formulario.add("grant_type", "refresh_token");
        String cuerpo = clienteHttp.post()
                .uri(java.net.URI.create(URL_TOKEN_GOOGLE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(String.class);
        try {
            return json.readTree(cuerpo == null ? "{}" : cuerpo);
        } catch (Exception error) {
            throw new IllegalStateException("Respuesta de renovación no válida.", error);
        }
    }

    /** Valida que el token recibido siga vigente tras una respuesta del proveedor (reuso del margen). */
    public Duration margenRenovacion() { return Duration.ofSeconds(MARGEN_RENOVACION_SEGUNDOS); }
}
