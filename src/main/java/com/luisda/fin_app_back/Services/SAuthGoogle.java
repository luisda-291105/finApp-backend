package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GoogleCompletarRegistroDTO;
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

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

/** OAuth authorization-code exchange for Google sign-in and sign-up with credential completion. */
@Service
public class SAuthGoogle {
    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_PERFIL_GOOGLE = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String ATRIBUTO_STATE = "googleLoginOAuthState";
    private static final String ATRIBUTO_MODO = "googleOAuthModo";
    private static final String ATRIBUTO_REGISTRO_PENDIENTE = "googleRegistroPendiente";

    public record RegistroGooglePendiente(String googleSubject, String correo, String nombre) implements Serializable {}

    public sealed interface ResultadoCallbackGoogle {
        record LoginExitoso(MUsuario usuario) implements ResultadoCallbackGoogle {}
        record NoRegistrado(String correo) implements ResultadoCallbackGoogle {}
        record YaRegistrado() implements ResultadoCallbackGoogle {}
        record CorreoYaRegistrado(String correo) implements ResultadoCallbackGoogle {}
        record RegistroPendiente(String correo, String nombre) implements ResultadoCallbackGoogle {}
    }

    private final IUsuario usuarios;
    private final SVerificadorContrasena verificador;
    private volatile RestClient clienteHttp;
    private final SecureRandom random = new SecureRandom();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientId;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecret;
    @Value("${finapp.google.auth-redirect-uri:${GOOGLE_AUTH_REDIRECT_URI:http://localhost:8081/api/auth/google/callback}}") private String redirectUri;
    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:client_secret_2_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}") private String archivoCredenciales;

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

    public String crearEstado(String modo, HttpServletRequest request) {
        String modoNormalizado = "registro".equalsIgnoreCase(modo) ? "registro" : "login";
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        HttpSession sesion = request.getSession(true);
        sesion.setAttribute(ATRIBUTO_STATE, state);
        sesion.setAttribute(ATRIBUTO_MODO, modoNormalizado);
        return state;
    }

    public void cancelar(String state, HttpServletRequest request) {
        consumirEstadoValido(state, request);
    }

    public ResultadoCallbackGoogle procesarCallback(String code, String state, HttpServletRequest request) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Google no devolvió un código de autorización.");
        String modo = consumirEstadoValido(state, request);
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

        if ("registro".equals(modo)) {
            if (usuarios.findByGoogleSubject(subject).isPresent()) {
                return new ResultadoCallbackGoogle.YaRegistrado();
            }
            if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
                return new ResultadoCallbackGoogle.CorreoYaRegistrado(correo);
            }
            RegistroGooglePendiente pendiente = new RegistroGooglePendiente(subject, correo, nombre);
            request.getSession(true).setAttribute(ATRIBUTO_REGISTRO_PENDIENTE, pendiente);
            return new ResultadoCallbackGoogle.RegistroPendiente(correo, nombre);
        }

        // Modo login
        Optional<MUsuario> porSubject = usuarios.findByGoogleSubject(subject);
        if (porSubject.isPresent()) {
            return new ResultadoCallbackGoogle.LoginExitoso(porSubject.get());
        }
        Optional<MUsuario> porCorreo = usuarios.findByCorreoIgnoreCase(correo);
        if (porCorreo.isPresent()) {
            MUsuario usuario = porCorreo.get();
            if (usuario.getGoogleSubject() == null || usuario.getGoogleSubject().isBlank()) {
                usuario.setGoogleSubject(subject);
                usuarios.save(usuario);
            }
            return new ResultadoCallbackGoogle.LoginExitoso(usuario);
        }
        return new ResultadoCallbackGoogle.NoRegistrado(correo);
    }

    public RegistroGooglePendiente obtenerRegistroPendiente(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) return null;
        Object pendiente = sesion.getAttribute(ATRIBUTO_REGISTRO_PENDIENTE);
        return pendiente instanceof RegistroGooglePendiente r ? r : null;
    }

    public MUsuario completarRegistro(GoogleCompletarRegistroDTO datos, HttpServletRequest request) {
        RegistroGooglePendiente pendiente = obtenerRegistroPendiente(request);
        if (pendiente == null) {
            throw new IllegalStateException("No hay un registro de Google pendiente o la sesión expiró. Inicia el registro de nuevo.");
        }
        if (datos == null || datos.idUsuario() == null || datos.idUsuario().isBlank()) {
            throw new IllegalArgumentException("La identificación es obligatoria.");
        }
        String idUsuario = datos.idUsuario().trim();
        if (usuarios.existsById(idUsuario)) {
            throw new IllegalStateException("Ya existe un usuario con esta identificación.");
        }
        if (datos.contrasena() == null || datos.contrasena().length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (usuarios.findByCorreoIgnoreCase(pendiente.correo()).isPresent()) {
            throw new IllegalStateException("Ya existe una cuenta con este correo.");
        }
        String nombre = datos.nombre() != null && !datos.nombre().isBlank() ? datos.nombre().trim() : pendiente.nombre();
        String telefono = datos.telefono() != null && !datos.telefono().isBlank() ? datos.telefono().trim() : null;

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(idUsuario);
        usuario.setNombre(nombre.isBlank() ? pendiente.correo().substring(0, pendiente.correo().indexOf('@')) : nombre);
        usuario.setCorreo(pendiente.correo());
        usuario.setTelefono(telefono);
        usuario.setContrasena(verificador.codificar(datos.contrasena()));
        usuario.setGoogleSubject(pendiente.googleSubject());
        usuario.setFechaCreacion(LocalDate.now());

        MUsuario guardado = usuarios.save(usuario);
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(ATRIBUTO_REGISTRO_PENDIENTE);
        }
        return guardado;
    }

    private String consumirEstadoValido(String state, HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        Object esperado = sesion == null ? null : sesion.getAttribute(ATRIBUTO_STATE);
        if (!(esperado instanceof String valor) || state == null || !MessageDigest.isEqual(
                valor.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("La verificación de seguridad de Google expiró o no coincide. Intenta de nuevo.");
        }
        String modo = (String) sesion.getAttribute(ATRIBUTO_MODO);
        sesion.removeAttribute(ATRIBUTO_STATE);
        sesion.removeAttribute(ATRIBUTO_MODO);
        return modo != null ? modo : "login";
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
                Path desdeCarpetaBackend = Path.of("finApp-backend").resolve(ruta.getFileName());
                if (Files.isRegularFile(desdeCarpetaBackend)) ruta = desdeCarpetaBackend;
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
