package com.luisda.fin_app_back.Services;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UsuarioActivoInterceptor implements HandlerInterceptor {
    private final JwtTokenService tokens;

    /** Permite dejar la documentación (Swagger UI y OpenAPI) consultable sin sesión. */
    @Value("${finapp.docs.publico:true}")
    private boolean docsPublicas;

    public UsuarioActivoInterceptor(JwtTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String method = req.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        String path = req.getRequestURI();
        boolean exento = path.equals("/usuario") || path.equals("/usuario/login") || path.equals("/usuario/logout")
                || path.equals("/usuario/admin-login")
                || path.equals("/api/gmail/callback") || path.equals("/api/gmail/webhook/pubsub")
                || path.equals("/asistente/publico");
        boolean esDocumentacion = path.equals("/api-docs") || path.equals("/docs") || path.startsWith("/swagger-ui");
        if (exento || (esDocumentacion && docsPublicas)) return true;
        Optional<String> idUsuario = this.tokens.sesionActiva(req, res);
        if (idUsuario.isEmpty()) {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.addHeader(HttpHeaders.SET_COOKIE, this.tokens.cookieExpirado().toString());
            res.getWriter().write("{\"tipo\":\"error\",\"mensaje\":\"Inicia sesión para continuar.\",\"campo\":null,\"datos\":null}");
            return false;
        }
        return true;
    }
}
