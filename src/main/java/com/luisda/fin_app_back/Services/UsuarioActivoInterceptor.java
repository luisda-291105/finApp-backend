package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.EspecificaPublicaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

@Component
public class UsuarioActivoInterceptor implements HandlerInterceptor {
    private final JwtTokenService tokens;
    private final EspecificaPublicaApi especificaPublica;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Permite dejar la documentación (Swagger UI y OpenAPI) consultable sin sesión. */
    @Value("${finapp.docs.publico:true}")
    private boolean docsPublicas;

    public UsuarioActivoInterceptor(JwtTokenService tokens, EspecificaPublicaApi especificaPublica) {
        this.tokens = tokens;
        this.especificaPublica = especificaPublica;
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
        if (exento) return true;

        boolean esEspecifica = path.equals("/api-docs") || path.equals("/api-docs.yaml");
        boolean esInterfaz = path.equals("/docs") || path.startsWith("/swagger-ui");

        if (esEspecifica || esInterfaz) {
            boolean autenticado = sesionValida(req, res);
            if (!autenticado && !docsPublicas) {
                responderNoAutorizado(res);
                return false;
            }
            if (esEspecifica && !autenticado) {
                responderEspecificaPublica(res);
                return false;
            }
            return true;
        }

        if (!sesionValida(req, res)) {
            responderNoAutorizado(res);
            return false;
        }
        return true;
    }

    /** Valida la sesión sin propagar fallos de configuración (por ejemplo, secret sin definir). */
    private boolean sesionValida(HttpServletRequest req, HttpServletResponse res) {
        try {
            return this.tokens.sesionActiva(req, res).isPresent();
        } catch (RuntimeException error) {
            return false;
        }
    }

    /** Entrega la especificación OpenAPI reducida a las rutas públicas. */
    private void responderEspecificaPublica(HttpServletResponse res) throws java.io.IOException {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(this.objectMapper.writeValueAsString(this.especificaPublica.construir()));
    }

    private void responderNoAutorizado(HttpServletResponse res) throws java.io.IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.addHeader(HttpHeaders.SET_COOKIE, this.tokens.cookieExpirado().toString());
        res.getWriter().write("{\"tipo\":\"error\",\"mensaje\":\"Inicia sesión para continuar.\",\"campo\":null,\"datos\":null}");
    }
}
