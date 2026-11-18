package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GmailListaBlancaDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraConfiguracionDTO;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-33, RF-34, RF-36, RF-37 — endpoints de configuración por categoría y lista blanca:
 * sesión/ownership, `200` de lectura sin exigir el permiso ampliado, `200`/`400`/`409` de la
 * actualización y `200`/`201`/`400`/`204`/`404` del CRUD de la lista blanca.
 */
class CPapeleraGmailConfiguracionTest {

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

    private static GmailPapeleraConfiguracionDTO configuracion(boolean scopeAutorizado) {
        return new GmailPapeleraConfiguracionDTO(List.of(
                new GmailPapeleraConfiguracionDTO.CategoriaConfigurada("CATEGORY_PROMOTIONS", false, false, false),
                new GmailPapeleraConfiguracionDTO.CategoriaConfigurada("CATEGORY_SOCIAL", false, false, false),
                new GmailPapeleraConfiguracionDTO.CategoriaConfigurada("CATEGORY_FORUMS", false, false, false),
                new GmailPapeleraConfiguracionDTO.CategoriaConfigurada("CATEGORY_UPDATES", false, true, false),
                new GmailPapeleraConfiguracionDTO.CategoriaConfigurada("SPAM", false, false, false)),
                7, 200, scopeAutorizado);
    }

    @Test
    void sinSesionConfiguracionResponde401() {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        assertEquals(401, controlador.configuracion("cuenta-1", mock(HttpServletRequest.class)).getStatusCode().value());
        verify(papelera, never()).configuracion(any());
    }

    @Test
    void cuentaAjenaConfiguracionResponde404() {
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.empty());

        assertEquals(404, controlador.configuracion("cuenta-1", mock(HttpServletRequest.class)).getStatusCode().value());
    }

    @Test
    void leerLaConfiguracionNoExigeElPermisoAmpliado() {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");
        when(papelera.configuracion(cuenta)).thenReturn(configuracion(false));

        ResponseEntity<?> respuesta = controlador.configuracion("cuenta-1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        assertFalse(((GmailPapeleraConfiguracionDTO) respuesta.getBody()).scopeAutorizado());
        verify(papelera).configuracion(cuenta);
    }

    @Test
    void actualizarLaConfiguracionResponde200() {
        GmailPapeleraConfiguracionDTO.Solicitud solicitud =
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_PROMOTIONS"), 7, 200, null);
        when(papelera.actualizarConfiguracion(cuenta, solicitud)).thenReturn(configuracion(true));

        ResponseEntity<?> respuesta = controlador.actualizarConfiguracion("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(((GmailPapeleraConfiguracionDTO) respuesta.getBody()).scopeAutorizado());
    }

    @Test
    void actualizarConUnValorInvalidoResponde400() {
        GmailPapeleraConfiguracionDTO.Solicitud solicitud =
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_INVENTADA"), 7, 200, null);
        when(papelera.actualizarConfiguracion(cuenta, solicitud))
                .thenThrow(new IllegalArgumentException("Categoría de Gmail no válida."));

        ResponseEntity<?> respuesta = controlador.actualizarConfiguracion("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertTrue(cuerpo.containsKey("message"));
        assertFalse(cuerpo.containsKey("requires_scope_authorization"));
    }

    @Test
    void activarSinPermisoAmpliadoResponde409RequiresScopeAuthorization() {
        GmailPapeleraConfiguracionDTO.Solicitud solicitud =
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_PROMOTIONS"), 7, 200, null);
        when(papelera.actualizarConfiguracion(cuenta, solicitud)).thenThrow(new SPapeleraGmail.RequiereScopeException());

        ResponseEntity<?> respuesta = controlador.actualizarConfiguracion("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_scope_authorization"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
    }

    @Test
    void listarLaListaBlancaResponde200() {
        when(papelera.listarListaBlanca(cuenta)).thenReturn(new GmailListaBlancaDTO(List.of(
                new GmailListaBlancaDTO.Remitente("lb-1", "banco@banco.com"))));

        ResponseEntity<?> respuesta = controlador.listarListaBlanca("cuenta-1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        GmailListaBlancaDTO cuerpo = (GmailListaBlancaDTO) respuesta.getBody();
        assertEquals(1, cuerpo.remitentes().size());
        assertEquals("banco@banco.com", cuerpo.remitentes().get(0).remitente());
    }

    @Test
    void agregarUnRemitenteResponde201() {
        GmailListaBlancaDTO.Solicitud solicitud = new GmailListaBlancaDTO.Solicitud("banco@banco.com");
        when(papelera.agregarRemitente(cuenta, "banco@banco.com"))
                .thenReturn(new GmailListaBlancaDTO.Remitente("lb-1", "banco@banco.com"));

        ResponseEntity<?> respuesta = controlador.agregarRemitente("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(201, respuesta.getStatusCode().value());
        assertEquals("lb-1", ((GmailListaBlancaDTO.Remitente) respuesta.getBody()).id());
    }

    @Test
    void agregarUnRemitenteInvalidoResponde400() {
        GmailListaBlancaDTO.Solicitud solicitud = new GmailListaBlancaDTO.Solicitud("sin-arroba");
        when(papelera.agregarRemitente(cuenta, "sin-arroba"))
                .thenThrow(new IllegalArgumentException("El remitente no es una dirección de correo válida."));

        ResponseEntity<?> respuesta = controlador.agregarRemitente("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        assertTrue(((Map<?, ?>) respuesta.getBody()).containsKey("message"));
    }

    @Test
    void eliminarUnRemitenteResponde204() {
        ResponseEntity<?> respuesta = controlador.eliminarRemitente("cuenta-1", "lb-1", mock(HttpServletRequest.class));

        assertEquals(204, respuesta.getStatusCode().value());
        assertNull(respuesta.getBody());
        verify(papelera).eliminarRemitente(cuenta, "lb-1");
    }

    @Test
    void eliminarUnRemitenteAusenteResponde404() {
        org.mockito.Mockito.doThrow(new SPapeleraGmail.RemitenteNoEncontradoException())
                .when(papelera).eliminarRemitente(eq(cuenta), eq("lb-x"));

        ResponseEntity<?> respuesta = controlador.eliminarRemitente("cuenta-1", "lb-x", mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
        assertTrue(((Map<?, ?>) respuesta.getBody()).containsKey("message"));
    }

    @Test
    void sinSesionLaListaBlancaResponde401() {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        assertEquals(401, controlador.listarListaBlanca("cuenta-1", mock(HttpServletRequest.class)).getStatusCode().value());
        verify(papelera, never()).listarListaBlanca(any());
        verify(papelera, never()).agregarRemitente(any(), anyString());
        verify(papelera, never()).eliminarRemitente(any(), anyString());
    }
}
