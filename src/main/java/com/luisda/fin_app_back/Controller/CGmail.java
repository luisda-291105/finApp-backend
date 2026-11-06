package com.luisda.fin_app_back.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GmailProfileDTO;
import com.luisda.fin_app_back.DTO.GmailCuentaDTO;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
import com.luisda.fin_app_back.Model.CuentaGmail;
import com.luisda.fin_app_back.Model.MGmailConexion;
import com.luisda.fin_app_back.Repository.ICuentaGmail;
import com.luisda.fin_app_back.Repository.IGmailConexion;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/** Canjea y mantiene credenciales Gmail cifradas para el usuario activo. */
@RestController
@RequestMapping("/api/gmail")
public class CGmail {
    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_PERFIL_GOOGLE = "https://gmail.googleapis.com/gmail/v1/users/me/profile";
    private static final int DURACION_TOKEN_SEGUNDOS_POR_DEFECTO = 3600;
    private static final int MARGEN_RENOVACION_SEGUNDOS = 60;

    private volatile RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IGmailConexion conexiones;
    private final ICuentaGmail cuentasGmail;
    private final SUsuarioActivo usuarioActivo;
    private final SecureRandom random = new SecureRandom();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientId;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecret;
    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:../client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}")
    private String archivoCredenciales;
    @Value("${finapp.cors.allowed-origins:http://localhost:5173}") private String origenesPermitidos;

    public CGmail(IGmailConexion conexiones, ICuentaGmail cuentasGmail, SUsuarioActivo usuarioActivo) {
        this.conexiones = conexiones;
        this.cuentasGmail = cuentasGmail;
        this.usuarioActivo = usuarioActivo;
    }

    private RestClient clienteHttp() {
        RestClient cliente = clienteHttp;
        if (cliente != null) return cliente;
        synchronized (this) {
            if (clienteHttp == null) clienteHttp = RestClient.create();
            return clienteHttp;
        }
    }

    @PostMapping("/callback")
    public ResponseEntity<?> canjearCodigo(
            @RequestBody SolicitudCodigo solicitud,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origen,
            @RequestHeader(value = "X-Requested-With", required = false) String cabeceraSolicitud,
            HttpServletRequest request
    ) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Inicia sesión en FinApp antes de conectar Gmail."));
        if (origen == null || Arrays.stream(origenesPermitidos.split(",")).map(String::trim).noneMatch(origen::equals)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Origen no autorizado para conectar Gmail."));
        }
        if (!"XmlHttpRequest".equals(cabeceraSolicitud)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "No se pudo validar la solicitud de autorización."));
        }
        if (solicitud == null || solicitud.code() == null || solicitud.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Falta el código de autorización de Google."));
        }

        CredencialesOAuth credenciales;
        try {
            credenciales = resolverCredenciales();
        } catch (IOException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
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
            GoogleOAuthTokenDTO respuesta = clienteHttp().post().uri(URL_TOKEN_GOOGLE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario)
                    .retrieve().body(GoogleOAuthTokenDTO.class);
            String accessToken = respuesta == null || respuesta.accessToken() == null ? "" : respuesta.accessToken().trim();
            if (accessToken.isBlank()) return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Google no devolvió un token de acceso."));

            String correo = obtenerCorreoGoogle(accessToken);
            var cuentaExistente = cuentasGmail.findByUsuarioIdAndEmailIgnoreCase(usuario.getIdUsuario(), correo);
            String refreshToken = respuesta.refreshToken() == null ? "" : respuesta.refreshToken().trim();
            CuentaGmail cuenta = cuentaExistente.orElseGet(CuentaGmail::new);
            if (refreshToken.isBlank() && cuenta.getRefreshTokenCifrado() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "message", "Google no entregó un refresh token. Vuelve a conectar y acepta el consentimiento de Gmail."
                ));
            }
            cuenta.setUsuarioId(usuario.getIdUsuario());
            cuenta.setEmail(correo);
            if (!refreshToken.isBlank()) cuenta.setRefreshTokenCifrado(cifrar(refreshToken, credenciales.clientSecret()));
            cuenta = cuentasGmail.save(cuenta);

            MGmailConexion conexion = conexiones.findById(usuario.getIdUsuario()).orElseGet(MGmailConexion::new);
            conexion.setIdUsuario(usuario.getIdUsuario());
            conexion.setCorreoGoogle(correo);
            conexion.setAccessTokenCifrado(cifrar(accessToken, credenciales.clientSecret()));
            if (!refreshToken.isBlank()) conexion.setRefreshTokenCifrado(cifrar(refreshToken, credenciales.clientSecret()));
            long expiraEn = respuesta.expiresIn() == null || respuesta.expiresIn() <= 0
                    ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : respuesta.expiresIn();
            conexion.setExpiraEn(Instant.now().plusSeconds(expiraEn));
            conexiones.save(conexion);

            return ResponseEntity.ok(Map.of("access_token", accessToken, "email", correo,
                    "account_id", cuenta.getId(), "expires_at", conexion.getExpiraEn().toString()));
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", "Google rechazó el canje del código de autorización. Revisa la configuración OAuth del backend."
            ));
        } catch (GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "No se pudo cifrar la conexión de Gmail para guardarla de forma segura."
            ));
        } catch (RuntimeException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", "No se pudo completar y guardar la conexión con Gmail."
            ));
        }
    }

    /** Restaura tras recargar la página el token cifrado guardado para el usuario de la sesión. */
    @GetMapping("/cuentas")
    public ResponseEntity<?> listarCuentas(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        // Migra la conexión única previa a la tabla de cuentas múltiples cuando conserva refresh token.
        if (cuentasGmail.findByUsuarioIdOrderByCreadaEnAsc(usuario.getIdUsuario()).isEmpty()) {
            conexiones.findById(usuario.getIdUsuario()).filter(legacy -> legacy.getRefreshTokenCifrado() != null
                    && !legacy.getRefreshTokenCifrado().isBlank()).ifPresent(legacy -> {
                CuentaGmail cuenta = new CuentaGmail();
                cuenta.setUsuarioId(usuario.getIdUsuario());
                cuenta.setEmail(legacy.getCorreoGoogle());
                cuenta.setRefreshTokenCifrado(legacy.getRefreshTokenCifrado());
                cuentasGmail.save(cuenta);
            });
        }
        return ResponseEntity.ok(cuentasGmail.findByUsuarioIdOrderByCreadaEnAsc(usuario.getIdUsuario()).stream()
                .map(cuenta -> new GmailCuentaDTO(cuenta.getId(), cuenta.getEmail())).toList());
    }

    /** Devuelve un token de acceso renovado solo si la cuenta pertenece a la sesión activa. */
    @GetMapping("/cuentas/{cuentaId}/conexion")
    public ResponseEntity<?> obtenerConexionCuenta(@org.springframework.web.bind.annotation.PathVariable Long cuentaId,
                                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        CuentaGmail cuenta = cuentasGmail.findByIdAndUsuarioId(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail."));
        try {
            CredencialesOAuth credenciales = resolverCredenciales();
            GoogleOAuthTokenDTO renovacion = renovarToken(descifrar(cuenta.getRefreshTokenCifrado(), credenciales.clientSecret()), credenciales);
            String token = renovacion == null || renovacion.accessToken() == null ? "" : renovacion.accessToken().trim();
            if (token.isBlank()) return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Google no devolvió un token renovado."));
            long duracion = renovacion.expiresIn() == null || renovacion.expiresIn() <= 0
                    ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : renovacion.expiresIn();
            return ResponseEntity.ok(Map.of("connected", true, "access_token", token,
                    "email", cuenta.getEmail(), "expires_at", Instant.now().plusSeconds(duracion).toString()));
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "connected", false, "requires_reconnect", true,
                    "message", "Google revocó o venció esta autorización. Conecta de nuevo la cuenta."
            ));
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo restaurar esta cuenta Gmail."));
        }
    }

    @DeleteMapping("/cuentas/{cuentaId}")
    public ResponseEntity<?> eliminarCuenta(@org.springframework.web.bind.annotation.PathVariable Long cuentaId,
                                            HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        var cuenta = cuentasGmail.findByIdAndUsuarioId(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null) return ResponseEntity.notFound().build();
        cuentasGmail.delete(cuenta);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conexion")
    public ResponseEntity<?> obtenerConexion(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        Optional<MGmailConexion> encontrada = conexiones.findById(usuario.getIdUsuario());
        if (encontrada.isEmpty()) return ResponseEntity.ok(Map.of("connected", false));

        MGmailConexion conexion = encontrada.get();
        try {
            CredencialesOAuth credenciales = resolverCredenciales();
            if (!conexion.getExpiraEn().isAfter(Instant.now().plusSeconds(MARGEN_RENOVACION_SEGUNDOS))) {
                String refreshCifrado = conexion.getRefreshTokenCifrado();
                if (refreshCifrado == null || refreshCifrado.isBlank()) {
                    return ResponseEntity.ok(Map.of("connected", false, "requires_reconnect", true, "email", conexion.getCorreoGoogle()));
                }
                try {
                    GoogleOAuthTokenDTO renovacion = renovarToken(descifrar(refreshCifrado, credenciales.clientSecret()), credenciales);
                    String tokenNuevo = renovacion == null || renovacion.accessToken() == null ? "" : renovacion.accessToken().trim();
                    if (tokenNuevo.isBlank()) return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("message", "Google no devolvió un token renovado."));
                    conexion.setAccessTokenCifrado(cifrar(tokenNuevo, credenciales.clientSecret()));
                    long duracion = renovacion.expiresIn() == null || renovacion.expiresIn() <= 0
                            ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : renovacion.expiresIn();
                    conexion.setExpiraEn(Instant.now().plusSeconds(duracion));
                    conexiones.save(conexion);
                } catch (RestClientResponseException error) {
                    return ResponseEntity.ok(Map.of("connected", false, "requires_reconnect", true, "email", conexion.getCorreoGoogle()));
                }
            }
            String accessToken = descifrar(conexion.getAccessTokenCifrado(), credenciales.clientSecret());
            return ResponseEntity.ok(Map.of("connected", true, "access_token", accessToken, "email", conexion.getCorreoGoogle(),
                    "expires_at", conexion.getExpiraEn().toString()));
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", "No se pudo restaurar la conexión guardada. Vuelve a conectar Gmail."
            ));
        }
    }

    /** Elimina la credencial persistida al desconectar Gmail desde Configuración. */
    @DeleteMapping("/conexion")
    public ResponseEntity<?> desconectar(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        conexiones.deleteById(usuario.getIdUsuario());
        return ResponseEntity.noContent().build();
    }

    private GoogleOAuthTokenDTO renovarToken(String refreshToken, CredencialesOAuth credenciales) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", credenciales.clientId());
        formulario.add("client_secret", credenciales.clientSecret());
        formulario.add("refresh_token", refreshToken);
        formulario.add("grant_type", "refresh_token");
        return clienteHttp().post().uri(URL_TOKEN_GOOGLE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario).retrieve().body(GoogleOAuthTokenDTO.class);
    }

    private String obtenerCorreoGoogle(String accessToken) {
        GmailProfileDTO perfil = clienteHttp().get().uri(URL_PERFIL_GOOGLE)
                .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(GmailProfileDTO.class);
        String correo = perfil == null || perfil.emailAddress() == null ? "" : perfil.emailAddress().trim();
        if (correo.isBlank()) throw new IllegalArgumentException("Google no devolvió la dirección de la cuenta.");
        return correo;
    }

    private String cifrar(String valor, String secreto) throws GeneralSecurityException {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, llave(secreto), new GCMParameterSpec(128, nonce));
        byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
        byte[] combinado = new byte[nonce.length + cifrado.length];
        System.arraycopy(nonce, 0, combinado, 0, nonce.length);
        System.arraycopy(cifrado, 0, combinado, nonce.length, cifrado.length);
        return Base64.getEncoder().encodeToString(combinado);
    }

    private String descifrar(String valor, String secreto) throws GeneralSecurityException {
        byte[] combinado = Base64.getDecoder().decode(valor);
        byte[] nonce = Arrays.copyOfRange(combinado, 0, 12);
        byte[] cifrado = Arrays.copyOfRange(combinado, 12, combinado.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, llave(secreto), new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    private SecretKeySpec llave(String secreto) throws GeneralSecurityException {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(("finapp:gmail-token:v1:" + secreto).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "AES");
    }

    private CredencialesOAuth resolverCredenciales() throws IOException {
        String idConfigurado = clientId == null ? "" : clientId.trim();
        String secretoConfigurado = clientSecret == null ? "" : clientSecret.trim();
        if (!idConfigurado.isBlank() && !secretoConfigurado.isBlank()) return new CredencialesOAuth(idConfigurado, secretoConfigurado);

        Path ruta = Path.of(archivoCredenciales);
        if (!Files.isRegularFile(ruta)) {
            Path desdeRaiz = Path.of("client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json");
            if (Files.isRegularFile(desdeRaiz)) ruta = desdeRaiz;
        }
        JsonNode web;
        try (var entrada = Files.newInputStream(ruta)) { web = objectMapper.readTree(entrada).path("web"); }
        String id = idConfigurado.isBlank() ? web.path("client_id").asText("") : idConfigurado;
        String secreto = secretoConfigurado.isBlank() ? web.path("client_secret").asText("") : secretoConfigurado;
        if (id.isBlank() || secreto.isBlank()) throw new IOException("Faltan las credenciales web de Google.");
        return new CredencialesOAuth(id, secreto);
    }

    public record SolicitudCodigo(String code) { }
    private record CredencialesOAuth(String clientId, String clientSecret) { }
}
