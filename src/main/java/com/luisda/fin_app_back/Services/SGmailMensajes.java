package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * @uso               Descarga y guarda correos de Gmail de a uno por vez (por mensaje)
 * @capa              Servicio
 * @responsabilidades Descargar un mensaje completo de Gmail, validar que pertenezca a la caché,
 *                    persistirlo (contenido cifrado) y registrar la suscripción detectada
 * @datos             Mensajes de la API de Gmail (formato full) y filas de gmail_mensaje
 * @dependencias      IFuentes, IGmailMensajes, SSuscripcionGmail, credenciales OAuth de Google
 * @usadoPor          CGmail (worker de la cola, sincronizaciones y sincronización de suscripciones)
 */
@Service
public class SGmailMensajes {

    public static final int RETENCION_CORREOS_DIAS = 90;
    public static final Set<String> REMITENTES_PRIORITARIOS = Set.of(
            "nu@nu.com.co",
            "efacturahomecenter@homecenter.co",
            "serviciopse@achcolombia.com.co",
            "people@pasteur.com.co",
            SImportacionLoteGmail.REMITENTE_OFICIAL
    );
    private static final long PAUSA_GMAIL_MS = 100;
    private static final long PAUSA_BASE_REINTENTO_GMAIL_MS = 15000;
    private static final int INTENTOS_GMAIL = 6;
    private static final int LARGO_CONTENIDO_MAXIMO = 12000;
    /** Etiqueta de Gmail que marca un correo en la papelera (RF-29). */
    private static final String ETIQUETA_PAPELERA = "TRASH";
    private static final Logger LOGGER = LoggerFactory.getLogger(SGmailMensajes.class);

    private volatile RestClient clienteHttp;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IFuentes fuentes;
    private final IGmailMensajes mensajes;
    private final SSuscripcionGmail suscripcionesGmail;
    private final SecureRandom random = new SecureRandom();

    @Value("${finapp.google.client-id:${GOOGLE_CLIENT_ID:}}") private String clientId;
    @Value("${finapp.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") private String clientSecret;
    @Value("${finapp.google.credentials-file:${GOOGLE_OAUTH_CREDENTIALS_FILE:client_secret_2_1064872593009-3arpd7098fqkrutcp5ut8pr2i7225jbk.apps.googleusercontent.com.json}}")
    private String archivoCredenciales;

    public SGmailMensajes(IFuentes fuentes, IGmailMensajes mensajes, SSuscripcionGmail suscripcionesGmail) {
        this.fuentes = fuentes;
        this.mensajes = mensajes;
        this.suscripcionesGmail = suscripcionesGmail;
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

    /**
     * Descarga un mensaje de Gmail por su id y lo guarda en la caché (un correo por llamada).
     * Si el mensaje ya no pertenece a la caché lo elimina. Lanza la excepción del fallo para
     * que quien la invoca (la cola de entradas) decida el reintento.
     */
    public void guardarMensaje(MFuente cuenta, String token, String mensajeId)
            throws IOException, GeneralSecurityException {
        URI uri = UriComponentsBuilder.fromUriString("https://gmail.googleapis.com/gmail/v1/users/me/messages")
                .pathSegment(mensajeId).queryParam("format", "full").build().encode().toUri();
        JsonNode mensaje = respuestaJsonConReintentos(uri, token);
        if (!mensajePerteneceACache(cuenta, mensaje)) {
            mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
            return;
        }
        persistirMensaje(cuenta, mensaje);
    }

    /**
     * Descarta de forma persistente la alerta de movimiento pendiente al mover un correo a la
     * papelera (RF-28). El movimiento contable ya registrado no se toca (RF-27).
     */
    public void descartarAlertaMovimiento(MFuente cuenta, String mensajeId) {
        mensajes.findByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId).ifPresent(entidad -> {
            entidad.setAlertaMovimientoDescartada(true);
            entidad.setMovimientoAlertaPendiente(false);
            mensajes.save(entidad);
        });
    }

    private JsonNode respuestaJsonConReintentos(URI uri, String token) {
        for (int intento = 0; intento < INTENTOS_GMAIL; intento++) {
            try {
                JsonNode mensaje = respuestaJson(clienteHttp().get().uri(uri)
                        .headers(h -> h.setBearerAuth(token)), JsonNode.class);
                Thread.sleep(PAUSA_GMAIL_MS);
                return mensaje;
            } catch (HttpStatusCodeException error) {
                int estado = error.getStatusCode().value();
                if (estado != 429 && estado != 403) throw error;
                if (intento == INTENTOS_GMAIL - 1) throw error;
                long pausa = PAUSA_BASE_REINTENTO_GMAIL_MS * (1L << intento);
                dormir(pausa);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RestClientException("Sincronización Gmail interrumpida.", error);
            }
        }
        throw new RestClientException("No se pudo obtener el mensaje de Gmail tras agotar los reintentos.");
    }

    private void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Sincronización Gmail interrumpida.", error);
        }
    }

    private boolean mensajePerteneceACache(MFuente cuenta, JsonNode mensaje) {
        if (mensaje == null) return false;
        JsonNode headers = mensaje.path("payload").path("headers");
        String remitente = extraerDireccionRemitente(header(headers, "From"));
        if (esRemitentePrioritario(remitente)
                || (!remitente.isBlank() && suscripcionesGmail.existeEnCuenta(
                        cuenta.getIdFuente(), remitente))) return true;
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

    /** Extrae la dirección de correo de un encabezado From. */
    public static String extraerDireccionRemitente(String remitente) {
        if (remitente == null || remitente.isBlank()) return "";
        var matcher = java.util.regex.Pattern.compile("(?i)[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
                .matcher(remitente);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
    }

    private void persistirMensaje(MFuente cuenta, JsonNode mensaje) throws IOException, GeneralSecurityException {
        if (mensaje == null || mensaje.path("id").asText("").isBlank()) return;
        String mensajeId = mensaje.path("id").asText("");
        // Un correo que Gmail tiene en la papelera no se guarda ni reaparece en la bandeja (RF-29):
        // el punto único de escritura elimina la fila local y omite el guardado.
        if (etiquetasContienen(mensaje.path("labelIds"), ETIQUETA_PAPELERA)) {
            mensajes.deleteByCuenta_IdFuenteAndGoogleMessageId(cuenta.getIdFuente(), mensajeId);
            return;
        }
        if (!fuentes.existsById(cuenta.getIdFuente())) {
            return;
        }
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
                .map(SGmailMensajes::nombreEtiqueta).toList()));
        entidad.setListUnsubscribe(header(headers, "List-Unsubscribe"));
        entidad.setListUnsubscribePost(header(headers, "List-Unsubscribe-Post"));
        entidad.setContenidoCifrado(cifrar(extraerTextoMensaje(mensaje.path("payload")),
                resolverCredenciales().clientSecret()));
        entidad.setFechaInterna(fecha);
        mensajes.save(entidad);
        persistirSuscripcion(cuenta, mensaje);
    }

    /** Registra la suscripción detectada en los encabezados del mensaje guardado. */
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
        suscripcionesGmail.guardar(cuenta, from, unsubscribe, header(headers, "List-Unsubscribe-Post"),
                etiquetas, Instant.ofEpochMilli(timestamp));
    }

    private static String header(JsonNode headers, String nombre) {
        if (headers == null || !headers.isArray()) return "";
        for (JsonNode header : headers) if (nombre.equalsIgnoreCase(header.path("name").asText())) return header.path("value").asText("");
        return "";
    }

    /** Indica si la lista de etiquetas de Gmail contiene la etiqueta indicada. */
    private static boolean etiquetasContienen(JsonNode etiquetas, String etiqueta) {
        if (etiquetas == null || !etiquetas.isArray()) return false;
        for (JsonNode item : etiquetas) if (etiqueta.equals(item.asText())) return true;
        return false;
    }

    private static String nombreEtiqueta(String id) {
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

    /** Indica si el texto de destinatarios contiene exactamente la dirección dada. */
    public static boolean contieneDireccion(String destinatarios, String direccion) {
        if (destinatarios == null || direccion == null) return false;
        return java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9._%+-])"
                + java.util.regex.Pattern.quote(direccion.trim())
                + "(?![A-Z0-9._%+-])").matcher(destinatarios).find();
    }

    /** Valor nulo convertido en cadena vacía para los DTO. */
    public static String valorSeguro(String valor) {
        return valor == null ? "" : valor;
    }

    /** Valida el formato de una dirección de correo (sin llaves ni espacios). */
    public static boolean direccionCorreoValida(String valor) {
        return valor != null && valor.trim().matches("(?i)^[^\\s@{}]+@[^\\s@{}]+\\.[^\\s@{}]+$");
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
        return normalizado.substring(0, Math.min(normalizado.length(), LARGO_CONTENIDO_MAXIMO));
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

    /** Cifra un valor con AES-GCM usando la clave derivada del secreto de Google. */
    public String cifrar(String valor, String secreto) throws GeneralSecurityException {
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

    /** Descifra un valor producido por {@link #cifrar}. */
    public String descifrar(String valor, String secreto) throws GeneralSecurityException {
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

    /** Resuelve las credenciales OAuth de Google desde configuración o archivo privado. */
    public CredencialesOAuth resolverCredenciales() throws IOException {
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

    /** Credenciales web de Google usadas para el canje y renovación de tokens. */
    public record CredencialesOAuth(String clientId, String clientSecret) { }
}
