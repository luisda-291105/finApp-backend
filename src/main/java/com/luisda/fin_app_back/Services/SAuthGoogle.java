package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
import com.luisda.fin_app_back.DTO.GoogleUserInfoDTO;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/** OAuth authorization-code exchange and FinApp session creation for Google sign-in. */
@Service
public class SAuthGoogle {
    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_PERFIL_GOOGLE = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String ATRIBUTO_STATE = "googleLoginOAuthState";

    private final IUsuario usuarios;
    private final SVerificadorContrasena verificador;
    private volatile RestClient clienteHttp;
    private final SecureRandom random = new SecureRandom();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientId;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecret;
    @Value("${finapp.google.auth-redirect-uri:${GOOGLE_AUTH_REDIRECT_URI:http://localhost:8080/api/auth/google/callback}}") private String redirectUri;
    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:../client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}") private String archivoCredenciales;

    public SAuthGoogle(IUsuario usuarios, SVerificadorContrasena verificador) {
        this.usuarios = usuarios;
        this.verificador = verificador;
    }

    private RestClient clienteHttp() {
        RestClient cliente = clienteHttp;
        if (cliente != null) return cliente;
        synchronized (this) {
            if (clienteHttp == null) clienteHttp = RestClient.create();
            return clienteHttp;
        }
    }

    public String crearEstado(HttpServletRequest request) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.getSession(true).setAttribute(ATRIBUTO_STATE, state);
        return state;
    }

    public void cancelar(String state, HttpServletRequest request) {
        consumirEstadoValido(state, request);
    }

    public MUsuario autenticar(String code, String state, HttpServletRequest request) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Google no devolvió un código de autorización.");
        HttpSession sesion = consumirEstadoValido(state, request);
        GoogleOAuthTokenDTO tokens;
        try {
            CredencialesOAuth credenciales = resolverCredenciales();
            MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
            formulario.add("code", code);
            formulario.add("client_id", credenciales.clientId());
            formulario.add("client_secret", credenciales.clientSecret());
            formulario.add("redirect_uri", requerido(redirectUri, "Falta configurar GOOGLE_AUTH_REDIRECT_URI."));
            formulario.add("grant_type", "authorization_code");
            tokens = clienteHttp().post().uri(URL_TOKEN_GOOGLE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario).retrieve().body(GoogleOAuthTokenDTO.class);
        } catch (RestClientException error) {
            throw new IllegalArgumentException("Google rechazó el código de autorización. Verifica el redirect URI y las credenciales OAuth.");
        }

        String token = tokens == null || tokens.accessToken() == null ? "" : tokens.accessToken().trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Google no devolvió un token de acceso válido.");
        }
        GoogleUserInfoDTO perfil;
        try {
            perfil = clienteHttp().get().uri(URL_PERFIL_GOOGLE)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(GoogleUserInfoDTO.class);
        } catch (RestClientException error) {
            throw new IllegalArgumentException("No se pudo verificar el perfil de Google.");
        }

        String subject = perfil == null || perfil.sub() == null ? "" : perfil.sub().trim();
        String correo = perfil == null || perfil.email() == null ? "" : perfil.email().trim();
        String nombre = perfil == null || perfil.name() == null ? "" : perfil.name().trim();
        if (perfil == null || subject.isBlank() || correo.isBlank() || !Boolean.TRUE.equals(perfil.emailVerified())) {
            throw new IllegalArgumentException("La cuenta de Google no tiene un correo verificado disponible.");
        }

        MUsuario usuario = usuarios.findByGoogleSubject(subject).orElseGet(() -> crearUsuarioGoogle(subject, correo, nombre));
        request.changeSessionId();
        sesion = request.getSession(true);
        sesion.removeAttribute(ATRIBUTO_STATE);
        sesion.setAttribute(SUsuarioActivo.CLAVE_SESION, usuario.getIdUsuario());
        return usuario;
    }

    private MUsuario crearUsuarioGoogle(String subject, String correo, String nombre) {
        if (usuarios.findByCorreoGoogleIgnoreCase(correo).isPresent()) {
            throw new IllegalArgumentException("Este correo ya está vinculado a otra identidad de Google.");
        }
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(UUID.nameUUIDFromBytes(("finapp-google:" + subject).getBytes(StandardCharsets.UTF_8)).toString());
        usuario.setNombre(nombre.isBlank() ? correo.substring(0, correo.indexOf('@')) : nombre);
        usuario.setContrasena(verificador.codificar(UUID.randomUUID() + "-" + UUID.randomUUID()));
        usuario.setFechaCreacion(LocalDate.now());
        usuario.setGoogleSubject(subject);
        usuario.setCorreoGoogle(correo);
        return usuarios.save(usuario);
    }

    private HttpSession consumirEstadoValido(String state, HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object esperado = sesion == null ? null : sesion.getAttribute(ATRIBUTO_STATE);
        if (!(esperado instanceof String valor) || state == null || !MessageDigest.isEqual(
                valor.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("La verificación de seguridad de Google expiró o no coincide. Intenta de nuevo.");
        }
        sesion.removeAttribute(ATRIBUTO_STATE);
        return sesion;
    }

    private static String requerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new IllegalStateException(mensaje);
        return valor.trim();
    }

    private CredencialesOAuth resolverCredenciales() {
        String idConfigurado = clientId == null ? "" : clientId.trim();
        String secretoConfigurado = clientSecret == null ? "" : clientSecret.trim();
        if (!idConfigurado.isBlank() && !secretoConfigurado.isBlank())
            return new CredencialesOAuth(idConfigurado, secretoConfigurado);
        try {
            Path ruta = Path.of(archivoCredenciales);
            if (!Files.isRegularFile(ruta)) {
                Path desdeRaiz = Path.of("client_secret_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json");
                if (Files.isRegularFile(desdeRaiz)) ruta = desdeRaiz;
            }
            JsonNode web;
            try (var entrada = Files.newInputStream(ruta)) {
                web = new ObjectMapper().readTree(entrada).path("web");
            }
            String id = idConfigurado.isBlank() ? web.path("client_id").asText("") : idConfigurado;
            String secreto = secretoConfigurado.isBlank() ? web.path("client_secret").asText("") : secretoConfigurado;
            if (!id.isBlank() && !secreto.isBlank()) return new CredencialesOAuth(id, secreto);
        } catch (IOException | RuntimeException ignored) {
            // Keep the public auth response generic and do not reveal credential file details.
        }
        throw new IllegalStateException("Configura las credenciales OAuth web de Google en el backend.");
    }

    private record CredencialesOAuth(String clientId, String clientSecret) { }
}
