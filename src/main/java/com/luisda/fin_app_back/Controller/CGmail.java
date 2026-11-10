package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.GmailProfileDTO;
import com.luisda.fin_app_back.DTO.GmailCuentaDTO;
import com.luisda.fin_app_back.DTO.GmailContenidoDTO;
import com.luisda.fin_app_back.DTO.GmailMensajeDTO;
import com.luisda.fin_app_back.DTO.GmailMovimientoAlertaDTO;
import com.luisda.fin_app_back.DTO.GmailSuscripcionDTO;
import com.luisda.fin_app_back.DTO.GoogleOAuthTokenDTO;
import com.luisda.fin_app_back.DTO.SolicitudAlertasGmailDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MGmailSuscripcion;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Repository.IGmailSuscripciones;
import com.luisda.fin_app_back.Services.ParserMovimientoBancolombia;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import com.google.auth.oauth2.TokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gestiona OAuth y consulta Gmail/Google Contacts con credenciales del usuario activo. */
@RestController
@Tag(name = "Gmail", description = "Conexión y consultas de Gmail y Google Contacts.")
@RequestMapping("/api/gmail")
public class CGmail {
    private static final String URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token";
    private static final String URL_PERFIL_GOOGLE = "https://gmail.googleapis.com/gmail/v1/users/me/profile";
    private static final int DURACION_TOKEN_SEGUNDOS_POR_DEFECTO = 3600;
    private static final int MARGEN_RENOVACION_SEGUNDOS = 60;
    private static final int RETENCION_CORREOS_DIAS = 90;
    private static final int TAMANO_PAGINA_GMAIL = 500;
    private static final Set<String> REMITENTES_PRIORITARIOS = Set.of(
            "nu@nu.com.co",
            "efacturahomecenter@homecenter.co",
            "serviciopse@achcolombia.com.co",
            "people@pasteur.com.co",
            "alertasynotificaciones@an.notificacionesbancolombia.com"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(CGmail.class);

    private volatile RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IFuentes fuentes;
    private final IGmailMensajes mensajes;
    private final IGmailSuscripciones suscripciones;
    private final SUsuarioActivo usuarioActivo;
    private final SMovimientoContable movimientosContables;
    private final ParserMovimientoBancolombia parserMovimiento = new ParserMovimientoBancolombia();
    private final SecureRandom random = new SecureRandom();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientId;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecret;
    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:client_secret_2_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}")
    private String archivoCredenciales;
    @Value("${finapp.cors.allowed-origins:http://localhost:5173}") private String origenesPermitidos;
    @Value("${finapp.google.pubsub.topic:}") private String temaPubSub;
    @Value("${finapp.google.pubsub.audience:}") private String audienciaPubSub;
    @Value("${finapp.google.pubsub.service-account:}") private String cuentaServicioPubSub;

    public CGmail(IFuentes fuentes, IGmailMensajes mensajes, IGmailSuscripciones suscripciones,
                  SUsuarioActivo usuarioActivo, SMovimientoContable movimientosContables) {
        this.fuentes = fuentes;
        this.mensajes = mensajes;
        this.suscripciones = suscripciones;
        this.usuarioActivo = usuarioActivo;
        this.movimientosContables = movimientosContables;
    }

    private RestClient clienteHttp() {
        RestClient cliente = clienteHttp;
        if (cliente != null) return cliente;
        synchronized (this) {
            if (clienteHttp == null) clienteHttp = RestClient.create();
            return clienteHttp;
        }
    }

    private <T> T respuestaJson(RestClient.RequestHeadersSpec<?> solicitud, Class<T> tipo) {
        String contenido = solicitud.retrieve().body(String.class);
        if (contenido == null || contenido.isBlank()) return null;
        try {
            return objectMapper.readValue(contenido, tipo);
        } catch (IOException error) {
            throw new RestClientException("Google devolvió una respuesta JSON no válida.", error);
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
            GoogleOAuthTokenDTO respuesta = respuestaJson(clienteHttp().post().uri(URL_TOKEN_GOOGLE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario), GoogleOAuthTokenDTO.class);
            String accessToken = respuesta == null || respuesta.accessToken() == null ? "" : respuesta.accessToken().trim();
            if (accessToken.isBlank()) return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Google no devolvió un token de acceso."));

            String correo = obtenerCorreoGoogle(accessToken);
            List<MFuente> coincidenciasFuente = fuentes.findByUsuario_IdUsuarioAndTipoIgnoreCaseAndContenidoIgnoreCase(
                    usuario.getIdUsuario(), "CORREO", correo);
            MFuente fuente;
            if (solicitud.expectedAccountId() != null && !solicitud.expectedAccountId().isBlank()) {
                fuente = fuentes.findByIdFuenteAndUsuario_IdUsuario(solicitud.expectedAccountId(), usuario.getIdUsuario()).orElse(null);
                if (fuente == null || !"CORREO".equalsIgnoreCase(fuente.getTipo())
                        || !fuente.getContenido().equalsIgnoreCase(correo)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                            "message", "Google autorizó otra cuenta. Vuelve a intentarlo con la misma cuenta registrada en Fuentes."
                    ));
                }
            } else {
                fuente = coincidenciasFuente.stream().filter(MFuente::tieneConexionGmail).findFirst()
                        .orElseGet(() -> coincidenciasFuente.stream().findFirst().orElseGet(() -> {
                            MFuente nueva = new MFuente(UUID.randomUUID().toString(), "CORREO", correo, true, LocalDate.now());
                            nueva.setUsuario(usuario);
                            return nueva;
                        }));
            }
            String refreshToken = respuesta.refreshToken() == null ? "" : respuesta.refreshToken().trim();
            if (refreshToken.isBlank() && !fuente.tieneConexionGmail()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "message", "Google no entregó un refresh token. Vuelve a conectar y acepta el consentimiento de Gmail."
                ));
            }
            long expiraEn = respuesta.expiresIn() == null || respuesta.expiresIn() <= 0
                    ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : respuesta.expiresIn();
            fuente.setTipo("CORREO");
            fuente.setContenido(correo);
            fuente.setEstado(true);
            fuente.setAccessTokenCifrado(cifrar(accessToken, credenciales.clientSecret()));
            if (!refreshToken.isBlank()) fuente.setRefreshTokenCifrado(cifrar(refreshToken, credenciales.clientSecret()));
            fuente.setExpiraEn(Instant.now().plusSeconds(expiraEn));
            fuente = fuentes.save(fuente);

            Map<String, Object> respuestaConexion = new LinkedHashMap<>();
            respuestaConexion.put("email", correo);
            respuestaConexion.put("account_id", fuente.getIdFuente());
            respuestaConexion.put("expires_at", fuente.getExpiraEn().toString());
            if (pubsubConfigurado()) {
                try {
                    registrarWatch(fuente, accessToken);
                } catch (Exception error) {
                    LOGGER.error("No se pudo registrar el watch de Gmail para la cuenta {}", correo, error);
                    respuestaConexion.put("push_warning", "La cuenta se conectó, pero no se pudo iniciar su sincronización automática.");
                }
            } else if (!temaPubSub.isBlank()) {
                respuestaConexion.put("push_warning", "Completa la configuración OIDC de Pub/Sub para iniciar la sincronización automática.");
            }
            return ResponseEntity.ok(respuestaConexion);
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

    /** Lista fuentes CORREO con una autorización Gmail renovable. */
    @GetMapping("/cuentas")
    public ResponseEntity<?> listarCuentas(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        return ResponseEntity.ok(fuentes.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(fuente -> "CORREO".equalsIgnoreCase(fuente.getTipo()) && fuente.tieneConexionGmail())
                .map(fuente -> new GmailCuentaDTO(fuente.getIdFuente(), fuente.getContenido())).toList());
    }

    /** Devuelve los mensajes persistidos para las direcciones propias solicitadas. */
    @PostMapping("/cuentas/{cuentaId}/mensajes")
    public ResponseEntity<?> consultarMensajes(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                               @RequestBody(required = false) SolicitudMensajes solicitud,
                                               HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        List<String> direcciones = new ArrayList<>();
        if (solicitud != null && solicitud.fuentesIds() != null) {
            if (solicitud.fuentesIds().size() > 25) return ResponseEntity.badRequest()
                    .body(Map.of("message", "Solo se pueden consultar hasta 25 fuentes a la vez."));
            for (String idFuente : solicitud.fuentesIds()) {
                if (idFuente == null || idFuente.isBlank()) continue;
                MFuente fuente = fuentes.findByIdFuenteAndUsuario_IdUsuario(idFuente, usuario.getIdUsuario()).orElse(null);
                if (fuente == null || !"CORREO".equalsIgnoreCase(fuente.getTipo())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Una de las fuentes de correo no pertenece a tu cuenta."));
                }
                if (fuente.isEstado() && direccionCorreoValida(fuente.getContenido())) direcciones.add(fuente.getContenido().trim());
            }
        }
        direcciones.add(cuenta.getContenido().trim());
        try {
            return ResponseEntity.ok(mensajesCacheados(cuenta, direcciones));
        } catch (IOException error) {
            LOGGER.error("No se pudieron convertir los mensajes guardados de la cuenta Gmail {}", cuentaId, error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudieron leer los mensajes guardados."));
        }
    }

    @GetMapping("/cuentas/{cuentaId}/mensajes/{mensajeId}/contenido")
    public ResponseEntity<?> obtenerContenidoMensaje(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                                    @org.springframework.web.bind.annotation.PathVariable String mensajeId,
                                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        if (mensajeId == null || !mensajeId.matches("[A-Za-z0-9_-]{1,256}")) {
            return ResponseEntity.badRequest().body(Map.of("message", "El identificador del correo no es válido."));
        }
        var mensajeCacheado = mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
        if (mensajeCacheado.isPresent() && mensajeCacheado.get().getFechaInterna()
                .isAfter(Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS)))) {
            try {
                return ResponseEntity.ok(new GmailContenidoDTO(descifrar(mensajeCacheado.get().getContenidoCifrado(),
                        resolverCredenciales().clientSecret())));
            } catch (IOException | GeneralSecurityException error) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo descifrar el correo guardado."));
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "El contenido todavía no está sincronizado en la base de datos."));
    }

    @GetMapping("/cuentas/{cuentaId}/contactos")
    public ResponseEntity<?> consultarContactosGoogle(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                                      HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        try {
            String token = tokenAccesoActual(cuenta);
            List<Map<String, String>> contactos = new ArrayList<>();
            String pageToken = "";
            do {
                var builder = UriComponentsBuilder.fromUriString("https://people.googleapis.com/v1/people/me/connections")
                        .queryParam("personFields", "names,emailAddresses,phoneNumbers,biographies,organizations")
                        .queryParam("pageSize", 1000);
                if (!pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
                JsonNode pagina = respuestaJson(clienteHttp().get().uri(builder.build().encode().toUri())
                        .headers(h -> h.setBearerAuth(token)), JsonNode.class);
                JsonNode conexiones = pagina == null ? null : pagina.path("connections");
                if (conexiones != null && conexiones.isArray()) {
                    for (JsonNode persona : conexiones) {
                        JsonNode nombre = primerNodo(persona.path("names"), "displayName");
                        JsonNode correo = primerNodo(persona.path("emailAddresses"), "value");
                        JsonNode telefono = primerNodo(persona.path("phoneNumbers"), "canonicalForm");
                        if (telefono == null) telefono = primerNodo(persona.path("phoneNumbers"), "value");
                        JsonNode biografia = primerNodo(persona.path("biographies"), "value");
                        JsonNode organizacion = primeraOrganizacion(persona.path("organizations"));
                        contactos.add(Map.of(
                                "googleResourceName", persona.path("resourceName").asText(""),
                                "nombre", valorNodo(nombre, "displayName", valorNodo(correo, "value", "Contacto de Google")),
                                "correo", valorNodo(correo, "value", ""),
                                "telefono", valorNodo(telefono, "canonicalForm", valorNodo(telefono, "value", "")),
                                "notas", valorNodo(biografia, "value", ""),
                                "cargo", valorNodo(organizacion, "title", ""),
                                "empresa", valorNodo(organizacion, "name", "")
                        ));
                    }
                }
                pageToken = pagina == null ? "" : pagina.path("nextPageToken").asText("");
            } while (!pageToken.isBlank());
            return ResponseEntity.ok(contactos);
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", mensajeErrorGoogle(error, "No se pudieron consultar los contactos de Google.")));
        } catch (CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo renovar la conexión de Gmail."));
        }
    }

    @GetMapping("/cuentas/{cuentaId}/contactos/permiso")
    public ResponseEntity<?> verificarPermisoContactos(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                                       HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        try {
            String token = tokenAccesoActual(cuenta);
            URI uri = UriComponentsBuilder.fromUriString("https://people.googleapis.com/v1/people/me/connections")
                    .queryParam("personFields", "names").queryParam("pageSize", 1).build().encode().toUri();
            clienteHttp().get().uri(uri).headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
            return ResponseEntity.ok(Map.of("autorizado", true));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 403) return ResponseEntity.ok(Map.of("autorizado", false));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", mensajeErrorGoogle(error, "No se pudo verificar el permiso de Contactos de Google.")));
        } catch (CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo renovar la conexión de Gmail."));
        }
    }

    /** Devuelve exclusivamente suscripciones Gmail persistidas de las cuentas del usuario. */
    @GetMapping("/suscripciones")
    public ResponseEntity<?> listarSuscripciones(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        List<MFuente> cuentas = fuentes.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()) && f.tieneConexionGmail()).toList();
        List<GmailSuscripcionDTO> resultado = new ArrayList<>();
        try {
            for (MFuente cuenta : cuentas) resultado.addAll(suscripcionesCacheadas(cuenta));
        } catch (IOException error) {
            LOGGER.error("No se pudieron leer las suscripciones Gmail guardadas del usuario {}", usuario.getIdUsuario(), error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudieron leer las suscripciones guardadas."));
        }
        return ResponseEntity.ok(Map.of("suscripciones", resultado, "erroresCuentas", List.of(),
                "cuentasConsultadas", cuentas.size()));
    }

    /** Lista los mensajes cacheados del remitente de una suscripción activa de la cuenta indicada. */
    @GetMapping("/cuentas/{cuentaId}/suscripciones/mensajes")
    public ResponseEntity<?> listarMensajesSuscripcion(
            @org.springframework.web.bind.annotation.PathVariable String cuentaId,
            @org.springframework.web.bind.annotation.RequestParam String correo,
            HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        String remitente = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (!direccionCorreoValida(remitente)) return ResponseEntity.badRequest()
                .body(Map.of("message", "La dirección del remitente no es válida."));
        if (suscripciones.findByCuenta_IdFuenteAndCorreoRemitente(cuentaId, remitente).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No se encontró una suscripción activa para ese remitente."));
        }

        try {
            String token = tokenAccesoActual(cuenta);
            java.util.Set<String> consultados = new java.util.HashSet<>();
            recorrerMensajes(token, "newer_than:" + RETENCION_CORREOS_DIAS + "d from:" + remitente, resumen -> {
                String id = resumen.path("id").asText("");
                if (id.isBlank() || !consultados.add(id)
                        || mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuentaId, id).isPresent()) return;
                guardarMensajeGmail(cuenta, token, id);
            });

            Instant desde = Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS));
            List<GmailMensajeDTO> resultado = mensajes
                    .findByCuenta_IdFuenteAndFechaInternaAfterOrderByFechaInternaDesc(cuentaId, desde).stream()
                    .filter(mensaje -> remitente.equals(extraerDireccionRemitente(mensaje.getRemitente())))
                    .map(mensaje -> {
                        try {
                            return new GmailMensajeDTO(mensaje.getGoogleMessageId(), valorSeguro(mensaje.getRemitente()),
                                    valorSeguro(mensaje.getDestinatario()), valorSeguro(mensaje.getAsunto()),
                                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                                            .format(mensaje.getFechaInterna().atZone(java.time.ZoneOffset.UTC)),
                                    valorSeguro(mensaje.getFragmento()), leerListaJson(mensaje.getEtiquetasJson()),
                                    leerListaJson(mensaje.getNombresEtiquetasJson()));
                        } catch (IOException error) {
                            throw new IllegalStateException("No se pudieron leer las etiquetas del correo.", error);
                        }
                    }).toList();
            return ResponseEntity.ok(resultado);
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", mensajeErrorGoogle(error, "No se pudieron sincronizar los mensajes de esta suscripción.")));
        } catch (CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "No se pudieron sincronizar los mensajes de esta suscripción."));
        }
    }

    @GetMapping("/movimientos/notificaciones")
    public ResponseEntity<?> listarNotificacionesMovimientos(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        List<GmailMovimientoAlertaDTO> alertas = mensajes
                .findByCuenta_Usuario_IdUsuarioAndMovimientoAlertaPendienteTrueOrderByFechaInternaDesc(usuario.getIdUsuario())
                .stream()
                .map(mensaje -> new GmailMovimientoAlertaDTO(
                        mensaje.getId(), mensaje.getMovimientoAutomaticoId(), mensaje.getAsunto(),
                        mensaje.getRemitente(), mensaje.getFechaInterna().toString()))
                .toList();
        return ResponseEntity.ok(alertas);
    }

    @PostMapping("/movimientos/notificaciones/confirmar")
    public ResponseEntity<?> confirmarNotificacionesMovimientos(
            @RequestBody(required = false) SolicitudAlertasGmailDTO solicitud, HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        if (solicitud == null || solicitud.ids() == null || solicitud.ids().size() > 100)
            return ResponseEntity.badRequest().body(Map.of("message", "La lista de alertas no es válida."));
        for (String id : solicitud.ids()) {
            if (id == null || id.isBlank()) continue;
            mensajes.findByIdAndCuenta_Usuario_IdUsuario(id, usuario.getIdUsuario()).ifPresent(mensaje -> {
                mensaje.setMovimientoAlertaPendiente(false);
                mensajes.save(mensaje);
            });
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cuentas/{cuentaId}/watch")
    public ResponseEntity<?> activarNotificaciones(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail conectada."));
        }
        if (!pubsubConfigurado()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Configura el tópico, audiencia OIDC y cuenta de servicio de Pub/Sub."));
        try {
            String token = tokenAccesoActual(cuenta);
            registrarWatch(cuenta, token);
            return ResponseEntity.ok(Map.of("watch_expira_en", cuenta.getGmailWatchExpiraEn().toString()));
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", mensajeErrorGoogle(error, "No se pudo activar la sincronización push de Gmail.")));
        } catch (CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo preparar la cuenta para la sincronización push."));
        }
    }

    @PostMapping("/webhook/pubsub")
    public ResponseEntity<?> recibirNotificacionPubSub(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion,
            @RequestBody(required = false) JsonNode cuerpo) {
        if (audienciaPubSub.isBlank() || cuentaServicioPubSub.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "La validación OIDC de Pub/Sub no está configurada."));
        }
        try {
            verificarTokenPubSub(autorizacion);
        } catch (TokenVerifier.VerificationException | IllegalArgumentException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token OIDC de Pub/Sub inválido."));
        } catch (IOException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No se pudo validar el token OIDC de Pub/Sub."));
        }
        String datosCodificados = cuerpo == null ? "" : cuerpo.path("message").path("data").asText("");
        if (datosCodificados.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "La notificación Pub/Sub no incluye datos Gmail."));
        try {
            JsonNode evento = objectMapper.readTree(Base64.getDecoder().decode(datosCodificados));
            String correo = evento.path("emailAddress").asText("").trim();
            String historyId = evento.path("historyId").asText("").trim();
            if (correo.isBlank() || historyId.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "La notificación Gmail no contiene cuenta e historial."));
            List<MFuente> cuentas = fuentes.findByTipoIgnoreCaseAndContenidoIgnoreCase("CORREO", correo)
                    .stream().filter(MFuente::tieneConexionGmail).toList();
            for (MFuente cuenta : cuentas) {
                procesarNotificacionGmail(cuenta, historyId);
                procesarMovimientosGmailPendientes(cuenta);
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", "Los datos de la notificación Gmail no son válidos."));
        } catch (CuentaGmailRequiereReconectarException error) {
            LOGGER.warn("Se omitió un evento Gmail para una cuenta que requiere reconexión.");
            return ResponseEntity.noContent().build();
        } catch (IOException | GeneralSecurityException | RestClientResponseException error) {
            LOGGER.error("No se pudo procesar una notificación push de Gmail", error);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "No se pudo sincronizar la notificación Gmail; Pub/Sub puede reintentar."));
        }
    }

    @Scheduled(fixedDelayString = "${finapp.google.pubsub.renew-interval-ms:86400000}", initialDelayString = "1000")
    public void renovarWatchGmail() {
        if (!pubsubConfigurado()) return;
        Instant limite = Instant.now().plusSeconds(TimeUnit.DAYS.toSeconds(1));
        for (MFuente cuenta : fuentes.findAll().stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()) && f.tieneConexionGmail())
                .filter(f -> f.getGmailSincronizadoEn() == null || f.getGmailWatchExpiraEn() == null
                        || f.getGmailWatchExpiraEn().isBefore(limite))
                .toList()) {
            try {
                String token = tokenAccesoActual(cuenta);
                registrarWatch(cuenta, token);
                if (cuenta.getGmailSincronizadoEn() == null) sincronizarInicialMensajes(cuenta, token);
            } catch (IOException | GeneralSecurityException | RestClientException | IllegalStateException error) {
                LOGGER.error("No se pudo renovar watch Gmail para la cuenta {}", cuenta.getIdFuente(), error);
            }
        }
    }

    @Scheduled(fixedDelayString = "${finapp.google.sync-interval-ms:300000}", initialDelayString = "30000")
    public void sincronizarCuentasGmail() {
        List<MFuente> cuentas = fuentes.findAll().stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()) && f.tieneConexionGmail())
                .toList();
        for (MFuente cuenta : cuentas) {
            try {
                sincronizarSuscripcionesGuardadas(cuenta);
                String token = tokenAccesoActual(cuenta);
                if (cuenta.getGmailHistoryId() == null || cuenta.getGmailHistoryId().isBlank()
                        || cuenta.getGmailSincronizadoEn() == null) {
                    if (pubsubConfigurado() && !watchActivo(cuenta)) registrarWatch(cuenta, token);
                    if (cuenta.getGmailHistoryId() == null || cuenta.getGmailHistoryId().isBlank())
                        cuenta.setGmailHistoryId(obtenerHistoryIdGmail(token));
                    sincronizarInicialMensajes(cuenta, token);
                } else {
                    String historyIdActual = obtenerHistoryIdGmail(token);
                    if (!historyIdActual.equals(cuenta.getGmailHistoryId()))
                        procesarNotificacionGmail(cuenta, historyIdActual);
                    if (pubsubConfigurado() && (cuenta.getGmailWatchExpiraEn() == null
                            || cuenta.getGmailWatchExpiraEn().isBefore(Instant.now().plusSeconds(
                                    TimeUnit.DAYS.toSeconds(1))))) registrarWatch(cuenta, token);
                }
                procesarMovimientosGmailPendientes(cuenta);
            } catch (CuentaGmailRequiereReconectarException error) {
                LOGGER.warn("La cuenta Gmail {} requiere reconexión.", cuenta.getIdFuente());
            } catch (IOException | GeneralSecurityException | RestClientException | IllegalStateException error) {
                LOGGER.error("No se pudo sincronizar en segundo plano la cuenta Gmail {}", cuenta.getIdFuente(), error);
            }
        }
    }

    @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT1S")
    public void purgarCacheGmailExpirada() {
        fuentes.findAll().stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()))
                .forEach(cuenta -> {
                    try {
                        purgarMensajesAntiguos(cuenta);
                    } catch (RuntimeException error) {
                        LOGGER.error("No se pudo purgar la caché Gmail expirada para la cuenta {}", cuenta.getContenido(), error);
                    }
                });
    }

    private void verificarTokenPubSub(String autorizacion) throws IOException, TokenVerifier.VerificationException {
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) throw new IllegalArgumentException("Falta token bearer.");
        String jwt = autorizacion.substring("Bearer ".length()).trim();
        TokenVerifier.newBuilder().setAudience(audienciaPubSub).setIssuer("https://accounts.google.com").build().verify(jwt);
        String[] partes = jwt.split("\\.");
        if (partes.length != 3) throw new IllegalArgumentException("Token OIDC inválido.");
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(partes[1]));
        if (!cuentaServicioPubSub.equals(claims.path("email").asText())
                || !claims.path("email_verified").asBoolean(false)) {
            throw new IllegalArgumentException("El token no pertenece a la cuenta de servicio de Pub/Sub configurada.");
        }
    }

    private void registrarWatch(MFuente cuenta, String token) {
        if (temaPubSub.isBlank()) throw new IllegalStateException("Falta configurar el tópico de Pub/Sub.");
        JsonNode respuesta = respuestaJson(clienteHttp().post()
                .uri("https://gmail.googleapis.com/gmail/v1/users/me/watch")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("topicName", temaPubSub, "labelIds", List.of("INBOX"))), JsonNode.class);
        String historyId = respuesta == null ? "" : respuesta.path("historyId").asText("");
        long expiration = respuesta == null ? 0 : respuesta.path("expiration").asLong(0);
        if (historyId.isBlank() || expiration <= 0) throw new IllegalStateException("Gmail devolvió un watch incompleto.");
        cuenta.setGmailHistoryId(historyId);
        cuenta.setGmailWatchExpiraEn(Instant.ofEpochMilli(expiration));
        fuentes.save(cuenta);
    }

    private String obtenerHistoryIdGmail(String token) {
        JsonNode perfil = respuestaJson(clienteHttp().get().uri(URL_PERFIL_GOOGLE)
                .headers(headers -> headers.setBearerAuth(token)), JsonNode.class);
        String historyId = perfil == null ? "" : perfil.path("historyId").asText("");
        if (historyId.isBlank()) throw new RestClientException("Gmail no devolvió el cursor del historial.");
        return historyId;
    }

    private void sincronizarInicialMensajes(MFuente cuenta, String token) throws IOException, GeneralSecurityException {
        List<String> direcciones = new ArrayList<>(fuentes.findByUsuario_IdUsuario(cuenta.getUsuario().getIdUsuario()).stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()) && f.isEstado() && direccionCorreoValida(f.getContenido()))
                .map(f -> f.getContenido().trim()).distinct().toList());
        direcciones.add(cuenta.getContenido().trim());
        if (direcciones.isEmpty()) direcciones = List.of(cuenta.getContenido());
        String consulta = direcciones.stream().map(d -> "to:" + d + " cc:" + d + " deliveredto:" + d)
                .collect(java.util.stream.Collectors.joining(" "));
        String consultaRemitentesPrioritarios = REMITENTES_PRIORITARIOS.stream()
                .map(remitente -> "from:" + remitente)
                .collect(java.util.stream.Collectors.joining(" "));
        java.util.Set<String> mensajesProcesados = new java.util.HashSet<>();
        recorrerMensajes(token, "newer_than:" + RETENCION_CORREOS_DIAS + "d {" + consulta + " "
                        + consultaRemitentesPrioritarios + " is:important}",
                resumen -> guardarMensajeListado(cuenta, token, resumen, mensajesProcesados));
        recorrerMensajes(token, "newer_than:" + RETENCION_CORREOS_DIAS + "d {category:promotions category:updates}",
                resumen -> guardarMensajeListado(cuenta, token, resumen, mensajesProcesados));
        List<String> remitentesSuscritos = suscripciones.findByCuenta_IdFuenteOrderByUltimaActividadDesc(cuenta.getIdFuente()).stream()
                .map(MGmailSuscripcion::getCorreoRemitente)
                .filter(this::direccionCorreoValida)
                .distinct()
                .map(remitente -> "from:" + remitente)
                .toList();
        for (int inicio = 0; inicio < remitentesSuscritos.size(); inicio += 25) {
            String consultaRemitentesSuscritos = String.join(" ", remitentesSuscritos.subList(
                    inicio, Math.min(inicio + 25, remitentesSuscritos.size())));
            recorrerMensajes(token, "newer_than:" + RETENCION_CORREOS_DIAS + "d {" + consultaRemitentesSuscritos + "}",
                    resumen -> guardarMensajeListado(cuenta, token, resumen, mensajesProcesados));
        }
        purgarMensajesAntiguos(cuenta);
        cuenta.setGmailSincronizadoEn(Instant.now());
        fuentes.save(cuenta);
    }

    private void guardarMensajeListado(MFuente cuenta, String token, JsonNode resumen,
                                       java.util.Set<String> mensajesProcesados)
            throws IOException, GeneralSecurityException {
        String id = resumen.path("id").asText("");
        if (!id.isBlank() && mensajesProcesados.add(id)) guardarMensajeGmail(cuenta, token, id);
    }

    private void recorrerMensajes(String token, String consulta, ProcesadorMensajeListado procesador)
            throws IOException, GeneralSecurityException {
        String pageToken = "";
        java.util.Set<String> tokensProcesados = new java.util.HashSet<>();
        do {
            var builder = UriComponentsBuilder.fromUriString("https://gmail.googleapis.com/gmail/v1/users/me/messages")
                    .queryParam("q", consulta)
                    .queryParam("maxResults", TAMANO_PAGINA_GMAIL);
            if (!pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
            JsonNode pagina = respuestaJson(clienteHttp().get().uri(builder.build().encode().toUri())
                    .headers(headers -> headers.setBearerAuth(token)), JsonNode.class);
            JsonNode lista = pagina == null ? null : pagina.path("messages");
            if (lista != null && lista.isArray()) {
                for (JsonNode resumen : lista) procesador.procesar(resumen);
            }
            pageToken = pagina == null ? "" : pagina.path("nextPageToken").asText("");
            if (!pageToken.isBlank() && !tokensProcesados.add(pageToken))
                throw new RestClientException("Gmail devolvió un token de paginación repetido.");
        } while (!pageToken.isBlank());
    }

    @FunctionalInterface
    private interface ProcesadorMensajeListado {
        void procesar(JsonNode resumen) throws IOException, GeneralSecurityException;
    }

    private void procesarNotificacionGmail(MFuente cuenta, String historyIdNotificado)
            throws IOException, GeneralSecurityException {
        if (cuenta.getGmailHistoryId() == null || cuenta.getGmailHistoryId().isBlank()
                || cuenta.getGmailSincronizadoEn() == null) {
            String tokenInicial = tokenAccesoActual(cuenta);
            if (pubsubConfigurado() && !watchActivo(cuenta)) registrarWatch(cuenta, tokenInicial);
            if (cuenta.getGmailHistoryId() == null || cuenta.getGmailHistoryId().isBlank())
                cuenta.setGmailHistoryId(historyIdNotificado);
            sincronizarInicialMensajes(cuenta, tokenInicial);
            return;
        }
        String token = tokenAccesoActual(cuenta);
        String cursor = cuenta.getGmailHistoryId();
        String pageToken = "";
        List<String> mensajesActualizados = new ArrayList<>();
        List<String> mensajesEliminados = new ArrayList<>();
        try {
            do {
                var builder = UriComponentsBuilder.fromUriString("https://gmail.googleapis.com/gmail/v1/users/me/history")
                        .queryParam("startHistoryId", cursor);
                if (!pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
                JsonNode pagina = respuestaJson(clienteHttp().get().uri(builder.build().encode().toUri())
                        .headers(h -> h.setBearerAuth(token)), JsonNode.class);
                JsonNode historial = pagina == null ? null : pagina.path("history");
                if (historial != null && historial.isArray()) for (JsonNode registro : historial) {
                    recolectarIdsHistorial(registro.path("messagesAdded"), "message", mensajesActualizados);
                    recolectarIdsHistorial(registro.path("labelsAdded"), "message", mensajesActualizados);
                    recolectarIdsHistorial(registro.path("labelsRemoved"), "message", mensajesActualizados);
                    recolectarIdsHistorial(registro.path("messagesDeleted"), "message", mensajesEliminados);
                }
                pageToken = pagina == null ? "" : pagina.path("nextPageToken").asText("");
            } while (!pageToken.isBlank());
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() != 404) throw error;
            registrarWatch(cuenta, token);
            sincronizarInicialMensajes(cuenta, token);
            return;
        }
        for (String id : mensajesEliminados) mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), id);
        for (String id : mensajesActualizados.stream().distinct()
                .filter(id -> !mensajesEliminados.contains(id)).toList()) guardarMensajeGmail(cuenta, token, id);
        cuenta.setGmailHistoryId(historyIdNotificado);
        cuenta.setGmailSincronizadoEn(Instant.now());
        fuentes.save(cuenta);
        purgarMensajesAntiguos(cuenta);
    }

    private void recolectarIdsHistorial(JsonNode eventos, String campo, List<String> ids) {
        if (eventos == null || !eventos.isArray()) return;
        for (JsonNode evento : eventos) {
            String id = evento.path(campo).path("id").asText("");
            if (!id.isBlank()) ids.add(id);
        }
    }

    private void guardarMensajeGmail(MFuente cuenta, String token, String mensajeId)
            throws IOException, GeneralSecurityException {
        URI uri = UriComponentsBuilder.fromUriString("https://gmail.googleapis.com/gmail/v1/users/me/messages")
                .pathSegment(mensajeId).queryParam("format", "full").build().encode().toUri();
        JsonNode mensaje = respuestaJson(clienteHttp().get().uri(uri).headers(h -> h.setBearerAuth(token)), JsonNode.class);
        if (!mensajePerteneceACache(cuenta, mensaje)) {
            mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
            return;
        }
        persistirMensajeGmail(cuenta, mensaje);
    }

    private boolean mensajePerteneceACache(MFuente cuenta, JsonNode mensaje) {
        if (mensaje == null) return false;
        JsonNode headers = mensaje.path("payload").path("headers");
        String remitente = extraerDireccionRemitente(header(headers, "From"));
        if (esRemitentePrioritario(remitente)
                || (!remitente.isBlank() && suscripciones.findByCuenta_IdFuenteAndCorreoRemitente(
                        cuenta.getIdFuente(), remitente).isPresent())) return true;
        JsonNode labels = mensaje.path("labelIds");
        if (labels != null && labels.isArray()) {
            for (JsonNode label : labels) if ("IMPORTANT".equals(label.asText())) return true;
        }
        List<String> direcciones = new ArrayList<>(fuentes.findByUsuario_IdUsuario(cuenta.getUsuario().getIdUsuario()).stream()
                .filter(f -> "CORREO".equalsIgnoreCase(f.getTipo()) && f.isEstado() && direccionCorreoValida(f.getContenido()))
                .map(f -> f.getContenido().trim()).toList());
        direcciones.add(cuenta.getContenido().trim());
        if (direcciones.stream().anyMatch(d -> contieneDireccion(
                List.of("To", "Cc", "Delivered-To", "X-Original-To").stream()
                        .map(nombre -> header(headers, nombre)).collect(java.util.stream.Collectors.joining(", ")), d))) {
            return true;
        }
        if (labels == null || !labels.isArray()) return false;
        boolean esPromocionOActualizacion = false;
        for (JsonNode label : labels) {
            String id = label.asText();
            if (id.equals("CATEGORY_PROMOTIONS") || id.equals("CATEGORY_UPDATES")) {
                esPromocionOActualizacion = true;
                break;
            }
        }
        return esPromocionOActualizacion && !header(headers, "List-Unsubscribe").isBlank();
    }

    private boolean esRemitentePrioritario(String remitente) {
        if (remitente == null || remitente.isBlank()) return false;
        return REMITENTES_PRIORITARIOS.contains(remitente.toLowerCase(Locale.ROOT));
    }

    private String extraerDireccionRemitente(String remitente) {
        if (remitente == null || remitente.isBlank()) return "";
        var matcher = java.util.regex.Pattern.compile("(?i)[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
                .matcher(remitente);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
    }

    private void persistirMensajeGmail(MFuente cuenta, JsonNode mensaje) throws IOException, GeneralSecurityException {
        if (mensaje == null || mensaje.path("id").asText("").isBlank()) return;
        String mensajeId = mensaje.path("id").asText("");
        long timestamp = mensaje.path("internalDate").asLong(System.currentTimeMillis());
        Instant fecha = Instant.ofEpochMilli(timestamp);
        if (fecha.isBefore(Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS)))) {
            mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
            return;
        }
        MGmailMensaje entidad = mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId)
                .orElseGet(() -> new MGmailMensaje(UUID.randomUUID().toString(), cuenta, mensajeId, fecha));
        JsonNode headers = mensaje.path("payload").path("headers");
        List<String> etiquetasIds = new ArrayList<>();
        JsonNode etiquetas = mensaje.path("labelIds");
        if (etiquetas.isArray()) etiquetas.forEach(item -> etiquetasIds.add(item.asText()));
        entidad.setRemitente(header(headers, "From"));
        entidad.setDestinatario(List.of("To", "Cc", "Delivered-To", "X-Original-To").stream()
                .map(nombre -> header(headers, nombre)).filter(valor -> !valor.isBlank())
                .collect(java.util.stream.Collectors.joining(", ")));
        entidad.setAsunto(header(headers, "Subject").isBlank() ? "(Sin asunto)" : header(headers, "Subject"));
        entidad.setFragmento(mensaje.path("snippet").asText(""));
        entidad.setEtiquetasJson(objectMapper.writeValueAsString(etiquetasIds));
        entidad.setNombresEtiquetasJson(objectMapper.writeValueAsString(etiquetasIds.stream()
                .map(this::nombreEtiqueta).toList()));
        entidad.setListUnsubscribe(header(headers, "List-Unsubscribe"));
        entidad.setListUnsubscribePost(header(headers, "List-Unsubscribe-Post"));
        entidad.setContenidoCifrado(cifrar(extraerTextoMensaje(mensaje.path("payload")), resolverCredenciales().clientSecret()));
        entidad.setFechaInterna(fecha);
        mensajes.save(entidad);
        persistirSuscripcion(cuenta, mensaje);
    }

    private void procesarMovimientosGmailPendientes(MFuente cuenta) {
        Instant desde = Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS));
        String secreto;
        try {
            secreto = resolverCredenciales().clientSecret();
        } catch (IOException error) {
            LOGGER.error("No se pudieron preparar credenciales para analizar movimientos de Gmail de la cuenta {}",
                    cuenta.getIdFuente(), error);
            return;
        }
        List<MGmailMensaje> lote;
        do {
            lote = mensajes.findTop500ByCuenta_IdFuenteAndFechaInternaAfterAndMovimientoAutomaticoEstadoOrderByFechaInternaAsc(
                    cuenta.getIdFuente(), desde, "PENDIENTE");
            for (MGmailMensaje mensaje : lote) procesarMovimientoGmail(cuenta, mensaje, secreto);
        } while (lote.size() == 500);
    }

    private void procesarMovimientoGmail(MFuente cuenta, MGmailMensaje mensaje, String secreto) {
        try {
            String contenido = descifrar(mensaje.getContenidoCifrado(), secreto);
            var movimiento = parserMovimiento.extraer(mensaje.getAsunto(), contenido);
            if (movimiento.isEmpty()) {
                mensaje.setMovimientoAutomaticoEstado("IGNORADO");
                mensajes.save(mensaje);
                return;
            }
            String origenId = (cuenta.getIdFuente() + ":" + mensaje.getGoogleMessageId());
            if (origenId.length() > 180) origenId = origenId.substring(0, 180);
            var sugerencia = movimiento.get();
            var registrado = movimientosContables.crearAutomaticamenteDesdeGmail(
                    cuenta.getUsuario().getIdUsuario(), sugerencia.tipo(),
                    LocalDate.ofInstant(mensaje.getFechaInterna(), java.time.ZoneId.of("America/Bogota")),
                    sugerencia.monto(), sugerencia.categoria(), sugerencia.concepto(), origenId,
                    mensaje.getRemitente() == null || mensaje.getRemitente().isBlank()
                            ? "Bancolombia" : mensaje.getRemitente());
            if (registrado.isEmpty()) {
                mensaje.setMovimientoAutomaticoEstado("PENDIENTE_BOLSILLO");
            } else {
                mensaje.setMovimientoAutomaticoId(registrado.get().idMovimiento());
                mensaje.setMovimientoAutomaticoEstado("REGISTRADO");
                mensaje.setMovimientoAlertaPendiente(true);
            }
            mensajes.save(mensaje);
        } catch (IllegalStateException error) {
            if (error.getMessage() != null && error.getMessage().contains("ya fue importado")) {
                mensaje.setMovimientoAutomaticoEstado("DUPLICADO");
                mensajes.save(mensaje);
                return;
            }
            marcarErrorProcesamientoMovimiento(mensaje);
            LOGGER.error("No se pudo registrar automáticamente el movimiento del correo {} de la cuenta {}",
                    mensaje.getGoogleMessageId(), cuenta.getIdFuente(), error);
        } catch (GeneralSecurityException | RuntimeException error) {
            marcarErrorProcesamientoMovimiento(mensaje);
            LOGGER.error("No se pudo analizar automáticamente el correo {} de la cuenta {}",
                    mensaje.getGoogleMessageId(), cuenta.getIdFuente(), error);
        }
    }

    private void marcarErrorProcesamientoMovimiento(MGmailMensaje mensaje) {
        mensaje.setMovimientoAutomaticoEstado("ERROR");
        mensajes.save(mensaje);
    }

    private boolean watchActivo(MFuente cuenta) {
        return cuenta.getGmailHistoryId() != null && !cuenta.getGmailHistoryId().isBlank()
                && cuenta.getGmailWatchExpiraEn() != null && cuenta.getGmailWatchExpiraEn().isAfter(Instant.now())
                && cuenta.getGmailSincronizadoEn() != null;
    }

    private boolean pubsubConfigurado() {
        return !temaPubSub.isBlank() && !audienciaPubSub.isBlank() && !cuentaServicioPubSub.isBlank();
    }

    private List<GmailMensajeDTO> mensajesCacheados(MFuente cuenta, List<String> direcciones)
            throws IOException {
        Instant desde = Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS));
        List<GmailMensajeDTO> resultado = new ArrayList<>();
        for (IGmailMensajes.Resumen mensaje : mensajes.findResumenesByCuentaAndFecha(cuenta.getIdFuente(), desde)) {
            boolean dirigido = direcciones.stream().anyMatch(d -> contieneDireccion(mensaje.getDestinatario(), d));
            if (!dirigido) continue;
            List<String> etiquetasIds = leerListaJson(mensaje.getEtiquetasJson());
            List<String> nombresEtiquetas = leerListaJson(mensaje.getNombresEtiquetasJson());
            resultado.add(new GmailMensajeDTO(
                    mensaje.getGoogleMessageId(),
                    valorSeguro(mensaje.getRemitente()),
                    valorSeguro(mensaje.getDestinatario()),
                    valorSeguro(mensaje.getAsunto()),
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                            .format(mensaje.getFechaInterna().atZone(java.time.ZoneOffset.UTC)),
                    valorSeguro(mensaje.getFragmento()),
                    etiquetasIds,
                    nombresEtiquetas));
        }
        return resultado;
    }

    private List<String> leerListaJson(String valor) throws IOException {
        if (valor == null || valor.isBlank()) return List.of();
        return objectMapper.readValue(valor, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }

    private boolean contieneDireccion(String destinatarios, String direccion) {
        if (destinatarios == null || direccion == null) return false;
        return java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9._%+-])"
                + java.util.regex.Pattern.quote(direccion.trim())
                + "(?![A-Z0-9._%+-])").matcher(destinatarios).find();
    }

    private String valorSeguro(String valor) {
        return valor == null ? "" : valor;
    }

    private String nombreEtiqueta(String id) {
        return switch (id) {
            case "IMPORTANT" -> "Importante";
            case "UNREAD" -> "No leído";
            case "INBOX" -> "Recibidos";
            case "CATEGORY_PROMOTIONS" -> "Promociones";
            case "CATEGORY_UPDATES" -> "Actualizaciones";
            case "CATEGORY_SOCIAL" -> "Social";
            case "STARRED" -> "Destacados";
            default -> id;
        };
    }

    private void purgarMensajesAntiguos(MFuente cuenta) {
        mensajes.deleteByCuenta_IdFuenteAndFechaInternaBefore(cuenta.getIdFuente(),
                Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS)));
    }

    private void sincronizarSuscripcionesGuardadas(MFuente cuenta) throws IOException {
        if (!suscripciones.findByCuenta_IdFuenteOrderByUltimaActividadDesc(cuenta.getIdFuente()).isEmpty()) return;
        Instant desde = Instant.now().minus(java.time.Duration.ofDays(RETENCION_CORREOS_DIAS));
        for (MGmailMensaje mensaje : mensajes.findByCuenta_IdFuenteAndFechaInternaAfterAndListUnsubscribeIsNotNullOrderByFechaInternaDesc(
                cuenta.getIdFuente(), desde)) {
            guardarSuscripcion(cuenta, valorSeguro(mensaje.getRemitente()), valorSeguro(mensaje.getListUnsubscribe()),
                    valorSeguro(mensaje.getListUnsubscribePost()), leerListaJson(mensaje.getEtiquetasJson()),
                    mensaje.getFechaInterna());
        }
    }

    private String tokenAccesoActual(MFuente cuenta) throws IOException, GeneralSecurityException {
        CredencialesOAuth credenciales = resolverCredenciales();
        if (cuenta.getExpiraEn() != null && cuenta.getExpiraEn().isAfter(Instant.now().plusSeconds(MARGEN_RENOVACION_SEGUNDOS))
                && cuenta.getAccessTokenCifrado() != null && !cuenta.getAccessTokenCifrado().isBlank()) {
            return descifrar(cuenta.getAccessTokenCifrado(), credenciales.clientSecret());
        }
        if (cuenta.getRefreshTokenCifrado() == null || cuenta.getRefreshTokenCifrado().isBlank())
            throw new CuentaGmailRequiereReconectarException();
        GoogleOAuthTokenDTO renovacion = renovarToken(descifrar(cuenta.getRefreshTokenCifrado(), credenciales.clientSecret()), credenciales);
        String token = renovacion == null || renovacion.accessToken() == null ? "" : renovacion.accessToken().trim();
        if (token.isBlank()) throw new CuentaGmailRequiereReconectarException();
        long duracion = renovacion.expiresIn() == null || renovacion.expiresIn() <= 0 ? DURACION_TOKEN_SEGUNDOS_POR_DEFECTO : renovacion.expiresIn();
        cuenta.setAccessTokenCifrado(cifrar(token, credenciales.clientSecret()));
        cuenta.setExpiraEn(Instant.now().plusSeconds(duracion));
        fuentes.save(cuenta);
        return token;
    }

    private List<GmailSuscripcionDTO> suscripcionesCacheadas(MFuente cuenta) throws IOException {
        List<GmailSuscripcionDTO> resultado = new ArrayList<>();
        for (MGmailSuscripcion suscripcion : suscripciones.findByCuenta_IdFuenteOrderByUltimaActividadDesc(
                cuenta.getIdFuente())) {
            String correo = suscripcion.getCorreoRemitente();
            String unsubscribe = valorSeguro(suscripcion.getListUnsubscribe());
            String from = valorSeguro(suscripcion.getNombre());
            List<String> categorias = leerListaJson(suscripcion.getCategoriasJson());
            resultado.add(new GmailSuscripcionDTO(correo.toLowerCase(Locale.ROOT), from, correo, categorias,
                    extraerDestinoBaja(unsubscribe, "https?"), extraerDestinoBaja(unsubscribe, "mailto"),
                    valorSeguro(suscripcion.getListUnsubscribePost()).toLowerCase(Locale.ROOT)
                            .contains("list-unsubscribe=one-click"),
                    null, cuenta.getIdFuente(), cuenta.getContenido(), suscripcion.getUltimaActividad()));
        }
        return List.copyOf(resultado);
    }

    private void persistirSuscripcion(MFuente cuenta, JsonNode mensaje) throws IOException {
        if (mensaje == null) return;
        JsonNode headers = mensaje.path("payload").path("headers");
        String unsubscribe = header(headers, "List-Unsubscribe");
        if (unsubscribe.isBlank()) return;
        String from = header(headers, "From");
        long timestamp = mensaje.path("internalDate").asLong(System.currentTimeMillis());
        List<String> etiquetas = new ArrayList<>();
        JsonNode idsEtiquetas = mensaje.path("labelIds");
        if (idsEtiquetas.isArray()) idsEtiquetas.forEach(etiqueta -> etiquetas.add(etiqueta.asText()));
        guardarSuscripcion(cuenta, from, unsubscribe, header(headers, "List-Unsubscribe-Post"),
                etiquetas, Instant.ofEpochMilli(timestamp));
    }

    private void guardarSuscripcion(MFuente cuenta, String from, String unsubscribe, String unsubscribePost,
                                    List<String> etiquetas, Instant actividad) throws IOException {
        if (unsubscribe == null || unsubscribe.isBlank()) return;
        if (etiquetas.stream().noneMatch(etiqueta -> etiqueta.equals("CATEGORY_PROMOTIONS")
                || etiqueta.equals("CATEGORY_UPDATES"))) return;
        String correo = from.matches(".*<[^>]+>.*")
                ? from.replaceAll(".*<([^>]+)>.*", "$1").trim() : from.trim();
        if (correo.isBlank() || correo.length() > 254) return;
        String key = correo.toLowerCase(Locale.ROOT);
        String nombre = from.replaceAll("<[^>]+>", "").replaceAll("[\"']", "").trim();
        MGmailSuscripcion suscripcion = suscripciones.findByCuenta_IdFuenteAndCorreoRemitente(
                        cuenta.getIdFuente(), key)
                .orElseGet(() -> new MGmailSuscripcion(UUID.randomUUID().toString(), cuenta, key, actividad));

        if (!nombre.isBlank()) suscripcion.setNombre(nombre);
        if (!unsubscribe.isBlank()) suscripcion.setListUnsubscribe(unsubscribe);
        if (unsubscribePost != null && !unsubscribePost.isBlank()) suscripcion.setListUnsubscribePost(unsubscribePost);

        List<String> categorias = new ArrayList<>(leerListaJson(suscripcion.getCategoriasJson()));
        for (String categoria : etiquetas) {
            if ((categoria.equals("CATEGORY_PROMOTIONS") || categoria.equals("CATEGORY_UPDATES"))
                    && !categorias.contains(categoria)) categorias.add(categoria);
        }
        suscripcion.setCategoriasJson(objectMapper.writeValueAsString(categorias));
        if (actividad.isAfter(suscripcion.getUltimaActividad())) suscripcion.setUltimaActividad(actividad);
        suscripciones.save(suscripcion);
    }

    private String header(JsonNode headers, String nombre) {
        if (headers == null || !headers.isArray()) return "";
        for (JsonNode header : headers) if (nombre.equalsIgnoreCase(header.path("name").asText())) return header.path("value").asText("");
        return "";
    }

    private String extraerDestinoBaja(String value, String protocolo) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)<(" + protocolo + ":[^>]+)>").matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private boolean direccionCorreoValida(String valor) {
        return valor != null && valor.trim().matches("(?i)^[^\\s@{}]+@[^\\s@{}]+\\.[^\\s@{}]+$");
    }


    private String mensajeErrorGoogle(RestClientResponseException error, String predeterminado) {
        if (error.getStatusCode().value() == 401) return "La autorización de Gmail venció. Conecta Gmail de nuevo.";
        if (error.getStatusCode().value() == 429) return "Google limitó temporalmente las consultas. Espera un momento y vuelve a intentar.";
        return error.getStatusCode().value() == 403
                ? "Google denegó el permiso solicitado. Revisa los permisos concedidos y la configuración de la API."
                : predeterminado;
    }

    private JsonNode primerNodo(JsonNode lista, String campo) {
        if (lista == null || !lista.isArray()) return null;
        for (JsonNode elemento : lista) if (!elemento.path(campo).asText("").isBlank()) return elemento;
        return null;
    }

    private JsonNode primeraOrganizacion(JsonNode organizaciones) {
        if (organizaciones == null || !organizaciones.isArray()) return null;
        for (JsonNode organizacion : organizaciones) if (organizacion.path("current").asBoolean()) return organizacion;
        return organizaciones.isEmpty() ? null : organizaciones.get(0);
    }

    private String valorNodo(JsonNode nodo, String campo, String predeterminado) {
        if (nodo == null) return predeterminado;
        String valor = nodo.path(campo).asText("");
        return valor.isBlank() ? predeterminado : valor;
    }

    private String extraerTextoMensaje(JsonNode payload) {
        List<String> html = new ArrayList<>();
        List<String> texto = new ArrayList<>();
        List<String> imagenes = new ArrayList<>();
        recolectarPartes(payload, html, texto, imagenes);
        String contenido = !html.isEmpty() ? convertirHtmlTexto(String.join(" ", html), imagenes)
                : String.join("\n\n", texto) + (imagenes.isEmpty() ? "" : "\n" + imagenes.stream()
                .map(tipo -> "[Imagen " + tipo + ": no se puede renderizar en FinApp.]")
                .collect(java.util.stream.Collectors.joining("\n")));
        String normalizado = contenido.replace("\r", "").replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n[ \\t]+", "\n").replaceAll("\\n{2,}", "\n").trim();
        return normalizado.substring(0, Math.min(normalizado.length(), 12000));
    }

    private void recolectarPartes(JsonNode parte, List<String> html, List<String> texto, List<String> imagenes) {
        if (parte == null || parte.isMissingNode()) return;
        String tipo = parte.path("mimeType").asText("");
        String data = parte.path("body").path("data").asText("");
        if (tipo.startsWith("image/")) imagenes.add(tipo);
        if (!data.isBlank() && tipo.equalsIgnoreCase("text/html")) html.add(decodificarBase64Url(data));
        if (!data.isBlank() && tipo.equalsIgnoreCase("text/plain")) texto.add(decodificarBase64Url(data));
        JsonNode partes = parte.path("parts");
        if (partes.isArray()) for (JsonNode hija : partes) recolectarPartes(hija, html, texto, imagenes);
    }

    private String decodificarBase64Url(String valor) {
        try {
            return new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private String convertirHtmlTexto(String html, List<String> imagenes) {
        String sinScripts = html.replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style\\s*>", " ");
        java.util.regex.Matcher imagen = java.util.regex.Pattern.compile("(?is)<img\\b[^>]*>").matcher(sinScripts);
        StringBuffer conImagenes = new StringBuffer();
        int[] indiceImagen = {0};
        while (imagen.find()) {
            String tipo = indiceImagen[0] < imagenes.size() ? imagenes.get(indiceImagen[0]++) : "image/*";
            imagen.appendReplacement(conImagenes, java.util.regex.Matcher.quoteReplacement("\n[Imagen " + tipo + ": no se puede renderizar en FinApp.]\n"));
        }
        imagen.appendTail(conImagenes);
        java.util.regex.Matcher enlace = java.util.regex.Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a\\s*>").matcher(conImagenes.toString());
        StringBuffer conEnlaces = new StringBuffer();
        while (enlace.find()) {
            String destino = enlace.group(2).replace("&amp;", "&").trim();
            String etiqueta = enlace.group(3).replaceAll("(?is)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            String reemplazo = etiqueta;
            try {
                URI uri = URI.create(destino);
                String esquema = uri.getScheme();
                if (esquema != null && List.of("http", "https", "mailto").contains(esquema.toLowerCase(Locale.ROOT))) {
                    if (etiqueta.isBlank()) etiqueta = destino;
                    reemplazo = "[[[MAIL_LINK:" + codificarMarcador(etiqueta) + "|" + codificarMarcador(destino) + "]]]";
                }
            } catch (IllegalArgumentException ignored) {
                reemplazo = etiqueta;
            }
            enlace.appendReplacement(conEnlaces, java.util.regex.Matcher.quoteReplacement(reemplazo));
        }
        enlace.appendTail(conEnlaces);
        return conEnlaces.toString().replaceAll("(?i)</(?:p|div|li|h[1-6]|tr)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?is)<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
    }

    private String codificarMarcador(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Devuelve o renueva el token de la fuente Gmail propia indicada. */
    @GetMapping("/cuentas/{cuentaId}/conexion")
    public ResponseEntity<?> obtenerConexionCuenta(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                                    HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente fuente = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (fuente == null || !"CORREO".equalsIgnoreCase(fuente.getTipo()) || !fuente.tieneConexionGmail()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No se encontró esa cuenta Gmail."));
        }
        try {
            tokenAccesoActual(fuente);
            return ResponseEntity.ok(Map.of("connected", true, "email", fuente.getContenido(),
                    "expires_at", fuente.getExpiraEn().toString()));
        } catch (RestClientResponseException error) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "connected", false, "requires_reconnect", true,
                    "message", "Google revocó o venció esta autorización. Conecta de nuevo la cuenta."
            ));
        } catch (CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (IOException | GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "No se pudo restaurar esta cuenta Gmail."));
        }
    }

    @DeleteMapping("/cuentas/{cuentaId}")
    public ResponseEntity<?> eliminarCuenta(@org.springframework.web.bind.annotation.PathVariable String cuentaId,
                                            HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente fuente = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (fuente == null || !"CORREO".equalsIgnoreCase(fuente.getTipo())) return ResponseEntity.notFound().build();
        mensajes.deleteByCuenta_IdFuente(fuente.getIdFuente());
        suscripciones.deleteByCuenta_IdFuente(fuente.getIdFuente());
        fuente.desconectarGmail();
        fuentes.save(fuente);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conexion")
    public ResponseEntity<?> obtenerConexion(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        MFuente fuente = fuentes.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(item -> "CORREO".equalsIgnoreCase(item.getTipo()) && item.tieneConexionGmail()).findFirst().orElse(null);
        if (fuente == null) return ResponseEntity.ok(Map.of("connected", false));
        return obtenerConexionCuenta(fuente.getIdFuente(), request);
    }

    /** Desconecta las cuentas Gmail sin eliminar las fuentes de correo registradas. */
    @DeleteMapping("/conexion")
    public ResponseEntity<?> desconectar(HttpServletRequest request) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No hay una sesión activa."));
        List<MFuente> fuentesCorreo = fuentes.findByUsuario_IdUsuario(usuario.getIdUsuario()).stream()
                .filter(fuente -> "CORREO".equalsIgnoreCase(fuente.getTipo())).toList();
        fuentesCorreo.forEach(fuente -> {
            mensajes.deleteByCuenta_IdFuente(fuente.getIdFuente());
            suscripciones.deleteByCuenta_IdFuente(fuente.getIdFuente());
            fuente.desconectarGmail();
        });
        fuentes.saveAll(fuentesCorreo);
        return ResponseEntity.noContent().build();
    }

    private GoogleOAuthTokenDTO renovarToken(String refreshToken, CredencialesOAuth credenciales) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", credenciales.clientId());
        formulario.add("client_secret", credenciales.clientSecret());
        formulario.add("refresh_token", refreshToken);
        formulario.add("grant_type", "refresh_token");
        return respuestaJson(clienteHttp().post().uri(URL_TOKEN_GOOGLE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario), GoogleOAuthTokenDTO.class);
    }

    private String obtenerCorreoGoogle(String accessToken) {
        GmailProfileDTO perfil = respuestaJson(clienteHttp().get().uri(URL_PERFIL_GOOGLE)
                .headers(headers -> headers.setBearerAuth(accessToken)), GmailProfileDTO.class);
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
            Path desdeCarpetaBackend = Path.of("finApp-backend").resolve(ruta.getFileName());
            if (Files.isRegularFile(desdeCarpetaBackend)) ruta = desdeCarpetaBackend;
        }
        JsonNode web;
        try (var entrada = Files.newInputStream(ruta)) { web = objectMapper.readTree(entrada).path("web"); }
        String id = idConfigurado.isBlank() ? web.path("client_id").asText("") : idConfigurado;
        String secreto = secretoConfigurado.isBlank() ? web.path("client_secret").asText("") : secretoConfigurado;
        if (id.isBlank() || secreto.isBlank()) throw new IOException("Faltan las credenciales web de Google.");
        return new CredencialesOAuth(id, secreto);
    }

    private ResponseEntity<Map<String, Object>> respuestaReconexionRequerida() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "requires_reconnect", true,
                "message", "La autorización de esta cuenta Gmail ya no se puede renovar. Vuelve a conectarla desde Configuración."
        ));
    }

    public record SolicitudCodigo(String code, @com.fasterxml.jackson.annotation.JsonProperty("expected_account_id") String expectedAccountId) { }
    public record SolicitudMensajes(@com.fasterxml.jackson.annotation.JsonProperty("fuentes_ids") List<String> fuentesIds) { }
    private record CredencialesOAuth(String clientId, String clientSecret) { }
    private static class CuentaGmailRequiereReconectarException extends IllegalStateException { }
}
