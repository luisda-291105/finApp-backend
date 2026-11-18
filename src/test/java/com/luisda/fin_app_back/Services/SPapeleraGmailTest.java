package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GmailPapeleraDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RF-06..RF-12, RF-21, RF-22 — la Papelera se lista en vivo desde el TRASH de Gmail con paginación,
 * incluye correos nunca sincronizados, obtiene metadatos por mensaje y combina el marcador local.
 */
class SPapeleraGmailTest {

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
    }

    @Test
    void listaLaPapeleraEnVivoConPaginacionYMarcadorLocal() throws Exception {
        long fecha = Instant.parse("2026-11-10T14:03:00Z").toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "in:trash"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"m1\"},{\"id\":\"m2\"}],\"nextPageToken\":\"tok-2\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("format", "metadata"))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"TRASH\"]", "promo@tienda.com", "Oferta del mes"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"TRASH\"]", "news@foro.com", "Resumen semanal"),
                        MediaType.APPLICATION_JSON));

        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.ofEpochMilli(fecha));
        local.setPapeleraMovidoAutomaticamente(true);
        local.setPapeleraMotivo("CATEGORY_PROMOTIONS");
        local.setPapeleraCategoria("CATEGORY_PROMOTIONS");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m2")).thenReturn(Optional.empty());

        GmailPapeleraDTO resultado = papelera.listar(cuenta, null);

        assertEquals("cuenta-1", resultado.cuentaId());
        assertEquals(2, resultado.total());
        assertEquals("tok-2", resultado.siguientePageToken());

        GmailPapeleraDTO.CorreoPapelera primero = resultado.correos().get(0);
        assertEquals("m1", primero.mensajeId());
        assertEquals("promo@tienda.com", primero.remitente());
        assertEquals("Oferta del mes", primero.asunto());
        assertEquals("2026-11-10T14:03:00Z", primero.fecha());
        assertEquals("2026-12-10T14:03:00Z", primero.eliminacionEstimadaEn());
        assertTrue(primero.movidoAutomaticamente());
        assertEquals("CATEGORY_PROMOTIONS", primero.motivoAutomatico());
        assertEquals("CATEGORY_PROMOTIONS", primero.categoria());

        GmailPapeleraDTO.CorreoPapelera segundo = resultado.correos().get(1);
        assertEquals("m2", segundo.mensajeId());
        assertFalse(segundo.movidoAutomaticamente());
        assertNull(segundo.motivoAutomatico());
        assertNull(segundo.categoria());
        servidor.verify();
    }

    @Test
    void laPaginaSiguienteUsaElTokenRecibidoYUnaPapeleraVaciaNoConsultaMetadatos() throws Exception {
        servidor.expect(requestTo(containsString("pageToken=tok-1"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"messages\":[]}", MediaType.APPLICATION_JSON));

        GmailPapeleraDTO resultado = papelera.listar(cuenta, "tok-1");

        assertEquals(0, resultado.total());
        assertTrue(resultado.correos().isEmpty());
        assertNull(resultado.siguientePageToken());
        servidor.verify();
    }

    @Test
    void omiteMensajesQueGmailYaNoTieneEnLaPapelera() throws Exception {
        long fecha = Instant.now().toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}", MediaType.APPLICATION_JSON));
        // El original sigue en la papelera; el mensaje nuevo del mismo hilo ya no está en TRASH (RF-32).
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"TRASH\"]", "foro@foro.com", "Original"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\"]", "foro@foro.com", "Respuesta"),
                        MediaType.APPLICATION_JSON));

        GmailPapeleraDTO resultado = papelera.listar(cuenta, null);

        assertEquals(1, resultado.correos().size());
        assertEquals("m1", resultado.correos().get(0).mensajeId());
        servidor.verify();
    }

    @Test
    void listaCorreosMasAntiguosQueLaRetencionDeLaCache() throws Exception {
        long fecha = Instant.now().minus(java.time.Duration.ofDays(400)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"viejo\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/viejo?")))
                .andRespond(withSuccess(metadata("viejo", fecha, "[\"TRASH\"]", "antiguo@tienda.com", "Muy antiguo"),
                        MediaType.APPLICATION_JSON));

        GmailPapeleraDTO resultado = papelera.listar(cuenta, null);

        assertEquals(1, resultado.correos().size());
        assertEquals("viejo", resultado.correos().get(0).mensajeId());
        servidor.verify();
    }

    private static String metadata(String id, long internalDate, String labelIds, String from, String subject) {
        return "{\"id\":\"" + id + "\",\"internalDate\":\"" + internalDate + "\",\"labelIds\":" + labelIds
                + ",\"payload\":{\"headers\":[{\"name\":\"From\",\"value\":\"" + from + "\"},"
                + "{\"name\":\"Subject\",\"value\":\"" + subject + "\"}]}}";
    }
}
