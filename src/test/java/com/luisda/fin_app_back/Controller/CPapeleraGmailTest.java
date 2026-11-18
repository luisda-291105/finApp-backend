package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GmailPapeleraDTO;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-11/RF-23 — sesión y propiedad de la cuenta, distinción entre papelera vacía y fallo de Gmail,
 * y `409 requires_scope_authorization` cuando falta el permiso ampliado.
 */
class CPapeleraGmailTest {

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
    void sinSesionResponde401() {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(401, respuesta.getStatusCode().value());
    }

    @Test
    void unaCuentaAjenaResponde404() {
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void sinPermisoAmpliadoResponde409RequiresScopeAuthorization() throws Exception {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_scope_authorization"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
        verify(papelera, never()).listar(any(), anyString());
    }

    @Test
    void unaPapeleraVaciaResponde200ConListaVacia() throws Exception {
        when(papelera.listar(cuenta, null)).thenReturn(new GmailPapeleraDTO("cuenta-1", List.of(), 0, null));

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        GmailPapeleraDTO cuerpo = (GmailPapeleraDTO) respuesta.getBody();
        assertTrue(cuerpo.correos().isEmpty());
        assertEquals(0, cuerpo.total());
    }

    @Test
    void unFalloDeGmailResponde502YNoSePresentaComoVacia() throws Exception {
        when(papelera.listar(cuenta, null)).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(502, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertTrue(cuerpo.containsKey("message"));
    }

    @Test
    void unaConexionRevocadaResponde409RequiresReconnect() throws Exception {
        when(papelera.listar(cuenta, null)).thenThrow(new SPapeleraGmail.CuentaGmailRequiereReconectarException());

        ResponseEntity<?> respuesta = controlador.listar("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_reconnect"));
        assertFalse(cuerpo.containsKey("requires_scope_authorization"));
    }
}
