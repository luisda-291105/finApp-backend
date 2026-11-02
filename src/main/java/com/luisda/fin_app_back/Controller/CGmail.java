package com.luisda.fin_app_back.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Completa el canje del código OAuth de Gmail recibido por el flujo GIS popup. */
@RestController
@RequestMapping("/api/gmail")
public class CGmail {

    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";

    private final RestClient clienteHttp = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}")
    private String clientId;

    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:../client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}")
    private String archivoCredenciales;

    @Value("${finapp.cors.allowed-origins:http://localhost:5173}")
    private String origenesPermitidos;

    @PostMapping("/callback")
    public ResponseEntity<?> canjearCodigo(
            @RequestBody SolicitudCodigo solicitud,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origen,
            @RequestHeader(value = "X-Requested-With", required = false) String cabeceraSolicitud
    ) {
        if (origen == null || Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim)
                .noneMatch(origen::equals)) {
            return ResponseEntity.status(403).body(Map.of("message", "Origen no autorizado para conectar Gmail."));
        }
        if (!"XmlHttpRequest".equals(cabeceraSolicitud)) {
            return ResponseEntity.status(403).body(Map.of("message", "No se pudo validar la solicitud de autorización."));
        }
        if (solicitud == null || solicitud.code() == null || solicitud.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Falta el código de autorización de Google."));
        }
        CredencialesOAuth credenciales;
        try {
            credenciales = resolverCredenciales();
        } catch (IOException error) {
            return ResponseEntity.status(503).body(Map.of(
                    "message", "Configura GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET, o el archivo OAuth privado del backend."
            ));
        }

        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("code", solicitud.code());
        formulario.add("client_id", credenciales.clientId());
        formulario.add("client_secret", credenciales.clientSecret());
        formulario.add("redirect_uri", origen);
        formulario.add("grant_type", "authorization_code");

        try {
            Map<?, ?> respuesta = clienteHttp.post()
                    .uri(URL_TOKEN_GOOGLE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(Map.class);
            Object accessToken = respuesta == null ? null : respuesta.get("access_token");
            if (!(accessToken instanceof String token) || token.isBlank()) {
                return ResponseEntity.badGateway().body(Map.of("message", "Google no devolvió un token de acceso."));
            }
            return ResponseEntity.ok(Map.of("access_token", token));
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "Google rechazó el canje del código de autorización. Revisa la configuración OAuth del backend."
            ));
        } catch (RuntimeException error) {
            return ResponseEntity.status(502).body(Map.of("message", "No se pudo contactar con Google para conectar Gmail."));
        }
    }

    private CredencialesOAuth resolverCredenciales() throws IOException {
        String idConfigurado = clientId == null ? "" : clientId.trim();
        String secretoConfigurado = clientSecret == null ? "" : clientSecret.trim();
        if (!idConfigurado.isBlank() && !secretoConfigurado.isBlank()) {
            return new CredencialesOAuth(idConfigurado, secretoConfigurado);
        }

        Path ruta = Path.of(archivoCredenciales);
        if (!Files.isRegularFile(ruta)) {
            Path desdeRaiz = Path.of("client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json");
            if (Files.isRegularFile(desdeRaiz)) ruta = desdeRaiz;
        }
        JsonNode cliente = objectMapper.readTree(Files.newInputStream(ruta));
        JsonNode web = cliente.path("web");
        String id = idConfigurado.isBlank() ? web.path("client_id").asText("") : idConfigurado;
        String secreto = secretoConfigurado.isBlank() ? web.path("client_secret").asText("") : secretoConfigurado;
        if (id.isBlank() || secreto.isBlank()) throw new IOException("Faltan las credenciales web de Google.");
        return new CredencialesOAuth(id, secreto);
    }

    public record SolicitudCodigo(String code) { }
    private record CredencialesOAuth(String clientId, String clientSecret) { }
}
