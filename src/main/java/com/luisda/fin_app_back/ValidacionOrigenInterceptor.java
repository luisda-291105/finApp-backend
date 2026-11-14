package com.luisda.fin_app_back;

import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @uso               Valida el origen de las escrituras con cookie JWT
 * @capa              Web
 * @responsabilidades Rechazar peticiones state-changing sin origen permitido (CSRF de cookie)
 * @datos             Origines permitidos desde finapp.cors.allowed-origins
 * @dependencias      WebMvc
 * @usadoPor          CorsConfig
 */
public class ValidacionOrigenInterceptor implements HandlerInterceptor {

    private static final Set<String> RUTAS_EXENTAS = Set.of(
            "/api/gmail/webhook/pubsub",
            "/asistente/publico",
            "/usuario/login",
            "/usuario"
    );

    private final String[] origenesPermitidos;

    public ValidacionOrigenInterceptor(String[] origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String metodo = request.getMethod();
        if (!"POST".equalsIgnoreCase(metodo) && !"PUT".equalsIgnoreCase(metodo)
                && !"PATCH".equalsIgnoreCase(metodo) && !"DELETE".equalsIgnoreCase(metodo)) {
            return true;
        }
        String path = request.getRequestURI();
        boolean exento = RUTAS_EXENTAS.stream().anyMatch(path::startsWith);
        if (exento) return true;
        String origen = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader("Referer");
        String origenValido = origen;
        if ((origen == null || origen.isBlank()) && referer != null && !referer.isBlank()) {
            try {
                java.net.URI uri = new java.net.URI(referer);
                if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                    origenValido = uri.getScheme() + "://" + uri.getHost()
                            + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
                }
            } catch (Exception ignored) {
                origenValido = null;
            }
        }
        boolean autorizado = false;
        if (origenValido != null) {
            for (String permitido : origenesPermitidos) {
                if (permitido != null && permitido.trim().equals(origenValido.trim())) {
                    autorizado = true;
                    break;
                }
            }
        }
        if (!autorizado) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        return true;
    }
}
