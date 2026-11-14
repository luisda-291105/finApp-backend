package com.luisda.fin_app_back;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @uso               Añade cabeceras de seguridad HTTP a las respuestas del backend
 * @capa              Web
 * @responsabilidades Encabezados de mitigación (nosniff, frame, referrer, HSTS, CSP)
 * @datos             Políticas de seguridad
 * @dependencias      Servlet API
 * @usadoPor          Configuración global de filtros
 */
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'self'");
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        filterChain.doFilter(request, response);
    }
}
