package com.luisda.fin_app_back.Services;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtTokenService {
    public static final String COOKIE_NAME = "FINAPP_AUTH";

    @Value("${finapp.jwt.secret:${FINAPP_JWT_SECRET:}}")
    private String secret;

    @Value("${finapp.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    @Value("${finapp.jwt.cookie-secure:false}")
    private boolean secureCookie;

    public String emitir(String idUsuario) {
        if (idUsuario == null || idUsuario.isBlank()) throw new IllegalArgumentException("El usuario no es válido.");
        if (expirationSeconds <= 0) throw new IllegalStateException("FINAPP_JWT_EXPIRATION_SECONDS debe ser mayor que cero.");
        Date ahora = new Date();
        return Jwts.builder()
                .subject(idUsuario)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + Duration.ofSeconds(expirationSeconds).toMillis()))
                .signWith(clave())
                .compact();
    }

    public Optional<String> obtenerIdUsuario(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        String token = bearer != null && bearer.startsWith("Bearer ")
                ? bearer.substring("Bearer ".length()).trim()
                : obtenerCookie(request).orElse("");
        if (token.isBlank()) return Optional.empty();
        try {
            String subject = Jwts.parser().verifyWith(clave()).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return subject == null || subject.isBlank() ? Optional.empty() : Optional.of(subject);
        } catch (JwtException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(expirationSeconds))
                .build();
    }

    public ResponseCookie cookieExpirado() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private Optional<String> obtenerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) return Optional.ofNullable(cookie.getValue());
        }
        return Optional.empty();
    }

    private SecretKey clave() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Configura FINAPP_JWT_SECRET con una clave Base64 segura de al menos 256 bits.");
        }
        try {
            byte[] bytes = Decoders.BASE64.decode(secret.trim());
            return Keys.hmacShaKeyFor(bytes);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("FINAPP_JWT_SECRET debe ser Base64 y contener al menos 256 bits.", error);
        }
    }
}
