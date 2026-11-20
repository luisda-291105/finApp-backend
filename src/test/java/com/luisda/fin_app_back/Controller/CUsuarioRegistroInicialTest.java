package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.EstadoRegistroDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.UsuarioLoginDTO;
import com.luisda.fin_app_back.Services.JwtTokenService;
import com.luisda.fin_app_back.Services.SAccesoAdmin;
import com.luisda.fin_app_back.Services.SIntentosRateLimit;
import com.luisda.fin_app_back.Services.SUsuario;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import com.luisda.fin_app_back.Services.SVerificadorContrasena;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cubre la distinción entre "sin cuentas registradas" y credenciales incorrectas. */
class CUsuarioRegistroInicialTest {
    private SUsuario usuarios;
    private SIntentosRateLimit rateLimit;
    private CUsuario controlador;
    private HttpServletRequest solicitud;

    @BeforeEach
    void preparar() {
        usuarios = mock(SUsuario.class);
        rateLimit = mock(SIntentosRateLimit.class);
        when(rateLimit.registrar(anyString(), anyBoolean())).thenReturn(true);
        solicitud = mock(HttpServletRequest.class);
        when(solicitud.getRemoteAddr()).thenReturn("127.0.0.1");
        controlador = new CUsuario(mock(SUsuarioActivo.class), mock(SVerificadorContrasena.class),
                usuarios, mock(JwtTokenService.class), rateLimit, mock(SAccesoAdmin.class));
    }

    @Test
    void loginSinUsuariosRegistradosNoAutenticaNiRegistraFallo() {
        when(usuarios.hayUsuariosRegistrados()).thenReturn(false);

        ResponseEntity<?> respuesta = controlador.iniciarSesion(
                new UsuarioLoginDTO("ana", "secreta123"), solicitud);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        RespuestaModalDTO cuerpo = (RespuestaModalDTO) respuesta.getBody();
        assertNotNull(cuerpo);
        assertEquals("aviso", cuerpo.tipo());
        assertEquals("credenciales", cuerpo.campo());
        assertEquals("Aún no hay cuentas registradas. Crea una cuenta para comenzar.", cuerpo.mensaje());
        verify(usuarios, never()).autenticar(anyString(), anyString(), any(SVerificadorContrasena.class));
        verify(rateLimit, never()).registrar(anyString(), eq(true));
    }

    @Test
    void loginConUsuariosRegistradosConservaElMensajeGenerico() {
        when(usuarios.hayUsuariosRegistrados()).thenReturn(true);
        when(usuarios.autenticar(anyString(), anyString(), any(SVerificadorContrasena.class))).thenReturn(null);

        ResponseEntity<?> respuesta = controlador.iniciarSesion(
                new UsuarioLoginDTO("ana", "incorrecta123"), solicitud);

        assertEquals(HttpStatus.UNAUTHORIZED, respuesta.getStatusCode());
        RespuestaModalDTO cuerpo = (RespuestaModalDTO) respuesta.getBody();
        assertNotNull(cuerpo);
        assertEquals("El usuario o la contraseña no son correctos.", cuerpo.mensaje());
        verify(rateLimit).registrar(anyString(), eq(true));
    }

    @Test
    void estadoDelRegistroEsPublicoYReflejaLaBase() {
        when(usuarios.hayUsuariosRegistrados()).thenReturn(false);

        ResponseEntity<?> respuesta = controlador.hayRegistrados();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        RespuestaModalDTO cuerpo = (RespuestaModalDTO) respuesta.getBody();
        assertNotNull(cuerpo);
        assertEquals("exito", cuerpo.tipo());
        EstadoRegistroDTO datos = (EstadoRegistroDTO) cuerpo.datos();
        assertNotNull(datos);
        assertFalse(datos.hayUsuarios());
    }
}
