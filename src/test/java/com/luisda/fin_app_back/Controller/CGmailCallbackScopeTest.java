package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Services.SClasificadorMovimiento;
import com.luisda.fin_app_back.Services.SGmailEntradas;
import com.luisda.fin_app_back.Services.SGmailMensajes;
import com.luisda.fin_app_back.Services.SImportacionLoteGmail;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SSuscripcionGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RF-23/RF-24/RF-25/RF-26 — el callback persiste el permiso realmente concedido por Google:
 * un consentimiento con `gmail.modify` autoriza la cuenta; uno parcial en la autorización explícita
 * (`expected_account_id`) responde `409 requires_scope_authorization` sin marcarla autorizada ni
 * activar nada; una conexión inicial sin `expected_account_id` se guarda y responde 200 aunque solo
 * tenga `gmail.readonly`; una conexión revocada/expirada sigue respondiendo `409 requires_reconnect`.
 */
class CGmailCallbackScopeTest {

    private static final String URL_TOKEN = "https://oauth2.googleapis.com/token";
    private static final String URL_PERFIL = "https://gmail.googleapis.com/gmail/v1/users/me/profile";
    private static final String ALCANCE_MODIFY = "https://www.googleapis.com/auth/gmail.modify";
    private static final String ALCANCE_LECTURA = "https://www.googleapis.com/auth/gmail.readonly";

    private IFuentes fuentes;
    private SGmailMensajes servicioMensajes;
    private IGmailMensajes mensajes;
    private SGmailEntradas entradas;
    private SUsuarioActivo usuarioActivo;
    private MockRestServiceServer servidor;
    private CGmail gmail;
    private MFuente cuenta;

    @BeforeEach
    void preparar() throws Exception {
        fuentes = mock(IFuentes.class);
        servicioMensajes = mock(SGmailMensajes.class);
        mensajes = mock(IGmailMensajes.class);
        entradas = mock(SGmailEntradas.class);
        usuarioActivo = mock(SUsuarioActivo.class);
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        gmail = new CGmail(fuentes, mensajes, mock(SSuscripcionGmail.class), entradas,
                servicioMensajes, usuarioActivo, mock(SMovimientoContable.class),
                mock(SClasificadorMovimiento.class), mock(SImportacionLoteGmail.class));
        inyectar("clienteHttp", constructor.build());
        inyectar("origenesPermitidos", "http://localhost:5173");
        inyectar("temaPubSub", "");
        inyectar("audienciaPubSub", "");
        inyectar("cuentaServicioPubSub", "");

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        when(servicioMensajes.resolverCredenciales())
                .thenReturn(new SGmailMensajes.CredencialesOAuth("cliente", "secreto"));
        when(servicioMensajes.cifrar(anyString(), anyString())).thenReturn("cifrado");
        when(servicioMensajes.descifrar(anyString(), anyString())).thenReturn("descifrado");

        cuenta = new MFuente();
        cuenta.setIdFuente("cuenta-1");
        cuenta.setTipo("CORREO");
        cuenta.setContenido("usuario@gmail.com");
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1"))
                .thenReturn(Optional.of(cuenta));
        when(fuentes.save(any(MFuente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void unConsentimientoConGmailModifyPersisteElAlcanceYAutorizaLaCuenta() throws Exception {
        servidor.expect(requestTo(URL_TOKEN)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-google\",\"refresh_token\":\"refresh-google\","
                        + "\"expires_in\":3600,\"scope\":\"openid " + ALCANCE_LECTURA + " " + ALCANCE_MODIFY + "\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL_PERFIL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"emailAddress\":\"usuario@gmail.com\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<?> respuesta = gmail.canjearCodigo(
                new CGmail.SolicitudCodigo("codigo", "cuenta-1"),
                "http://localhost:5173", "XmlHttpRequest", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        assertTrue(cuenta.tienePermisoModificarGmail());
        assertTrue(cuenta.getGmailScopeConcedido().contains(ALCANCE_MODIFY));
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals("usuario@gmail.com", cuerpo.get("email"));
        assertEquals("cuenta-1", cuerpo.get("account_id"));
        servidor.verify();
    }

    @Test
    void unConsentimientoParcialNoAutorizaYRespondeRequiresScopeAuthorization() throws Exception {
        servidor.expect(requestTo(URL_TOKEN)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-google\",\"refresh_token\":\"refresh-google\","
                        + "\"expires_in\":3600,\"scope\":\"openid " + ALCANCE_LECTURA + "\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL_PERFIL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"emailAddress\":\"usuario@gmail.com\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<?> respuesta = gmail.canjearCodigo(
                new CGmail.SolicitudCodigo("codigo", "cuenta-1"),
                "http://localhost:5173", "XmlHttpRequest", mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_scope_authorization"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
        assertFalse(cuenta.tienePermisoModificarGmail());
        assertFalse(cuenta.getGmailScopeConcedido().contains(ALCANCE_MODIFY));
        // El consentimiento parcial no altera correos ni activa nada.
        verify(mensajes, never()).save(any());
        verify(entradas, never()).encolarNotificacion(any(), any());
        verify(entradas, never()).encolarMensaje(any(), any());
        servidor.verify();
    }

    @Test
    void unaConexionInicialConSoloLecturaResponde200YPersisteElAlcance() throws Exception {
        servidor.expect(requestTo(URL_TOKEN)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-google\",\"refresh_token\":\"refresh-google\","
                        + "\"expires_in\":3600,\"scope\":\"openid " + ALCANCE_LECTURA + "\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL_PERFIL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"emailAddress\":\"usuario@gmail.com\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<?> respuesta = gmail.canjearCodigo(
                new CGmail.SolicitudCodigo("codigo", null),
                "http://localhost:5173", "XmlHttpRequest", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals("usuario@gmail.com", cuerpo.get("email"));
        assertFalse(cuerpo.containsKey("requires_scope_authorization"));

        // Sin expected_account_id es una conexión nueva: se guarda el scope concedido y se devuelve
        // la cuenta, aunque Google solo haya concedido gmail.readonly (el ampliado se pide después).
        ArgumentCaptor<MFuente> captor = ArgumentCaptor.forClass(MFuente.class);
        verify(fuentes).save(captor.capture());
        MFuente guardada = captor.getValue();
        assertEquals("usuario@gmail.com", guardada.getContenido());
        assertTrue(guardada.getGmailScopeConcedido().contains(ALCANCE_LECTURA));
        assertFalse(guardada.tienePermisoModificarGmail());
        assertEquals(guardada.getIdFuente(), cuerpo.get("account_id"));
        servidor.verify();
    }

    @Test
    void unaConexionRevocadaSigueRespondiendoRequiresReconnect() throws Exception {
        MFuente desconectada = new MFuente();
        desconectada.setIdFuente("cuenta-1");
        desconectada.setTipo("CORREO");
        desconectada.setContenido("usuario@gmail.com");
        desconectada.setRefreshTokenCifrado("refresh-cifrado");
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1"))
                .thenReturn(Optional.of(desconectada));
        // Google responde al refresh sin access_token: la cuenta requiere reconexión, no permiso ampliado.
        servidor.expect(requestTo(URL_TOKEN)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        ResponseEntity<?> respuesta = gmail.obtenerConexionCuenta("cuenta-1", mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_reconnect"));
        assertFalse(cuerpo.containsKey("requires_scope_authorization"));
        servidor.verify();
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = CGmail.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(gmail, valor);
    }
}
