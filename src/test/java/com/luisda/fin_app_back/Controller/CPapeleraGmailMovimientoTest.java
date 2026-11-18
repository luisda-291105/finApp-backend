package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GmailPapeleraMovimientoDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Services.SPapeleraGmail;
import com.luisda.fin_app_back.Services.SReglaPapeleraGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-01..RF-05, RF-13..RF-20, RF-23 — sesión/ownership, traducción de errores a `{ "message": … }`
 * + flag y respuestas `200` de mover/restaurar (`410` purgado y `409 mail_unavailable` distinguibles
 * de `502`/`503`).
 */
class CPapeleraGmailMovimientoTest {

    private IFuentes fuentes;
    private SPapeleraGmail papelera;
    private SReglaPapeleraGmail regla;
    private SUsuarioActivo usuarioActivo;
    private CPapeleraGmail controlador;
    private MFuente cuenta;

    @BeforeEach
    void preparar() {
        fuentes = mock(IFuentes.class);
        papelera = mock(SPapeleraGmail.class);
        regla = mock(SReglaPapeleraGmail.class);
        usuarioActivo = mock(SUsuarioActivo.class);
        controlador = new CPapeleraGmail(fuentes, papelera, regla, usuarioActivo);

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));

        cuenta = new MFuente("cuenta-1", "CORREO", "usuario@gmail.com", true, LocalDate.now());
        cuenta.setAccessTokenCifrado("token-cifrado");
        cuenta.setExpiraEn(Instant.now().plusSeconds(3600));
        cuenta.setGmailScopeConcedido(MFuente.ALCANCE_GMAIL_MODIFICAR);
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.of(cuenta));
    }

    @Test
    void sinSesionMoverResponde401() throws Exception {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(401, respuesta.getStatusCode().value());
        verify(papelera, never()).mover(any(), anyString());
    }

    @Test
    void sinSesionRestaurarResponde401() throws Exception {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.restaurar("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(401, respuesta.getStatusCode().value());
        verify(papelera, never()).restaurar(any(), anyString());
    }

    @Test
    void cuentaAjenaResponde404() {
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.empty());

        assertEquals(404, controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class)).getStatusCode().value());
        assertEquals(404, controlador.restaurar("cuenta-1", "m1", mock(HttpServletRequest.class)).getStatusCode().value());
    }

    @Test
    void sinPermisoAmpliadoResponde409RequiresScopeAuthorization() throws Exception {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_scope_authorization"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
        verify(papelera, never()).mover(any(), anyString());
    }

    @Test
    void moverResponde200ConElDto() throws Exception {
        when(papelera.mover(cuenta, "m1")).thenReturn(new GmailPapeleraMovimientoDTO("m1", "EN_PAPELERA", false));

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        GmailPapeleraMovimientoDTO cuerpo = (GmailPapeleraMovimientoDTO) respuesta.getBody();
        assertEquals("m1", cuerpo.mensajeId());
        assertEquals("EN_PAPELERA", cuerpo.estado());
        assertEquals(false, cuerpo.movidoAutomaticamente());
    }

    @Test
    void restaurarResponde200ConElDto() throws Exception {
        when(papelera.restaurar(cuenta, "m1")).thenReturn(new GmailPapeleraMovimientoDTO("m1", "EN_BANDEJA", null));

        ResponseEntity<?> respuesta = controlador.restaurar("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        GmailPapeleraMovimientoDTO cuerpo = (GmailPapeleraMovimientoDTO) respuesta.getBody();
        assertEquals("EN_BANDEJA", cuerpo.estado());
    }

    @Test
    void unCorreoPurgadoResponde410() throws Exception {
        when(papelera.restaurar(cuenta, "m1")).thenThrow(new SPapeleraGmail.CorreoPurgadoException());

        ResponseEntity<?> respuesta = controlador.restaurar("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(410, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertTrue(cuerpo.containsKey("message"));
        assertFalse(cuerpo.containsKey("mail_unavailable"));
    }

    @Test
    void unCorreoNoDisponibleResponde409MailUnavailable() throws Exception {
        when(papelera.restaurar(cuenta, "m1")).thenThrow(new SPapeleraGmail.CorreoNoDisponibleException());

        ResponseEntity<?> respuesta = controlador.restaurar("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("mail_unavailable"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
    }

    @Test
    void unMensajeDeOtraCuentaResponde404() throws Exception {
        when(papelera.mover(cuenta, "m1")).thenThrow(new SPapeleraGmail.CorreoNoEncontradoException());

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void unFalloDeGmailResponde502() throws Exception {
        when(papelera.mover(cuenta, "m1")).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(502, respuesta.getStatusCode().value());
        assertTrue(((Map<?, ?>) respuesta.getBody()).containsKey("message"));
    }

    @Test
    void unaConexionRevocadaResponde409RequiresReconnect() throws Exception {
        when(papelera.mover(cuenta, "m1")).thenThrow(new SPapeleraGmail.CuentaGmailRequiereReconectarException());

        ResponseEntity<?> respuesta = controlador.mover("cuenta-1", "m1", mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_reconnect"));
        assertFalse(cuerpo.containsKey("requires_scope_authorization"));
    }
}
