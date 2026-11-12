package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GoogleCompletarRegistroDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Services.JwtTokenService;
import com.luisda.fin_app_back.Services.SAuthGoogle;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CAuthGoogleTest {

    private SAuthGoogle authGoogle;
    private JwtTokenService tokens;
    private CAuthGoogle controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        authGoogle = mock(SAuthGoogle.class);
        tokens = mock(JwtTokenService.class);
        controller = new CAuthGoogle(authGoogle, tokens);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void callbackRedirigeASuccessCuandoLoginEsExitoso() {
        MUsuario usuario = new MUsuario("1001", "Ana", "hash", LocalDate.now());
        when(authGoogle.procesarCallback(eq("codigo123"), eq("state123"), any()))
                .thenReturn(new SAuthGoogle.ResultadoCallbackGoogle.LoginExitoso(usuario));
        when(tokens.emitir("1001")).thenReturn("jwt-token");
        when(tokens.cookie("jwt-token")).thenReturn(org.springframework.http.ResponseCookie.from("token", "jwt-token").build());

        ResponseEntity<Void> respuesta = controller.callback("codigo123", "state123", null, request);

        assertEquals(HttpStatus.FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getHeaders().getLocation());
        assertTrue(respuesta.getHeaders().getLocation().toString().contains("google=success"));
    }

    @Test
    void callbackRedirigeANotRegisteredCuandoUsuarioNoExisteEnLogin() {
        when(authGoogle.procesarCallback(eq("codigo123"), eq("state123"), any()))
                .thenReturn(new SAuthGoogle.ResultadoCallbackGoogle.NoRegistrado("nuevo@gmail.com"));

        ResponseEntity<Void> respuesta = controller.callback("codigo123", "state123", null, request);

        assertEquals(HttpStatus.FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getHeaders().getLocation());
        assertTrue(respuesta.getHeaders().getLocation().toString().contains("google=not_registered"));
    }

    @Test
    void callbackRedirigeAPendingRegistrationCuandoRegistroEsValido() {
        when(authGoogle.procesarCallback(eq("codigo123"), eq("state123"), any()))
                .thenReturn(new SAuthGoogle.ResultadoCallbackGoogle.RegistroPendiente("nuevo@gmail.com", "Nuevo"));

        ResponseEntity<Void> respuesta = controller.callback("codigo123", "state123", null, request);

        assertEquals(HttpStatus.FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getHeaders().getLocation());
        assertTrue(respuesta.getHeaders().getLocation().toString().contains("google=pending_registration"));
    }

    @Test
    void callbackRedirigeAAlreadyRegisteredCuandoUsuarioYaExisteEnRegistro() {
        when(authGoogle.procesarCallback(eq("codigo123"), eq("state123"), any()))
                .thenReturn(new SAuthGoogle.ResultadoCallbackGoogle.YaRegistrado());

        ResponseEntity<Void> respuesta = controller.callback("codigo123", "state123", null, request);

        assertEquals(HttpStatus.FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getHeaders().getLocation());
        assertTrue(respuesta.getHeaders().getLocation().toString().contains("google=already_registered"));
    }

    @Test
    void registroPendienteRetorna404SiNoExiste() {
        when(authGoogle.obtenerRegistroPendiente(any())).thenReturn(null);

        ResponseEntity<?> respuesta = controller.registroPendiente(request);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
    }

    @Test
    void registroPendienteRetornaDatosVerificadosSiExiste() {
        when(authGoogle.obtenerRegistroPendiente(any()))
                .thenReturn(new SAuthGoogle.RegistroGooglePendiente("sub123", "ana@gmail.com", "Ana"));

        ResponseEntity<?> respuesta = controller.registroPendiente(request);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    }

    @Test
    void completarRegistroCreaUsuarioYRetornaCreated() {
        GoogleCompletarRegistroDTO datos = new GoogleCompletarRegistroDTO("1002", "clave12345", "Carlos", "3001234567");
        MUsuario creado = new MUsuario("1002", "Carlos", "hash", LocalDate.now());
        when(authGoogle.completarRegistro(eq(datos), any())).thenReturn(creado);
        when(tokens.emitir("1002")).thenReturn("jwt-token");
        when(tokens.cookie("jwt-token")).thenReturn(org.springframework.http.ResponseCookie.from("token", "jwt-token").build());

        ResponseEntity<?> respuesta = controller.completarRegistro(datos, request);

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertTrue(respuesta.getBody() instanceof RespuestaModalDTO);
    }
}
