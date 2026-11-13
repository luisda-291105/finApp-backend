package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.AlmacenamientoConexionDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoInicioDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @uso               Gestiona conexiones de almacenamiento en la nube (Google Drive, OneDrive y Nextcloud)
 * @capa              Servicio
 * @responsabilidades Listar, iniciar/completar OAuth con state de un solo uso, conectar Nextcloud,
 *                    desconectar con revocación remota y cifrar todos los secretos antes de persistir
 * @datos             Metadatos de cuenta para el frontend; tokens y credenciales cifradas solo en base de datos
 * @eventos           Ninguno
 * @estados           Estados de conexión ACTIVA y REQUIERE_RECONEXION
 * @usadoPor          CAlmacenamiento
 */
@Service
public class SAlmacenamientoConexiones {

    public static final String PROVEEDOR_GOOGLE_DRIVE = "GOOGLE_DRIVE";
    public static final String PROVEEDOR_ONEDRIVE = "ONEDRIVE";
    public static final String PROVEEDOR_NEXTCLOUD = "NEXTCLOUD";

    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_REVOCAR_GOOGLE = "https://oauth2.googleapis.com/revoke";
    private static final String URL_AUTORIZAR_GOOGLE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String URL_PERFIL_GOOGLE = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String URL_TOKEN_MICROSOFT = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String URL_AUTORIZAR_MICROSOFT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String URL_PERFIL_MICROSOFT = "https://graph.microsoft.com/v1.0/me";
    private static final String URL_REVOCAR_MICROSOFT = "https://graph.microsoft.com/v1.0/me/revokeSignInSessions";
    private static final String SCOPE_GOOGLE = "openid email profile";
    private static final String SCOPE_MICROSOFT = "openid profile email offline_access User.Read";
    private static final long VIDA_STATE_SEGUNDOS = 600;
    private static final long MARGEN_RENOVACION_SEGUNDOS = 60;
    private static final long DURACION_TOKEN_SEGUNDOS_POR_DEFECTO = 3600;

    private static final Logger LOGGER = LoggerFactory.getLogger(SAlmacenamientoConexiones.class);

    public record ConexionNextcloud(String urlServidor, String usuario, String credencial) { }

    public static class EstadoInvalidoException extends RuntimeException {
        public EstadoInvalidoException(String mensaje) { super(mensaje); }
    }

    public static class ProveedorNoSoportadoException extends RuntimeException {
        public ProveedorNoSoportadoException(String mensaje) { super(mensaje); }
    }

    private record RegistroState(String idUsuario, String proveedor, Instant expiracion) { }

    private final IAlmacenamientoConexiones conexiones;
    private final SAlmacenamientoCifrado cifrado;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, RegistroState> estadosPendientes = new ConcurrentHashMap<>();
    private volatile RestClient clienteHttp;

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientIdGoogle;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecretGoogle;
    @Value("${finapp.microsoft.client-id:${MICROSOFT_CLIENT_ID:}}") private String clientIdMicrosoft;
    @Value("${finapp.microsoft.client-secret:${MICROSOFT_CLIENT_SECRET:}}") private String clientSecretMicrosoft;
    @Value("${finapp.web.base-url:${WEB_APP_URL:http://localhost:5173}}") private String baseUrlWeb;

    public SAlmacenamientoConexiones(IAlmacenamientoConexiones conexiones, SAlmacenamientoCifrado cifrado) {
        this.conexiones = conexiones;
        this.cifrado = cifrado;
    }

    private RestClient clienteHttp() {
        RestClient cliente = clienteHttp;
        if (cliente != null) return cliente;
        synchronized (this) {
            if (clienteHttp == null) clienteHttp = RestClient.create();
            return clienteHttp;
        }
    }

    /** Lista las conexiones del usuario con estado efectivo; nunca incluye secretos. */
    public List<AlmacenamientoConexionDTO> listar(MUsuario usuario) {
        return conexiones.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .map(this::aDto)
                .toList();
    }

    /** Genera la URL de autorización del proveedor con state de un solo uso asociado al usuario. */
    public AlmacenamientoInicioDTO iniciar(MUsuario usuario, String proveedor) {
        String proveedorNormalizado = normalizarProveedor(proveedor);
        String estado = generarState();
        estadosPendientes.put(estado, new RegistroState(usuario.getIdUsuario(), proveedorNormalizado,
                Instant.now().plusSeconds(VIDA_STATE_SEGUNDOS)));
        String uri = switch (proveedorNormalizado) {
            case PROVEEDOR_GOOGLE_DRIVE -> UriComponentsBuilder.fromUriString(URL_AUTORIZAR_GOOGLE)
                    .queryParam("client_id", valor(clientIdGoogle))
                    .queryParam("redirect_uri", uriRetorno())
                    .queryParam("response_type", "code")
                    .queryParam("scope", SCOPE_GOOGLE)
                    .queryParam("state", estado)
                    .queryParam("access_type", "offline")
                    .queryParam("prompt", "consent")
                    .build().encode().toUriString();
            case PROVEEDOR_ONEDRIVE -> UriComponentsBuilder.fromUriString(URL_AUTORIZAR_MICROSOFT)
                    .queryParam("client_id", valor(clientIdMicrosoft))
                    .queryParam("redirect_uri", uriRetorno())
                    .queryParam("response_type", "code")
                    .queryParam("response_mode", "query")
                    .queryParam("scope", SCOPE_MICROSOFT)
                    .queryParam("state", estado)
                    .queryParam("prompt", "consent")
                    .build().encode().toUriString();
            default -> throw new ProveedorNoSoportadoException(
                    "El flujo OAuth no aplica a " + proveedorNormalizado + "; usa la conexión directa.");
        };
        return new AlmacenamientoInicioDTO(proveedorNormalizado, uri);
    }

    /** Completa el flujo OAuth: con state (OneDrive, redirección) o sin state (Google Drive, popup GIS).
     *  El proveedor se valida contra el state registrado; el cliente no lo decide por su cuenta. */
    public AlmacenamientoConexionDTO completarOAuth(MUsuario usuario, String proveedor, String code, String state,
                                                    String redirectUri) throws GeneralSecurityException {
        String proveedorNormalizado = state == null || state.isBlank()
                ? normalizarProveedor(proveedor)
                : null;
        if (code == null || code.isBlank()) {
            throw new EstadoInvalidoException("La autorización no incluye un código válido.");
        }
        if (state != null && !state.isBlank()) {
            RegistroState registro = estadosPendientes.remove(state);
            if (registro == null) throw new EstadoInvalidoException("El estado de autorización no es válido o ya se usó.");
            if (registro.expiracion().isBefore(Instant.now())) throw new EstadoInvalidoException("La autorización expiró; vuelve a intentarlo.");
            if (!registro.idUsuario().equals(usuario.getIdUsuario())) {
                throw new EstadoInvalidoException("La autorización no corresponde a esta sesión.");
            }
            proveedorNormalizado = registro.proveedor();
        }
        String proveedorFinal = normalizarProveedor(proveedorNormalizado);
        return switch (proveedorFinal) {
            case PROVEEDOR_GOOGLE_DRIVE -> completarGoogleDrive(usuario, code.trim(), redirectUri);
            case PROVEEDOR_ONEDRIVE -> completarOneDrive(usuario, code.trim(), redirectUri);
            default -> throw new ProveedorNoSoportadoException("Proveedor OAuth no soportado.");
        };
    }

    /** Conecta una cuenta Nextcloud validando la credencial por WebDAV y cifrándola antes de guardar. */
    public AlmacenamientoConexionDTO conectarNextcloud(MUsuario usuario, ConexionNextcloud solicitud)
            throws GeneralSecurityException {
        if (solicitud == null) throw new IllegalArgumentException("Faltan los datos de la conexión Nextcloud.");
        String url = normalizarUrlServidor(solicitud.urlServidor());
        String nombreUsuario = solicitud.usuario() == null ? "" : solicitud.usuario().trim();
        String credencial = solicitud.credencial() == null ? "" : solicitud.credencial();
        if (url.isBlank() || nombreUsuario.isBlank() || credencial.isBlank()) {
            throw new IllegalArgumentException("Completa la URL del servidor, el usuario y la contraseña de aplicación.");
        }
        validarCredencialNextcloud(url, nombreUsuario, credencial);
        String urlCanonica = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        MAlmacenamientoConexion conexion = conexiones.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(c -> PROVEEDOR_NEXTCLOUD.equals(c.getProveedor()))
                .filter(c -> urlCanonica.equalsIgnoreCase(c.getUrlServidor())
                        && nombreUsuario.equalsIgnoreCase(valor(c.getEtiquetaCuenta())))
                .findFirst()
                .orElseGet(() -> new MAlmacenamientoConexion(UUID.randomUUID().toString(), usuario, PROVEEDOR_NEXTCLOUD));
        conexion.setUrlServidor(urlCanonica);
        conexion.setEtiquetaCuenta(nombreUsuario);
        conexion.setIdentificadorExterno(nombreUsuario);
        conexion.setEstado("ACTIVA");
        conexion.setCredencialCifrada(cifrado.cifrar(credencial));
        conexion.setActualizadoEn(Instant.now());
        return aDto(conexiones.save(conexion));
    }

    /** Elimina la conexión del usuario e intenta revocar la autorización remota. */
    public boolean desconectar(String idConexion, MUsuario usuario) {
        MAlmacenamientoConexion conexion = conexiones.findByIdConexionAndUsuario_IdUsuario(idConexion, usuario.getIdUsuario())
                .orElse(null);
        if (conexion == null) return false;
        boolean revocacionFallida = false;
        try {
            revocar(conexion);
        } catch (RestClientResponseException | IllegalStateException error) {
            revocacionFallida = true;
            LOGGER.warn("No se pudo revocar la autorización remota de la conexión {} del usuario {}",
                    idConexion, usuario.getIdUsuario(), error);
        } catch (GeneralSecurityException error) {
            revocacionFallida = true;
            LOGGER.warn("No se pudo descifrar el material para revocar la conexión {}", idConexion, error);
        }
        conexiones.delete(conexion);
        return revocacionFallida;
    }

    private AlmacenamientoConexionDTO completarGoogleDrive(MUsuario usuario, String code, String redirectUri)
            throws GeneralSecurityException {
        if (clientIdGoogle.isBlank() || clientSecretGoogle.isBlank()) {
            throw new IllegalStateException("Configura GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET en el backend.");
        }
        JsonNode tokens = canjearToken(URL_TOKEN_GOOGLE, clientIdGoogle.trim(), clientSecretGoogle.trim(), code, null, redirectUri);
        String accessToken = tokens.path("access_token").asText("");
        if (accessToken.isBlank()) throw new IllegalStateException("Google no devolvió un token de acceso.");
        String refreshToken = tokens.path("refresh_token").asText("");
        long expiraEn = tokens.path("expires_in").asLong(DURACION_TOKEN_SEGUNDOS_POR_DEFECTO);
        JsonNode perfil = respuestaJson(clienteHttp().get().uri(URI.create(URL_PERFIL_GOOGLE))
                .headers(h -> h.setBearerAuth(accessToken)), JsonNode.class);
        String correo = perfil == null ? "" : perfil.path("email").asText("").trim();
        if (correo.isBlank()) throw new IllegalStateException("Google no devolvió la identidad de la cuenta.");

        MAlmacenamientoConexion conexion = conexiones.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(c -> PROVEEDOR_GOOGLE_DRIVE.equals(c.getProveedor()))
                .filter(c -> correo.equalsIgnoreCase(valor(c.getIdentificadorExterno())))
                .findFirst()
                .orElseGet(() -> new MAlmacenamientoConexion(UUID.randomUUID().toString(), usuario, PROVEEDOR_GOOGLE_DRIVE));
        conexion.setIdentificadorExterno(correo);
        conexion.setEtiquetaCuenta(correo);
        conexion.setEstado("ACTIVA");
        conexion.setAccessTokenCifrado(cifrado.cifrar(accessToken));
        if (!refreshToken.isBlank()) conexion.setRefreshTokenCifrado(cifrado.cifrar(refreshToken));
        conexion.setExpiraEn(Instant.now().plusSeconds(expiraEn));
        conexion.setActualizadoEn(Instant.now());
        return aDto(conexiones.save(conexion));
    }

    private AlmacenamientoConexionDTO completarOneDrive(MUsuario usuario, String code, String redirectUri)
            throws GeneralSecurityException {
        if (clientIdMicrosoft.isBlank() || clientSecretMicrosoft.isBlank()) {
            throw new IllegalStateException("Configura MICROSOFT_CLIENT_ID y MICROSOFT_CLIENT_SECRET en el backend.");
        }
        JsonNode tokens = canjearToken(URL_TOKEN_MICROSOFT, clientIdMicrosoft.trim(), clientSecretMicrosoft.trim(), code,
                SCOPE_MICROSOFT, redirectUri);
        String accessToken = tokens.path("access_token").asText("");
        if (accessToken.isBlank()) throw new IllegalStateException("Microsoft no devolvió un token de acceso.");
        String refreshToken = tokens.path("refresh_token").asText("");
        long expiraEn = tokens.path("expires_in").asLong(DURACION_TOKEN_SEGUNDOS_POR_DEFECTO);
        JsonNode perfil = respuestaJson(clienteHttp().get().uri(URI.create(URL_PERFIL_MICROSOFT))
                .headers(h -> h.setBearerAuth(accessToken)), JsonNode.class);
        String correo = perfil == null ? "" : primeraOpcion(perfil.path("mail"), perfil.path("userPrincipalName"));
        String nombre = perfil == null ? "" : perfil.path("displayName").asText("").trim();
        if (correo.isBlank()) throw new IllegalStateException("Microsoft no devolvió la identidad de la cuenta.");

        MAlmacenamientoConexion conexion = conexiones.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(c -> PROVEEDOR_ONEDRIVE.equals(c.getProveedor()))
                .filter(c -> correo.equalsIgnoreCase(valor(c.getIdentificadorExterno())))
                .findFirst()
                .orElseGet(() -> new MAlmacenamientoConexion(UUID.randomUUID().toString(), usuario, PROVEEDOR_ONEDRIVE));
        conexion.setIdentificadorExterno(correo);
        conexion.setEtiquetaCuenta(nombre.isBlank() ? correo : nombre + " (" + correo + ")");
        conexion.setEstado("ACTIVA");
        conexion.setAccessTokenCifrado(cifrado.cifrar(accessToken));
        if (!refreshToken.isBlank()) conexion.setRefreshTokenCifrado(cifrado.cifrar(refreshToken));
        conexion.setExpiraEn(Instant.now().plusSeconds(expiraEn));
        conexion.setActualizadoEn(Instant.now());
        return aDto(conexiones.save(conexion));
    }

    private JsonNode canjearToken(String urlToken, String clientId, String clientSecret, String code, String scope,
                                  String redirectUri) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("code", code);
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("redirect_uri", redirectUri == null || redirectUri.isBlank() ? uriRetorno() : redirectUri);
        formulario.add("grant_type", "authorization_code");
        if (scope != null && !scope.isBlank()) formulario.add("scope", scope);
        try {
            JsonNode respuesta = respuestaJson(clienteHttp().post().uri(URI.create(urlToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario), JsonNode.class);
            if (respuesta == null || respuesta.path("access_token").asText("").isBlank()) {
                throw new IllegalStateException("El proveedor no devolvió un token de acceso válido.");
            }
            return respuesta;
        } catch (RestClientResponseException error) {
            throw new IllegalStateException("El proveedor rechazó el canje del código de autorización.", error);
        }
    }

    private void revocar(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        if (PROVEEDOR_NEXTCLOUD.equals(conexion.getProveedor())) return;
        if (PROVEEDOR_GOOGLE_DRIVE.equals(conexion.getProveedor())) {
            String token = valorCifradoVigente(conexion);
            if (token.isBlank()) return;
            clienteHttp().post().uri(UriComponentsBuilder.fromUriString(URL_REVOCAR_GOOGLE)
                    .queryParam("token", token).build().encode().toUri()).retrieve().toBodilessEntity();
            return;
        }
        String token = valorCifradoVigente(conexion);
        if (token.isBlank()) return;
        clienteHttp().post().uri(URI.create(URL_REVOCAR_MICROSOFT))
                .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
    }

    /** Devuelve el token vigente (acceso o refresh descifrado) para la revocación remota. */
    private String valorCifradoVigente(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        String material = conexion.getAccessTokenCifrado() != null && !conexion.getAccessTokenCifrado().isBlank()
                && conexion.getExpiraEn() != null && conexion.getExpiraEn().isAfter(Instant.now())
                ? conexion.getAccessTokenCifrado() : conexion.getRefreshTokenCifrado();
        return material == null || material.isBlank() ? "" : cifrado.descifrar(material);
    }

    private void validarCredencialNextcloud(String url, String usuario, String credencial) {
        String autorizacion = Base64.getEncoder().encodeToString(
                (usuario + ":" + credencial).getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create(url + "/remote.php/dav/");
        int estado;
        try {
            estado = clienteHttp().method(org.springframework.http.HttpMethod.valueOf("PROPFIND")).uri(uri)
                    .header("Authorization", "Basic " + autorizacion)
                    .header("OCS-APIRequest", "true")
                    .header("Depth", "0")
                    .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException error) {
            estado = error.getStatusCode().value();
        } catch (RuntimeException error) {
            throw new IllegalStateException("No se pudo contactar el servidor Nextcloud. Verifica la URL y vuelve a intentarlo.", error);
        }
        if (estado == 401 || estado == 403) {
            throw new IllegalArgumentException("El servidor Nextcloud rechazó el usuario o la contraseña de aplicación.");
        }
        if (estado < 200 || estado >= 300) {
            throw new IllegalStateException("El servidor Nextcloud respondió con un estado inesperado (" + estado + ").");
        }
    }

    private AlmacenamientoConexionDTO aDto(MAlmacenamientoConexion conexion) {
        String estado = conexion.getEstado();
        if (!"NEXTCLOUD".equals(conexion.getProveedor()) && !conexion.renovable()) estado = "REQUIERE_RECONEXION";
        return new AlmacenamientoConexionDTO(
                conexion.getIdConexion(),
                conexion.getProveedor(),
                valor(conexion.getEtiquetaCuenta()),
                valor(conexion.getUrlServidor()),
                estado,
                conexion.getConectadoEn() == null ? "" : conexion.getConectadoEn().toString(),
                conexion.getActualizadoEn() == null ? "" : conexion.getActualizadoEn().toString()
        );
    }

    private String uriRetorno() {
        String base = baseUrlWeb == null || baseUrlWeb.isBlank() ? "http://localhost:5173" : baseUrlWeb;
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/dashboard/almacenamiento";
    }

    private String generarState() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String normalizarProveedor(String proveedor) {
        String valor = proveedor == null ? "" : proveedor.trim().toUpperCase(Locale.ROOT);
        return switch (valor) {
            case "GOOGLE_DRIVE", "GOOGLEDRIVE", "DRIVE" -> PROVEEDOR_GOOGLE_DRIVE;
            case "ONEDRIVE", "MICROSOFT" -> PROVEEDOR_ONEDRIVE;
            case "NEXTCLOUD" -> PROVEEDOR_NEXTCLOUD;
            default -> throw new ProveedorNoSoportadoException("El proveedor de almacenamiento no está soportado.");
        };
    }

    private String normalizarUrlServidor(String valor) {
        String url = valor == null ? "" : valor.trim();
        if (url.isBlank()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        URI uri = URI.create(url);
        if (uri.getHost() == null) throw new IllegalArgumentException("La URL del servidor Nextcloud no es válida.");
        return url;
    }

    private String primeraOpcion(JsonNode a, JsonNode b) {
        String valorA = a == null ? "" : a.asText("");
        return valorA.isBlank() ? (b == null ? "" : b.asText("").trim()) : valorA.trim();
    }

    private JsonNode respuestaJson(RestClient.RequestHeadersSpec<?> solicitud, Class<JsonNode> tipo) {
        String contenido = solicitud.retrieve().body(String.class);
        if (contenido == null || contenido.isBlank()) return null;
        try {
            return objectMapper.readValue(contenido, tipo);
        } catch (IOException error) {
            throw new IllegalStateException("El proveedor devolvió una respuesta no válida.", error);
        }
    }

    private String valor(String valor) { return valor == null ? "" : valor; }

    /** Elimina estados de OAuth vencidos para no acumular memoria. */
    public void limpiarEstadosVencidos() {
        Instant ahora = Instant.now();
        estadosPendientes.entrySet().removeIf(entrada -> entrada.getValue().expiracion().isBefore(ahora));
    }

}
