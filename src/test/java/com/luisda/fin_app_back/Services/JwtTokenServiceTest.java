package com.luisda.fin_app_back.Services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {
    private JwtTokenService tokens;

    @BeforeEach
    void preparar() {
        tokens = new JwtTokenService();
        ReflectionTestUtils.setField(tokens, "secret",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(tokens, "expirationSeconds", 3600L);
        ReflectionTestUtils.setField(tokens, "inactivitySeconds", 1800L);
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
    void renuevaTokenCaducadoPorInactividadHastaLaExpiracionAbsoluta() {
        SecretKey clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64.getEncoder().encodeToString(new byte[32])));
        Date antigua = new Date(System.currentTimeMillis() - Duration.ofHours(2).toMillis());
        Date expira = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
        String tokenInactivo = Jwts.builder()
                .subject("usuario-3")
                .id("inactivo-1")
                .issuedAt(antigua)
                .expiration(expira)
                .claim("expiraEn", expira.getTime())
                .claim("actividad", antigua.getTime())
                .signWith(clave)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokenInactivo));
        assertFalse(tokens.obtenerIdUsuario(request).isPresent());

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals("usuario-3", tokens.sesionActiva(request, response).orElseThrow());
        assertEquals("usuario-3", tokens.obtenerIdUsuario(request).orElseThrow());

        String cabecera = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cabecera);
        String renovado = cabecera.substring(cabecera.indexOf('=') + 1, cabecera.indexOf(';'));
        MockHttpServletRequest siguiente = new MockHttpServletRequest();
        siguiente.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, renovado));
        assertEquals("usuario-3", tokens.obtenerIdUsuario(siguiente).orElseThrow());

        Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(renovado).getPayload();
        assertEquals("inactivo-1", claims.getId());
        assertEquals(expira.getTime(), claims.get("expiraEn", Long.class));
        assertTrue(claims.get("actividad", Long.class) > antigua.getTime());
    }

    @Test
    void rechazaTokenTrasExpiracionAbsoluta() {
        SecretKey clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64.getEncoder().encodeToString(new byte[32])));
        Date vencida = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        String tokenVencido = Jwts.builder()
                .subject("usuario-6")
                .issuedAt(new Date(vencida.getTime() - Duration.ofHours(1).toMillis()))
                .expiration(vencida)
                .claim("expiraEn", vencida.getTime())
                .claim("actividad", System.currentTimeMillis())
                .signWith(clave)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokenVencido));
        assertTrue(tokens.obtenerIdUsuario(request).isEmpty());
        assertTrue(tokens.sesionActiva(request, new MockHttpServletResponse()).isEmpty());
    }

    @Test
    void logoutSigueRevocandoElTokenRenovado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("usuario-7")));
        assertEquals("usuario-7", tokens.sesionActiva(request, new MockHttpServletResponse()).orElseThrow());

        tokens.revocarToken(request);

        assertTrue(tokens.obtenerIdUsuario(request).isEmpty());
    }

    @Test
    void renuevaTokenConservandoExpiracionAbsoluta() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("usuario-4")));

        Optional<String> idUsuario = tokens.sesionActiva(request, new MockHttpServletResponse());
        assertTrue(idUsuario.isPresent());
        assertEquals("usuario-4", idUsuario.orElseThrow());
    }

    @Test
    void revocaTokenEnLogout() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("usuario-5")));

        assertEquals("usuario-5", tokens.obtenerIdUsuario(request).orElseThrow());
        tokens.revocarToken(request);
        assertTrue(tokens.obtenerIdUsuario(request).isEmpty());
    }
}
