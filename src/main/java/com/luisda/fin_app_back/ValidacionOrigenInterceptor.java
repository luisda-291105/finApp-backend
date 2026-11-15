package com.luisda.fin_app_back;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * @uso               Valida el origen de las escrituras con cookie JWT
 * @capa              Web
 * @responsabilidades Rechazar peticiones state-changing sin origen permitido (CSRF de cookie)
 * @datos             Origenes permitidos desde finapp.cors.allowed-origins
 * @dependencias      WebMvc
 * @usadoPor          CorsConfig
 */
public class ValidacionOrigenInterceptor implements HandlerInterceptor {

    private static final List<String> RUTAS_EXENTAS = List.of(
            "/api/gmail/webhook/pubsub",
            "/asistente/publico",
            "/usuario/login",
            "/usuario/admin-login",
            "/usuario"
    );

    private final List<String> origenesPermitidos;

    public ValidacionOrigenInterceptor(String[] origenesPermitidos) {
        this(OrigenesWeb.normalizarLista(String.join(",", origenesPermitidos)));
    }

    public ValidacionOrigenInterceptor(List<String> origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos == null ? List.of() : origenesPermitidos;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String metodo = request.getMethod();
        if (!"POST".equalsIgnoreCase(metodo) && !"PUT".equalsIgnoreCase(metodo)
                && !"PATCH".equalsIgnoreCase(metodo) && !"DELETE".equalsIgnoreCase(metodo)) {
            return true;
        }
        String path = request.getRequestURI();
        if (RUTAS_EXENTAS.contains(path)) return true;
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
        if (!OrigenesWeb.permitido(origenValido, origenesPermitidos)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        return true;
    }
}
