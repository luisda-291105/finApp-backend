package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Config.SesionConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * @uso               Emisión y validación de JWT de sesión con separación access/refresh
 * @capa              Servicio de seguridad
 * @responsabilidades Firmar tokens, rechazar refresh en el filtro, hash SHA-256 y cookies
 * @datos             SesionConfig (tiempos), FINAPP_JWT_SECRET, deny-list por jti
 * @dependencias      JJWT, SesionConfig
 * @usadoPor          CUsuario, UsuarioActivoInterceptor, SRefreshFamilias
 */
@Service
public class JwtTokenService {
    public static final String COOKIE_NAME = "FINAPP_AUTH";
    public static final String REFRESH_COOKIE_NAME = "FINAPP_REFRESH";
    public static final String REFRESH_COOKIE_PATH = "/usuario/refresh";
    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";

    @Value("${finapp.jwt.secret:${FINAPP_JWT_SECRET:}}")
    private String secret;

    @Value("${finapp.jwt.cookie-secure:true}")
    private boolean secureCookie;

    /** SameSite de la cookie de access: Lax como mínimo, Strict o None son configurables. */
    @Value("${finapp.jwt.cookie.same-site:Lax}")
    private String sameSiteCookie = "Lax";

    private final SesionConfig config;
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);

    /** Deny-list histórica de jti para logout; se conserva hasta el tope absoluto. */
    private final ConcurrentHashMap<String, Date> DENY_LIST = new ConcurrentHashMap<>();

    public JwtTokenService(SesionConfig config) {
        this.config = config;
    }

    /** Revoca un token por su identificador (logout). */
    public void revocar(String jti) {
        if (jti == null || jti.isBlank()) return;
        long ahora = System.currentTimeMillis();
        DENY_LIST.entrySet().removeIf(entrada -> entrada.getValue().getTime() <= ahora);
        DENY_LIST.put(jti, new Date(ahora
                + Duration.ofSeconds(config.absoluteSeconds()).toMillis()));
    }

    /** Indica si un token (jti) fue revocado (logout). */
    public boolean estaRevocado(String jti) {
        return jti != null && DENY_LIST.containsKey(jti);
    }

    /** Revoca el access de la request (logout). */
    public void revocarToken(HttpServletRequest request) {
        String token = tokenDe(request);
        if (token == null || token.isBlank()) return;
        Claims claims = leerClaimsSeguro(token);
        if (claims == null) return;
        String jti = claims.getId();
        // Nunca se revoca un refresh aquí: las familias se revocan en SRefreshFamilias.
        if (TIPO_REFRESH.equals(claims.get("tipo", String.class))) return;
        if (jti != null) revocar(jti);
    }

    /**
     * Token heredado (admin-login, Swagger, script docs/generar_token_swagger.py).
     * TODO(retirar tras la migración): los tokens sin claim `tipo` se siguen aceptando
     * como access solo por compatibilidad. Los nuevos flujos usan `emitirAccess`.
     */
    public String emitir(String idUsuario) {
        if (idUsuario == null || idUsuario.isBlank()) throw new IllegalArgumentException("El usuario no es válido.");
        if (config.absoluteSeconds() <= 0) throw new IllegalStateException("FINAPP_SESION_ABSOLUTE_SECONDS debe ser mayor que cero.");
        Instant ahora = Instant.now();
        Instant expiraEn = ahora.plusSeconds(config.absoluteSeconds());
        return Jwts.builder()
                .subject(idUsuario)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .claim("expiraEn", expiraEn.toEpochMilli())
                .claim("actividad", ahora.toEpochMilli())
                .signWith(clave())
                .compact();
    }

    /** Access token de 15 min como máximo; nunca supera el tope absoluto de la familia. */
    public String emitirAccess(String idUsuario, String familyId, String jti, Instant absoluto) {
        validarIdentidad(idUsuario, familyId, absoluto);
        Instant ahora = Instant.now();
        Instant expiraEn = limite(ahora.plusSeconds(config.accessSeconds()), absoluto);
        return Jwts.builder()
                .subject(idUsuario)
                .id(jti == null ? UUID.randomUUID().toString() : jti)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .claim("tipo", TIPO_ACCESS)
                .claim("fam", familyId)
                .claim("expiraEn", expiraEn.toEpochMilli())
                .signWith(clave())
                .compact();
    }

    /** Refresh token con ventana de inactividad de 30 min; se rota en cada uso. */
    public String emitirRefresh(String idUsuario, String familyId, Instant absoluto, long credentialVersion) {
        validarIdentidad(idUsuario, familyId, absoluto);
        Instant ahora = Instant.now();
        Instant expiraEn = limite(ahora.plusSeconds(config.refreshInactivitySeconds()), absoluto);
        return Jwts.builder()
                .subject(idUsuario)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .claim("tipo", TIPO_REFRESH)
                .claim("fam", familyId)
                .claim("cv", credentialVersion)
                .claim("expiraEn", expiraEn.toEpochMilli())
                .signWith(clave())
                .compact();
    }

    /** Parsea y valida un refresh: firma, exp y tipo explícito. Nunca acepta un access. */
    public Claims parsearRefresh(String token) {
        Claims claims = Jwts.parser().verifyWith(clave()).build()
                .parseSignedClaims(token).getPayload();
        if (!TIPO_REFRESH.equals(claims.get("tipo", String.class))) {
            throw new JwtException("El token no es de tipo refresh.");
        }
        String familyId = claims.get("fam", String.class);
        String subject = claims.getSubject();
        if (familyId == null || familyId.isBlank() || subject == null || subject.isBlank()) {
            throw new JwtException("El refresh no tiene familia o usuario.");
        }
        Long cv = claims.get("cv", Long.class);
        if (cv == null) throw new JwtException("El refresh no tiene versión de credenciales.");
        return claims;
    }

    /** Identificador del access para control de rate limit por familia/usuario. */
    public Optional<String> idUsuarioDeAcceso(HttpServletRequest request) {
        Claims claims = leerClaimsSeguro(tokenDe(request));
        if (claims == null || TIPO_REFRESH.equals(claims.get("tipo", String.class))) return Optional.empty();
        String subject = claims.getSubject();
        return subject == null || subject.isBlank() ? Optional.empty() : Optional.of(subject);
    }

    /** familyId del token de la request. Si ya expiró, se leen los claims de ExpiredJwtException. */
    public Optional<String> familyIdDe(HttpServletRequest request, boolean ignorarExpiracion) {
        String token = tokenDe(request);
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(clave()).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.get("fam", String.class));
        } catch (ExpiredJwtException error) {
            if (!ignorarExpiracion) return Optional.empty();
            return Optional.ofNullable(error.getClaims().get("fam", String.class));
        } catch (JwtException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    /** Valida la sesión sin reemitir cookies: el refresh es el único que renueva. */
    public Optional<String> obtenerIdUsuario(HttpServletRequest request) {
        return validarSesion(request);
    }

    /** Mantiene la firma histórica que usa UsuarioActivoInterceptor. */
    public Optional<String> sesionActiva(HttpServletRequest request, HttpServletResponse response) {
        return validarSesion(request);
    }

    private Optional<String> validarSesion(HttpServletRequest request) {
        Claims claims = leerClaimsSeguro(tokenDe(request));
        if (claims == null) return Optional.empty();

        String tipo = claims.get("tipo", String.class);
        // Regla obligatoria: un refresh nunca autentica peticiones de datos.
        if (TIPO_REFRESH.equals(tipo)) return Optional.empty();

        String jti = claims.getId();
        if (jti != null && estaRevocado(jti)) return Optional.empty();
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) return Optional.empty();

        Instant ahora = Instant.now();
        Long expiraEn = claims.get("expiraEn", Long.class);
        if (expiraEn != null && !ahora.isBefore(Instant.ofEpochMilli(expiraEn))) return Optional.empty();

        // TODO(retirar tras la migración): solo los tokens sin `tipo` conservan la
        // comprobación de actividad; los access de 15 min expiran antes por `exp`.
        if (tipo == null) {
            Long actividad = claims.get("actividad", Long.class);
            long ultimaActividad = actividad != null ? actividad
                    : (claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L);
            boolean activa = ultimaActividad > 0
                    && (ahora.toEpochMilli() - ultimaActividad) <= Duration.ofSeconds(config.refreshInactivitySeconds()).toMillis();
            if (!activa) return Optional.empty();
        }
        return Optional.of(subject);
    }

    /** SHA-256 del token en Base64. Solo se guarda el hash, nunca el token en claro. */
    public String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resumen);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
        }
    }

    /** Comparación en tiempo constante de dos hashes Base64. */
    public boolean coincideHash(String guardado, String candidato) {
        if (guardado == null || candidato == null) return false;
        try {
            return MessageDigest.isEqual(Base64.getDecoder().decode(guardado),
                    Base64.getDecoder().decode(candidato));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public ResponseCookie cookie(String token) {
        return cookie(token, config.absoluteSeconds());
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

    public ResponseCookie cookieAccess(String token, long maxAgeSegundos) {
        return cookie(token, maxAgeSegundos);
    }

    /** Cookie de refresh: Path=/usuario/refresh, SameSite=Strict, HttpOnly y Secure. */
    public ResponseCookie cookieRefresh(String token, long maxAgeSegundos) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSegundos))
                .build();
    }

    public ResponseCookie cookieExpirado() {
        return cookie("", 0);
    }

    /** Borrado con EXACTAMENTE los mismos atributos que la cookie original. */
    public ResponseCookie cookieRefreshExpirado() {
        return cookieRefresh("", 0);
    }

    public boolean hayToken(HttpServletRequest request) {
        return !tokenDe(request).isBlank();
    }

    public String refreshDe(HttpServletRequest request) {
        return obtenerCookie(request, REFRESH_COOKIE_NAME).orElse("");
    }

    private void validarIdentidad(String idUsuario, String familyId, Instant absoluto) {
        if (idUsuario == null || idUsuario.isBlank()) throw new IllegalArgumentException("El usuario no es válido.");
        if (familyId == null || familyId.isBlank()) throw new IllegalArgumentException("La familia no es válida.");
        if (absoluto == null) throw new IllegalArgumentException("El tope absoluto es obligatorio.");
    }

    private Instant limite(Instant candidato, Instant absoluto) {
        return candidato.isBefore(absoluto) ? candidato : absoluto;
    }

    private Claims leerClaimsSeguro(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return Jwts.parser().verifyWith(clave()).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException error) {
            return null;
        }
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
            LOGGER.warn("La cookie de sesión usa SameSite=None; se activa Secure porque el navegador lo exige.");
        }
        return normalizado;
    }

    private String tokenDe(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring("Bearer ".length()).trim();
        }
        return obtenerCookie(request, COOKIE_NAME).orElse("");
    }

    private Optional<String> obtenerCookie(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (nombre.equals(cookie.getName())) return Optional.ofNullable(cookie.getValue());
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
