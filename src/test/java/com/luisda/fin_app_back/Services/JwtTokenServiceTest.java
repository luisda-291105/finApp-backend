package com.luisda.fin_app_back.Services;

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
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void rechazaTokenExpiradoPorInactividad() {
        SecretKey clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(Base64.getEncoder().encodeToString(new byte[32])));
        Date antes = new Date(System.currentTimeMillis() - 7_200_000L);
        String tokenInactivo = Jwts.builder()
                .subject("usuario-3")
                .issuedAt(antes)
                .expiration(new Date(antes.getTime() + Duration.ofSeconds(3600).toMillis()))
                .claim("expiraEn", antes.getTime() + Duration.ofSeconds(3600).toMillis())
                .claim("actividad", antes.getTime())
                .signWith(clave)
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokenInactivo));
        assertFalse(tokens.obtenerIdUsuario(request).isPresent());

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(tokens.sesionActiva(request, response).isPresent());
        assertEquals(200, response.getStatus());
    }

    @Test
    void renuevaTokenConservandoExpiracionAbsoluta() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("usuario-4")));

        Optional<String> idUsuario = tokens.sesionActiva(request, new MockHttpServletResponse());
        assertTrue(idUsuario.isPresent());
        assertEquals("usuario-4", idUsuario.orElseThrow());
    }
}
