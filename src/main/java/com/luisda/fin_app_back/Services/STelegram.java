package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luisda.fin_app_back.DTO.PeticionChat;
import com.luisda.fin_app_back.DTO.RespuestaChat;
import com.luisda.fin_app_back.DTO.TurnoChat;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Model.MTelegramMensaje;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.luisda.fin_app_back.Repository.ITelegramMensaje;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/** Enlace de cuentas y envío de mensajes privados mediante el bot de Telegram. */
@Service
public class STelegram {
    private static final Duration VIGENCIA_ENLACE = Duration.ofMinutes(10);
    private static final int MAX_MENSAJES_ASISTENTE_POR_MINUTO = 30;
    private final IUsuario usuarios;
    private final ITelegramMensaje mensajes;
    private final SAsistenteChat asistente;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, EnlacePendiente> enlacesPendientes = new ConcurrentHashMap<>();
    private final Map<String, VentanaAsistente> ventanasAsistente = new ConcurrentHashMap<>();
    private final String token;
    private final String nombreBot;
    private final int tiempoEsperaPolling;
    private volatile long siguienteUpdate = 0;
    private volatile String errorRecepcion = "";

    public STelegram(IUsuario usuarios, ITelegramMensaje mensajes, SAsistenteChat asistente,
                     @Value("${finapp.telegram.bot-token:}") String token,
                     @Value("${finapp.telegram.bot-name:}") String nombreBot,
                     @Value("${finapp.telegram.poll-timeout-seconds:5}") int tiempoEsperaPolling) {
        this.usuarios = usuarios;
        this.mensajes = mensajes;
        this.asistente = asistente;
        this.token = token == null ? "" : token.trim();
        this.nombreBot = nombreBot == null ? "" : nombreBot.trim().replaceFirst("^@", "");
        this.tiempoEsperaPolling = Math.max(0, Math.min(tiempoEsperaPolling, 25));
    }

    public Map<String, Object> estado(String idUsuario) {
        MUsuario usuario = obtenerUsuario(idUsuario);
        return Map.of(
                "conectado", usuario.getTelegramChatId() != null && !usuario.getTelegramChatId().isBlank(),
                "usuarioTelegram", usuario.getTelegramUsername() == null ? "" : usuario.getTelegramUsername(),
                "nombreBot", nombreBot,
                "configurado", !token.isBlank() && !nombreBot.isBlank(),
                "errorRecepcion", errorRecepcion
        );
    }

    public Map<String, String> crearEnlace(String idUsuario) {
        validarConfiguracionBot();
        MUsuario usuario = obtenerUsuario(idUsuario);
        if (usuario.getTelegramChatId() != null && !usuario.getTelegramChatId().isBlank()) {
            throw new IllegalStateException("Ya hay una cuenta de Telegram vinculada.");
        }
        limpiarEnlacesExpirados();
        byte[] bytes = new byte[24];
        aleatorio.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        enlacesPendientes.put(codigo, new EnlacePendiente(idUsuario, Instant.now().plus(VIGENCIA_ENLACE)));
        return Map.of("enlace", "https://t.me/" + nombreBot + "?start=" + codigo);
    }

    @Transactional
    public void desvincular(String idUsuario) {
        MUsuario usuario = obtenerUsuario(idUsuario);
        usuario.setTelegramChatId(null);
        usuario.setTelegramUsername(null);
        usuarios.save(usuario);
        enlacesPendientes.entrySet().removeIf(entry -> entry.getValue().idUsuario().equals(idUsuario));
    }

    public void enviarMensaje(String idUsuario, String texto, String nombreGrupo) {
        validarConfiguracionBot();
        String contenido = texto == null ? "" : texto.trim();
        if (contenido.isEmpty() || contenido.length() > 3500) {
            throw new IllegalArgumentException("El mensaje debe tener entre 1 y 3500 caracteres.");
        }
        MUsuario usuario = obtenerUsuario(idUsuario);
        if (usuario.getTelegramChatId() == null || usuario.getTelegramChatId().isBlank()) {
            throw new IllegalStateException("Vincula tu Telegram antes de enviar mensajes.");
        }
        String grupo = nombreGrupo == null ? "" : nombreGrupo.trim();
        String mensaje = grupo.isEmpty() ? contenido : "FinApp · " + grupo + "\n" + contenido;
        enviarTexto(usuario.getTelegramChatId(), mensaje);
        guardarMensaje(usuario, mensaje, grupo, true, "user");
    }

    public java.util.List<Map<String, Object>> mensajes(String idUsuario) {
        return mensajes.findTop100ByUsuario_IdUsuarioOrderByFechaDesc(idUsuario).stream()
                .sorted(java.util.Comparator.comparing(MTelegramMensaje::getFecha))
                .map(mensaje -> Map.<String, Object>of(
                        "id", mensaje.getId(),
                        "texto", mensaje.getTexto(),
                        "grupo", mensaje.getGrupoNombre() == null ? "" : mensaje.getGrupoNombre(),
                        "direccion", mensaje.isEnviadoPorUsuario() ? "saliente" : "entrante",
                        "rol", mensaje.getRolAsistente(),
                        "fecha", mensaje.getFecha().toString()
                )).toList();
    }

    /** Recibe los comandos /start mediante long polling para completar el vínculo privado. */
    @Scheduled(fixedDelayString = "${finapp.telegram.poll-delay-ms:1000}")
    public void consultarActualizaciones() {
        if (token.isBlank()) return;
        try {
            String url = "https://api.telegram.org/bot" + token + "/getUpdates?offset=" + siguienteUpdate
                    + "&timeout=" + tiempoEsperaPolling + "&allowed_updates=%5B%22message%22%5D";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(tiempoEsperaPolling + 8L)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                errorRecepcion = "Telegram no está recibiendo actualizaciones. Comprueba el token y que el bot no tenga otro webhook activo.";
                return;
            }
            JsonNode cuerpo = json.readTree(response.body());
            if (!cuerpo.path("ok").asBoolean(false)) {
                errorRecepcion = "Telegram no está recibiendo actualizaciones. Comprueba el token y que el bot no tenga otro webhook activo.";
                return;
            }
            errorRecepcion = "";
            for (JsonNode update : cuerpo.path("result")) {
                siguienteUpdate = Math.max(siguienteUpdate, update.path("update_id").asLong(-1) + 1);
                procesarInicio(update.path("message"));
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            errorRecepcion = "No se pudo conectar con Telegram para recibir mensajes.";
        }
    }

    private void procesarInicio(JsonNode mensaje) {
        if (!"private".equals(mensaje.path("chat").path("type").asText())) return;
        String texto = mensaje.path("text").asText("").trim();
        if (texto.matches("(?i)^/start(?:@\\w+)?$")) {
            String chatId = mensaje.path("chat").path("id").asText("");
            boolean vinculado = !chatId.isBlank() && usuarios.findByTelegramChatId(chatId).isPresent();
            enviarTexto(chatId, vinculado
                    ? "Hola. Ya estás conectado con FinApp. Escríbeme qué necesitas consultar y te responderé con los datos de tu cuenta."
                    : "Para conversar con tu asistente de FinApp, primero vincula este chat desde Configuración → Mensajería por Telegram.");
            return;
        }
        if (!texto.startsWith("/start ")) {
            if (!texto.isBlank()) responderMensajeEntrante(mensaje, texto);
            return;
        }
        String codigo = texto.substring("/start ".length()).trim().split("\\s+", 2)[0];
        EnlacePendiente pendiente = enlacesPendientes.remove(codigo);
        if (pendiente == null || pendiente.expiraEn().isBefore(Instant.now())) return;
        String chatId = mensaje.path("chat").path("id").asText("");
        if (chatId.isBlank()) return;
        try {
            if (usuarios.findByTelegramChatId(chatId).filter(otro -> !otro.getIdUsuario().equals(pendiente.idUsuario())).isPresent()) {
                enviarTexto(chatId, "Este chat de Telegram ya está vinculado a otra cuenta de FinApp.");
                return;
            }
            MUsuario usuario = obtenerUsuario(pendiente.idUsuario());
            usuario.setTelegramChatId(chatId);
            String username = mensaje.path("from").path("username").asText("");
            usuario.setTelegramUsername(username.isBlank() ? null : username);
            usuarios.save(usuario);
            String bienvenida = "Telegram quedó vinculado a tu cuenta de FinApp. Ya puedes escribirme aquí para conversar con tu asistente.";
            enviarTexto(chatId, bienvenida);
            guardarMensaje(usuario, bienvenida, "", true, "assistant");
        } catch (RuntimeException error) {
            enviarTexto(chatId, "No se pudo vincular Telegram. Genera un enlace nuevo desde FinApp e inténtalo otra vez.");
        }
    }

    private void responderMensajeEntrante(JsonNode mensaje, String texto) {
        String chatId = mensaje.path("chat").path("id").asText("");
        if (chatId.isBlank()) return;
        MUsuario usuario = usuarios.findByTelegramChatId(chatId).orElse(null);
        if (usuario == null) return;
        if (texto.length() > 2000) {
            enviarTexto(chatId, "El asistente acepta mensajes de hasta 2000 caracteres. Intenta resumir tu consulta.");
            return;
        }
        if (superoLimiteAsistente(usuario.getIdUsuario())) {
            enviarTexto(chatId, "Alcanzaste el límite temporal del asistente. Inténtalo de nuevo en un minuto.");
            return;
        }

        List<MTelegramMensaje> anteriores = mensajes.findTop100ByUsuario_IdUsuarioOrderByFechaDesc(usuario.getIdUsuario()).stream()
                .sorted(Comparator.comparing(MTelegramMensaje::getFecha))
                .toList();
        List<TurnoChat> historial = anteriores.stream()
                .skip(Math.max(0, anteriores.size() - 20L))
                .map(item -> new TurnoChat(item.getRolAsistente(), item.getTexto()))
                .toList();
        RespuestaChat respuesta;
        try {
            respuesta = asistente.responder(new PeticionChat(texto, historial), usuario.getIdUsuario());
        } catch (ServicioChatException error) {
            enviarTexto(chatId, error.getMessage() == null ? "No pude procesar ese mensaje." : error.getMessage());
            return;
        } catch (Exception error) {
            enviarTexto(chatId, "No pude responder ahora. Inténtalo de nuevo en unos momentos.");
            return;
        }

        guardarMensaje(usuario, texto, "", false, "user");
        String textoRespuesta = respuesta.respuesta() == null || respuesta.respuesta().isBlank()
                ? "No pude generar una respuesta. Inténtalo de nuevo."
                : respuesta.respuesta();
        if (respuesta.accionPendiente() != null) {
            textoRespuesta += "\n\nPropuesta: " + respuesta.accionPendiente().descripcion()
                    + "\nSi estás de acuerdo, responde: Sí, confirmo.";
        }
        enviarTexto(chatId, textoRespuesta);
        guardarMensaje(usuario, textoRespuesta, "", true, "assistant");
    }

    private void guardarMensaje(MUsuario usuario, String texto, String grupo, boolean saliente, String rolAsistente) {
        MTelegramMensaje mensaje = new MTelegramMensaje();
        mensaje.setUsuario(usuario);
        mensaje.setTexto(texto);
        mensaje.setGrupoNombre(grupo.isBlank() ? null : grupo);
        mensaje.setEnviadoPorUsuario(saliente);
        mensaje.setRolAsistente(rolAsistente);
        mensaje.setFecha(Instant.now());
        mensajes.save(mensaje);
    }

    private boolean superoLimiteAsistente(String idUsuario) {
        long ahora = System.currentTimeMillis();
        VentanaAsistente ventana = ventanasAsistente.compute(idUsuario, (clave, actual) -> {
            if (actual == null || ahora - actual.inicio() >= 60_000) return new VentanaAsistente(ahora, new AtomicInteger(1));
            actual.solicitudes().incrementAndGet();
            return actual;
        });
        return ventana.solicitudes().get() > MAX_MENSAJES_ASISTENTE_POR_MINUTO;
    }

    private void enviarTexto(String chatId, String texto) {
        String contenido = texto == null ? "" : texto;
        int inicio = 0;
        while (inicio < contenido.length()) {
            int fin = Math.min(inicio + 3900, contenido.length());
            if (fin < contenido.length()) {
                int salto = contenido.lastIndexOf('\n', fin);
                if (salto > inicio + 1000) fin = salto;
                if (fin > inicio && Character.isHighSurrogate(contenido.charAt(fin - 1)) && Character.isLowSurrogate(contenido.charAt(fin))) fin--;
            }
            enviarFragmento(chatId, contenido.substring(inicio, fin));
            inicio = fin;
        }
    }

    private void enviarFragmento(String chatId, String texto) {
        ObjectNode cuerpo = json.createObjectNode().put("chat_id", chatId).put("text", texto);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode resultado = json.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !resultado.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Telegram no pudo entregar el mensaje. Revisa que el bot siga activo y que el chat no esté bloqueado.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el envío a Telegram.");
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo conectar con Telegram. Inténtalo de nuevo.");
        }
    }

    private MUsuario obtenerUsuario(String idUsuario) {
        return usuarios.findById(idUsuario).orElseThrow(() -> new IllegalStateException("La sesión no corresponde a un usuario activo."));
    }

    private void validarConfiguracionBot() {
        if (token.isBlank() || nombreBot.isBlank()) {
            throw new IllegalStateException("Telegram no está configurado en el backend. Revisa TELEGRAM_BOT_TOKEN y NOMBRE_BOT_TELEGRAM.");
        }
    }

    private void limpiarEnlacesExpirados() {
        Instant ahora = Instant.now();
        enlacesPendientes.entrySet().removeIf(entry -> entry.getValue().expiraEn().isBefore(ahora));
    }

    private record EnlacePendiente(String idUsuario, Instant expiraEn) {}
    private record VentanaAsistente(long inicio, AtomicInteger solicitudes) {}
}
