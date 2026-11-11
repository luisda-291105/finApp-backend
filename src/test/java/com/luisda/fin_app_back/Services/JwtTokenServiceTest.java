package com.luisda.fin_app_back.Services;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

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
}
