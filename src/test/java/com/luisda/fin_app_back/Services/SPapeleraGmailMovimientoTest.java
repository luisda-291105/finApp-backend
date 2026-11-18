package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GmailPapeleraMovimientoDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RF-01..RF-05, RF-13..RF-20, RF-27, RF-28, RF-40 — mover y restaurar manuales con `messages.modify`,
 * idempotencia, distinción entre correo purgado (`410`) y no disponible (`mail_unavailable`), descarte
 * persistente de la alerta sin tocar los movimientos contables y exención al restaurar.
 */
class SPapeleraGmailMovimientoTest {

    private IGmailMensajes mensajes;
    private SGmailMensajes servicioMensajes;
    private IFuentes fuentes;
    private MockRestServiceServer servidor;
    private SPapeleraGmail papelera;
    private MFuente cuenta;

    @BeforeEach
    void preparar() throws Exception {
        mensajes = mock(IGmailMensajes.class);
        servicioMensajes = mock(SGmailMensajes.class);
        fuentes = mock(IFuentes.class);
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        papelera = new SPapeleraGmail(mensajes, servicioMensajes, fuentes, constructor);

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        cuenta = new MFuente("cuenta-1", "CORREO", "usuario@gmail.com", true, LocalDate.now());
        cuenta.setUsuario(usuario);
        cuenta.setAccessTokenCifrado("token-cifrado");
        cuenta.setExpiraEn(Instant.now().plusSeconds(3600));
        cuenta.setGmailScopeConcedido(MFuente.ALCANCE_GMAIL_MODIFICAR);

        when(servicioMensajes.resolverCredenciales())
                .thenReturn(new SGmailMensajes.CredencialesOAuth("cliente", "secreto"));
        when(servicioMensajes.descifrar(anyString(), anyString())).thenReturn("token-de-acceso");
        when(fuentes.save(any(MFuente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(mensajes.save(any(MGmailMensaje.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void moverAgregaTrashActualizaElEstadoYDescartaLaAlerta() throws Exception {
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.now());
        local.setMovimientoAutomaticoEstado("REGISTRADO");
        local.setMovimientoAutomaticoId("mov-1");
        local.setMovimientoAlertaPendiente(true);
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));

        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("format", "metadata"))
                .andRespond(withSuccess("{\"id\":\"m1\",\"labelIds\":[\"INBOX\",\"IMPORTANT\"]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1/modify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.addLabelIds[0]").value("TRASH"))
                .andRespond(withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));

        GmailPapeleraMovimientoDTO resultado = papelera.mover(cuenta, "m1");

        assertEquals("m1", resultado.mensajeId());
        assertEquals("EN_PAPELERA", resultado.estado());
        assertEquals(false, resultado.movidoAutomaticamente());
        assertEquals("EN_PAPELERA", local.getPapeleraEstado());
        assertFalse(local.isPapeleraMovidoAutomaticamente());
        assertNull(local.getPapeleraMotivo());
        assertNull(local.getPapeleraCategoria());
        // RF-28: la alerta se descarta de forma persistente por el punto único de escritura.
        verify(servicioMensajes).descartarAlertaMovimiento(cuenta, "m1");
        // RF-27: el movimiento contable ya registrado permanece intacto.
        assertEquals("mov-1", local.getMovimientoAutomaticoId());
        assertEquals("REGISTRADO", local.getMovimientoAutomaticoEstado());
        servidor.verify();
    }

    @Test
    void moverEsIdempotenteSiElCorreoYaEstaEnLaPapelera() throws Exception {
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.now());
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"m1\",\"labelIds\":[\"TRASH\"]}", MediaType.APPLICATION_JSON));

        GmailPapeleraMovimientoDTO resultado = papelera.mover(cuenta, "m1");

        assertEquals("EN_PAPELERA", resultado.estado());
        assertEquals(false, resultado.movidoAutomaticamente());
        verify(mensajes, never()).save(any(MGmailMensaje.class));
        verify(servicioMensajes, never()).descartarAlertaMovimiento(any(), anyString());
        servidor.verify();
    }

    @Test
    void moverUnCorreoPurgadoLanzaPurgadoYEliminaLosDatosLocales() throws Exception {
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.empty());
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(SPapeleraGmail.CorreoPurgadoException.class, () -> papelera.mover(cuenta, "m1"));

        verify(mensajes).deleteByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1");
        servidor.verify();
    }

    @Test
    void moverUnMensajeDeOtraCuentaLanzaNoEncontrado() throws Exception {
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.empty());
        MFuente otra = new MFuente("cuenta-2", "CORREO", "otra@gmail.com", true, LocalDate.now());
        otra.setUsuario(cuenta.getUsuario());
        when(mensajes.findFirstByGoogleMessageIdAndCuenta_Usuario_IdUsuario("m1", "usuario-1"))
                .thenReturn(Optional.of(new MGmailMensaje("fila-2", otra, "m1", Instant.now())));
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(SPapeleraGmail.CorreoNoEncontradoException.class, () -> papelera.mover(cuenta, "m1"));

        verify(mensajes, never()).deleteByCuenta_IdFuenteAndGoogleMessageId(anyString(), anyString());
        servidor.verify();
    }

    @Test
    void restaurarQuitaTrashYMarcaElCorreoExento() throws Exception {
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.now());
        local.setPapeleraEstado("EN_PAPELERA");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));

        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"m1\",\"labelIds\":[\"TRASH\"]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1/modify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.removeLabelIds[0]").value("TRASH"))
                .andRespond(withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));

        GmailPapeleraMovimientoDTO resultado = papelera.restaurar(cuenta, "m1");

        assertEquals("m1", resultado.mensajeId());
        assertEquals("EN_BANDEJA", resultado.estado());
        assertNull(resultado.movidoAutomaticamente());
        assertEquals("EN_BANDEJA", local.getPapeleraEstado());
        assertTrue(local.isPapeleraExenta());
        servidor.verify();
    }

    @Test
    void restaurarEsIdempotenteSiElCorreoYaNoEstaEnLaPapelera() throws Exception {
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.now());
        local.setPapeleraEstado("EN_BANDEJA");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"m1\",\"labelIds\":[\"INBOX\"]}", MediaType.APPLICATION_JSON));

        GmailPapeleraMovimientoDTO resultado = papelera.restaurar(cuenta, "m1");

        assertEquals("EN_BANDEJA", resultado.estado());
        assertNull(resultado.movidoAutomaticamente());
        verify(mensajes, never()).save(any(MGmailMensaje.class));
        servidor.verify();
    }

    @Test
    void restaurarUnCorreoQueYaNoEstaDisponibleLanzaNoDisponible() throws Exception {
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.empty());
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"m1\",\"labelIds\":[\"INBOX\"]}", MediaType.APPLICATION_JSON));

        assertThrows(SPapeleraGmail.CorreoNoDisponibleException.class, () -> papelera.restaurar(cuenta, "m1"));

        servidor.verify();
    }

    @Test
    void restaurarUnCorreoPurgadoLanzaPurgado() throws Exception {
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.empty());
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(SPapeleraGmail.CorreoPurgadoException.class, () -> papelera.restaurar(cuenta, "m1"));

        servidor.verify();
    }

    @Test
    void restaurarUnCorreoNuncaSincronizadoCreaLaFilaYLaMarcaExenta() throws Exception {
        long fecha = Instant.parse("2026-11-01T10:00:00Z").toEpochMilli();
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.empty());
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"m1\",\"internalDate\":\"" + fecha + "\",\"labelIds\":[\"TRASH\"],"
                        + "\"payload\":{\"headers\":[{\"name\":\"From\",\"value\":\"promo@tienda.com\"},"
                        + "{\"name\":\"Subject\",\"value\":\"Oferta\"}]}}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1/modify"))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));

        GmailPapeleraMovimientoDTO resultado = papelera.restaurar(cuenta, "m1");

        assertEquals("EN_BANDEJA", resultado.estado());
        ArgumentCaptor<MGmailMensaje> captor = ArgumentCaptor.forClass(MGmailMensaje.class);
        verify(mensajes).save(captor.capture());
        MGmailMensaje guardado = captor.getValue();
        assertEquals("m1", guardado.getGoogleMessageId());
        assertNotNull(guardado.getId());
        assertTrue(guardado.isPapeleraExenta());
        assertEquals("EN_BANDEJA", guardado.getPapeleraEstado());
        assertEquals("promo@tienda.com", guardado.getRemitente());
        servidor.verify();
    }
}
