package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UsuarioActivoInterceptor implements HandlerInterceptor {
    private final ObjectMapper mapper;
    public UsuarioActivoInterceptor(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String method = req.getMethod();
        if (!(method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE"))) return true;
        String path = req.getRequestURI();
        if (path.equals("/usuario") || path.equals("/usuario/login") || path.equals("/usuario/logout") || path.equals("/api/gmail/callback")) return true;
        if (req.getSession(false) != null && req.getSession(false).getAttribute(SUsuarioActivo.CLAVE_SESION) != null) return true;
        res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(res.getWriter(), RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        return false;
    }
}
