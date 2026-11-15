package com.luisda.fin_app_back.Services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class JwtTokenService {
    public static final String COOKIE_NAME = "FINAPP_AUTH";

    @Value("${finapp.jwt.secret:${FINAPP_JWT_SECRET:}}")
    private String secret;

    @Value("${finapp.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    @Value("${finapp.jwt.inactivity-seconds:1800}")
    private long inactivitySeconds = 1800L;

    @Value("${finapp.jwt.cookie-secure:true}")
    private boolean secureCookie;

    /**
     * SameSite de la cookie de sesión: Lax (same-site), Strict o None (sitios distintos,
     * por ejemplo la web en un dominio y la API en otro). None exige Secure.
     */
    @Value("${finapp.jwt.cookie.same-site:Lax}")
    private String sameSiteCookie = "Lax";

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);

    private final ConcurrentHashMap<String, Date> DENY_LIST = new ConcurrentHashMap<>();

    /** Revoca un token por su identificador (logout). */
    public void revocar(String jti) {
        if (jti == null || jti.isBlank()) return;
        DENY_LIST.put(jti, new Date(System.currentTimeMillis() + Duration.ofSeconds(expirationSeconds).toMillis()));
    }

    /** Indica si un token (jti) fue revocado (logout). */
    public boolean estaRevocado(String jti) {
        return jti != null && DENY_LIST.containsKey(jti);
    }

    /** Revoca el token activo presente en la request (logout). */
    public void revocarToken(HttpServletRequest request) {
        String token = tokenDe(request);
        if (token == null || token.isBlank()) return;
        try {
            Claims claims = Jwts.parser().verifyWith(clave()).build()
                    .parseSignedClaims(token).getPayload();
            String jti = claims.getId();
            if (jti != null) revocar(jti);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token invalido: no hay nada que revocar.
        }
    }

    public String emitir(String idUsuario) {
        if (idUsuario == null || idUsuario.isBlank()) throw new IllegalArgumentException("El usuario no es válido.");
        if (expirationSeconds <= 0) throw new IllegalStateException("FINAPP_JWT_EXPIRATION_SECONDS debe ser mayor que cero.");
        Date ahora = new Date();
        long expiraEn = ahora.getTime() + Duration.ofSeconds(expirationSeconds).toMillis();
        return Jwts.builder()
                .subject(idUsuario)
                .id(UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiration(new Date(expiraEn))
                .claim("expiraEn", expiraEn)
                .claim("actividad", ahora.getTime())
                .signWith(clave())
                .compact();
    }

    public Optional<String> obtenerIdUsuario(HttpServletRequest request) {
        String token = tokenDe(request);
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(clave()).build()
                    .parseSignedClaims(token).getPayload();
            Date ahora = new Date();
            Long actividad = claims.get("actividad", Long.class);
            long ultimaActividad = actividad != null ? actividad : (claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L);
            String jti = claims.getId();
            if (jti != null && estaRevocado(jti)) return Optional.empty();
            boolean activa = ultimaActividad > 0 && (ahora.getTime() - ultimaActividad) <= Duration.ofSeconds(inactivitySeconds).toMillis();
            String subject = claims.getSubject();
            if (!activa) return Optional.empty();
            return subject == null || subject.isBlank() ? Optional.empty() : Optional.of(subject);
        } catch (JwtException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public Optional<String> sesionActiva(HttpServletRequest request, HttpServletResponse response) {
        String token = tokenDe(request);
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(clave()).build()
                    .parseSignedClaims(token).getPayload();
            Date ahora = new Date();
            Long actividad = claims.get("actividad", Long.class);
            long ultimaActividad = actividad != null ? actividad : (claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L);
            String jti = claims.getId();
            if (jti != null && estaRevocado(jti)) return Optional.empty();
            boolean activa = ultimaActividad > 0 && (ahora.getTime() - ultimaActividad) <= Duration.ofSeconds(inactivitySeconds).toMillis();
            if (!activa) return Optional.empty();
            String subject = claims.getSubject();
            Long expiraEn = claims.get("expiraEn", Long.class);
            if (subject == null || subject.isBlank() || expiraEn == null) return Optional.empty();
            long segundosRestantes = Math.max(0L, (expiraEn - ahora.getTime()) / 1000);
            String tokenRenovado = Jwts.builder()
                    .subject(subject)
                    .issuedAt(ahora)
                    .expiration(new Date(expiraEn))
                    .claim("expiraEn", expiraEn)
                    .claim("actividad", ahora.getTime())
                    .signWith(clave())
                    .compact();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(tokenRenovado, segundosRestantes).toString());
            return Optional.of(subject);
        } catch (JwtException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public ResponseCookie cookie(String token, long maxAgeSegundos) {
        String sameSite = normalizarSameSite();
        boolean seguro = this.secureCookie || "None".equals(sameSite);
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(seguro)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSegundos))
                .build();
    }

    public ResponseCookie cookie(String token) {
        return cookie(token, expirationSeconds);
    }

    public ResponseCookie cookieExpirado() {
        String sameSite = normalizarSameSite();
        boolean seguro = this.secureCookie || "None".equals(sameSite);
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(seguro)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Normaliza el SameSite configurado; None obliga a Secure porque el navegador lo exige. */
    private String normalizarSameSite() {
        String valor = this.sameSiteCookie == null ? "Lax" : this.sameSiteCookie.trim();
        if (valor.isEmpty()) return "Lax";
        String normalizado = switch (valor.toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> "None";
            case "strict" -> "Strict";
            default -> "Lax";
        };
        if ("None".equals(normalizado) && !this.secureCookie) {
            LOGGER.warn("La cookie de sesión usa SameSite=None; se activa Secure porque el navegador lo exige. "
                    + "Sirve solo en HTTPS o en localhost.");
        }
        return normalizado;
    }

    /** Indica si la petición trae algún token (válido o no), para diferenciar el motivo del 401. */
    public boolean hayToken(HttpServletRequest request) {
        return !tokenDe(request).isBlank();
    }

    private String tokenDe(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring("Bearer ".length()).trim();
        }
        return obtenerCookie(request).orElse("");
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
