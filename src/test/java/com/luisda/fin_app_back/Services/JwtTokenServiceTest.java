package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Config.SesionConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    private JwtTokenService tokens;
    private SesionConfig sesion;
    private SecretKey clave;

    @BeforeEach
    void preparar() {
        sesion = new SesionConfig();
        ReflectionTestUtils.setField(sesion, "accessSeconds", 900L);
        ReflectionTestUtils.setField(sesion, "refreshInactivitySeconds", 1800L);
        ReflectionTestUtils.setField(sesion, "absoluteSeconds", 28800L);
        ReflectionTestUtils.setField(sesion, "reuseGraceSeconds", 15L);

        tokens = new JwtTokenService(sesion);
        ReflectionTestUtils.setField(tokens, "secret",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(tokens, "secureCookie", true);
        ReflectionTestUtils.setField(tokens, "sameSiteCookie", "Lax");
        clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void validaJwtEnCookieHttpOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("usuario-1")));

        assertEquals("usuario-1", tokens.obtenerIdUsuario(request).orElseThrow());
        assertTrue(tokens.cookie(tokens.emitir("usuario-1")).toString().contains("HttpOnly"));
    }

    @Test
    void admiteJwtBearerYRechazaTokensModificados() {
        String valido = tokens.emitir("usuario-2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + valido);
        assertEquals("usuario-2", tokens.obtenerIdUsuario(request).orElseThrow());

        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + valido.substring(0, valido.length() - 2) + "xx");
        assertFalse(tokens.obtenerIdUsuario(request).isPresent());
    }

    @Test
    void accessTokenConTipoAccessEsValido() {
        Instant absoluto = Instant.now().plusSeconds(28800);
        String access = tokens.emitirAccess("usuario-3", "familia-1", "jti-1", absoluto);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, access));

        assertEquals("usuario-3", tokens.sesionActiva(request, new MockHttpServletResponse()).orElseThrow());
        Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(access).getPayload();
        assertEquals("access", claims.get("tipo", String.class));
        assertEquals("familia-1", claims.get("fam", String.class));
        assertEquals("jti-1", claims.getId());
    }

    @Test
    void refreshTokenNuncaAutenticaPeticiones() {
        Instant absoluto = Instant.now().plusSeconds(28800);
        String refresh = tokens.emitirRefresh("usuario-4", "familia-2", absoluto, 0L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + refresh);

        assertTrue(tokens.obtenerIdUsuario(request).isEmpty());
        assertTrue(tokens.sesionActiva(request, new MockHttpServletResponse()).isEmpty());
    }

    @Test
    void parsearRefreshRechazaUnAccess() {
        Instant absoluto = Instant.now().plusSeconds(28800);
        String access = tokens.emitirAccess("usuario-5", "familia-3", null, absoluto);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> tokens.parsearRefresh(access));
    }

    @Test
    void accessRenovadoNuncaSuperaElTopeAbsoluto() {
        Instant absoluto = Instant.now().plusSeconds(60);
        String access = tokens.emitirAccess("usuario-6", "familia-4", null, absoluto);
        Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(access).getPayload();
        assertTrue(claims.getExpiration().toInstant().toEpochMilli() <= absoluto.toEpochMilli());
    }

    @Test
    void logoutSigueRevocandoElAccessPorJti() {
        Instant absoluto = Instant.now().plusSeconds(28800);
        String access = tokens.emitirAccess("usuario-7", "familia-5", "jti-logout", absoluto);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, access));

        assertEquals("usuario-7", tokens.obtenerIdUsuario(request).orElseThrow());
        tokens.revocarToken(request);
        assertTrue(tokens.obtenerIdUsuario(request).isEmpty());
    }

    @Test
    void tokenHeredadoSinTipoSigueSiendoValidoPorCompatibilidad() {
        // TODO(migracion): retirar cuando todos los clientes emitan claim tipo.
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + Duration.ofHours(1).toMillis());
        String heredado = Jwts.builder()
                .subject("usuario-8")
                .id("heredado-1")
                .issuedAt(ahora)
                .expiration(expira)
                .claim("expiraEn", expira.getTime())
                .claim("actividad", ahora.getTime())
                .signWith(clave)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, heredado));
        assertEquals("usuario-8", tokens.obtenerIdUsuario(request).orElseThrow());
    }

    @Test
    void hashYComparacionSonConstantesEnResultado() {
        String hash1 = tokens.hash("token-secreto");
        String hash2 = tokens.hash("token-secreto");
        String hash3 = tokens.hash("otro-token");
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertTrue(tokens.coincideHash(hash1, hash2));
        assertFalse(tokens.coincideHash(hash1, hash3));
        assertFalse(tokens.coincideHash(null, hash2));
        assertFalse(tokens.coincideHash(hash1, "%%%no-base64%%%"));
    }

    @Test
    void cookieRefreshTienePathYFlagsCorrectos() {
        String valor = tokens.cookieRefresh("r", 1800).toString();
        assertTrue(valor.contains("FINAPP_REFRESH="));
        assertTrue(valor.contains("Path=/usuario/refresh"));
        assertTrue(valor.contains("HttpOnly"));
        assertTrue(valor.contains("Secure"));
        assertTrue(valor.contains("SameSite=Strict"));
    }
}
