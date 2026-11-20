package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MGmailPapeleraCategoria;
import com.luisda.fin_app_back.Model.MGmailPapeleraConfiguracion;
import com.luisda.fin_app_back.Model.MGmailPapeleraListaBlanca;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Repository.IGmailPapeleraCategoria;
import com.luisda.fin_app_back.Repository.IGmailPapeleraConfiguracion;
import com.luisda.fin_app_back.Repository.IGmailPapeleraListaBlanca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * RF-33, RF-34, RF-35, RF-37, RF-40, CD-04 — la regla automática evalúa en vivo cada categoría
 * activa (`messages.list`), aplica las condiciones de elegibilidad (INBOX/SPAM + antigüedad +
 * activación), TODAS las exclusiones de RF-35 (destacados, importantes, etiquetas personalizadas,
 * hilos con enviados, remitentes prioritarios, suscritos, movimiento registrado, alerta pendiente o
 * estados, exentos y lista blanca) y el refuerzo de UPDATES, mueve con `batchModify` en lotes ≤1000
 * con backoff, respeta el límite por ejecución con remanente y no se ejecuta sin permiso ampliado.
 */
class SReglaPapeleraGmailTest {

    private IGmailPapeleraConfiguracion configuraciones;
    private IGmailPapeleraCategoria categorias;
    private IGmailPapeleraListaBlanca listaBlanca;
    private IGmailMensajes mensajes;
    private SGmailMensajes servicioMensajes;
    private SSuscripcionGmail suscripcionesGmail;
    private IFuentes fuentes;
    private MockRestServiceServer servidor;
    private SReglaPapeleraGmail regla;
    private MFuente cuenta;

    @BeforeEach
    void preparar() throws Exception {
        configuraciones = mock(IGmailPapeleraConfiguracion.class);
        categorias = mock(IGmailPapeleraCategoria.class);
        listaBlanca = mock(IGmailPapeleraListaBlanca.class);
        mensajes = mock(IGmailMensajes.class);
        servicioMensajes = mock(SGmailMensajes.class);
        suscripcionesGmail = mock(SSuscripcionGmail.class);
        fuentes = mock(IFuentes.class);
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        regla = new ReglaSinEspera(configuraciones, categorias, listaBlanca, mensajes, servicioMensajes,
                suscripcionesGmail, fuentes, new SRegistroPapeleraGmail(), Runnable::run, constructor);

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
    void mueveSoloCorreosEnInboxQueSuperanLaAntiguedad() throws Exception {
        Instant activacion = Instant.now().minus(Duration.ofDays(30));
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", activacion)));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "category:CATEGORY_PROMOTIONS%20in:inbox%20-in:trash%20older_than:7d"))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("format", "metadata"))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andExpect(jsonPath("$.addLabelIds", contains("TRASH")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(1, resultado.movidos());
        assertFalse(resultado.limiteEjecucionAlcanzado());
        assertEquals(0, resultado.remanente());
        ArgumentCaptor<MGmailMensaje> captor = ArgumentCaptor.forClass(MGmailMensaje.class);
        verify(mensajes).save(captor.capture());
        MGmailMensaje guardado = captor.getValue();
        assertEquals("m1", guardado.getGoogleMessageId());
        assertEquals("EN_PAPELERA", guardado.getPapeleraEstado());
        assertTrue(guardado.isPapeleraMovidoAutomaticamente());
        assertEquals("CATEGORY_PROMOTIONS", guardado.getPapeleraMotivo());
        assertEquals("CATEGORY_PROMOTIONS", guardado.getPapeleraCategoria());
        servidor.verify();
    }

    @Test
    void noMueveCorreosQueNoSiguenEnInboxNiEstanEnPapelera() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        verify(mensajes, never()).save(any(MGmailMensaje.class));
        servidor.verify();
    }

    @Test
    void noMueveCorreosQueNoSuperanLaAntiguedadMinima() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(2)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void noMueveCorreosRecibidosAntesDeLaActivacionDeLaCategoria() throws Exception {
        Instant activacion = Instant.now().minus(Duration.ofDays(2));
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", activacion)));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeCorreosDestacadosEImportantes() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"STARRED\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"IMPORTANT\",\"CATEGORY_PROMOTIONS\"]", "news@marca.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeCorreosConEtiquetasPersonalizadas() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\",\"Label_123\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeHilosConMensajesEnviados() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_FORUMS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_FORUMS\"]", "foro@foro.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/threads/t1?")))
                .andRespond(withSuccess("{\"id\":\"t1\",\"messages\":[{\"id\":\"m9\",\"labelIds\":[\"SENT\"]}]}",
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeRemitentesPrioritarios() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]",
                        "Bancolombia <alertasynotificaciones@ayn.notificacionesbancolombia.com>"), MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeRemitentesSuscritos() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        when(suscripcionesGmail.existeEnCuenta("cuenta-1", "promo@tienda.com")).thenReturn(true);
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeCorreosConMovimientoContableRegistrado() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_UPDATES", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_UPDATES\"]", "ofertas@tienda.com"),
                        MediaType.APPLICATION_JSON));
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.ofEpochMilli(fecha));
        local.setMovimientoAutomaticoEstado("REGISTRADO");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeCorreosConAlertaPendienteOEnEstadoDeBolsilloDuplicadoError() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "news@marca.com"),
                        MediaType.APPLICATION_JSON));
        MGmailMensaje conAlerta = new MGmailMensaje("fila-1", cuenta, "m1", Instant.ofEpochMilli(fecha));
        conAlerta.setMovimientoAlertaPendiente(true);
        MGmailMensaje duplicado = new MGmailMensaje("fila-2", cuenta, "m2", Instant.ofEpochMilli(fecha));
        duplicado.setMovimientoAutomaticoEstado("DUPLICADO");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(conAlerta));
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m2")).thenReturn(Optional.of(duplicado));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeCorreosMarcadosComoExentos() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        MGmailMensaje local = new MGmailMensaje("fila-1", cuenta, "m1", Instant.ofEpochMilli(fecha));
        local.setPapeleraExenta(true);
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(local));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void excluyeRemitentesDeLaListaBlanca() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        when(listaBlanca.findByFuente_IdFuenteOrderByRemitenteAsc("cuenta-1"))
                .thenReturn(List.of(new MGmailPapeleraListaBlanca("lb-1", cuenta, "promo@tienda.com")));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void updatesNoMueveRemitentesBancariosNiRecibosPeroSiOtros() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_UPDATES", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_UPDATES\"]",
                        "Notificaciones <notificaciones@bancolombia.com.co>"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_UPDATES\"]", "ofertas@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m2")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(1, resultado.movidos());
        servidor.verify();
    }

    @Test
    void respetaElLimitePorEjecucionYReportaElRemanente() throws Exception {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        config.setAntiguedadMinimaDias(7);
        config.setLimitePorEjecucion(1);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "news@marca.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(1, resultado.movidos());
        assertTrue(resultado.limiteEjecucionAlcanzado());
        assertEquals(1, resultado.remanente());
        servidor.verify();
    }

    @Test
    void partirEnLotesNuncaSuperaMilIdentificadores() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1001; i++) ids.add("m" + i);

        List<List<String>> lotes = SReglaPapeleraGmail.partirEnLotes(ids);

        assertEquals(2, lotes.size());
        assertEquals(1000, lotes.get(0).size());
        assertEquals(1, lotes.get(1).size());
        assertEquals("m0", lotes.get(0).get(0));
        assertEquals("m1000", lotes.get(1).get(0));
    }

    @Test
    void reintentaConBackoffAnteCuotaAgotadaYCompletaElMovimiento() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(1, resultado.movidos());
        servidor.verify();
    }

    @Test
    void un403DeBatchModifyNoSeReintentaComoSiFueraCuota() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(HttpClientErrorException.class, () -> regla.ejecutarCuenta(cuenta));

        // Sin reintento y sin marcar como movido un correo que Gmail rechazó por permiso insuficiente.
        verify(mensajes, never()).save(any());
        servidor.verify();
    }

    @Test
    void noEjecutaCuentasSinPermisoAmpliado() throws Exception {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        verify(categorias, never()).findByFuente_IdFuente(anyString());
        servidor.verify();
    }

    @Test
    void mueveCorreosDeSpamCuandoLaCategoriaSpamEstaActiva() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("SPAM", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "in:spam%20-in:trash%20older_than:7d"))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"SPAM\",\"CATEGORY_PROMOTIONS\"]", "spam@desconocido.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(1, resultado.movidos());
        servidor.verify();
    }

    @Test
    void noMueveCorreosFueraDeInboxCuandoLaCategoriaNoEsSpam() throws Exception {
        when(categorias.findByFuente_IdFuente("cuenta-1"))
                .thenReturn(List.of(activa("CATEGORY_PROMOTIONS", Instant.now().minus(Duration.ofDays(30)))));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"SPAM\",\"CATEGORY_PROMOTIONS\"]", "spam@desconocido.com"),
                        MediaType.APPLICATION_JSON));

        SReglaPapeleraGmail.ResultadoEjecucion resultado = regla.ejecutarCuenta(cuenta);

        assertEquals(0, resultado.movidos());
        servidor.verify();
    }

    @Test
    void elSchedulerProcesaSoloCuentasConPermisoAmpliado() throws Exception {
        MFuente sinPermiso = new MFuente("cuenta-2", "CORREO", "otra@gmail.com", true, LocalDate.now());
        sinPermiso.setUsuario(cuenta.getUsuario());
        sinPermiso.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");
        when(fuentes.findAll()).thenReturn(List.of(sinPermiso));

        assertEquals(0, regla.ejecutarReglaPapelera());
        servidor.verify();
    }

    @Test
    void elSchedulerProcesaCuentasConPermisoPeroSinCategoriasActivas() throws Exception {
        when(fuentes.findAll()).thenReturn(List.of(cuenta));
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of());

        assertEquals(1, regla.ejecutarReglaPapelera());
        servidor.verify();
    }

    @Test
    void elBloqueoPorCuentaEvitaSolaparEjecuciones() {
        assertTrue(regla.intentarBloquear("cuenta-1"));
        assertFalse(regla.intentarBloquear("cuenta-1"));
        regla.liberar("cuenta-1");
        assertTrue(regla.intentarBloquear("cuenta-1"));
        regla.liberar("cuenta-1");
        assertTrue(regla.intentarBloquear("cuenta-2"));
    }

    private MGmailPapeleraCategoria activa(String categoria, Instant activadaEn) {
        MGmailPapeleraCategoria fila = new MGmailPapeleraCategoria("cat-" + categoria, cuenta, categoria);
        fila.setActiva(true);
        fila.setActivadaEn(activadaEn);
        return fila;
    }

    private static String lista(String... ids) {
        StringBuilder json = new StringBuilder("{\"messages\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) json.append(",");
            json.append("{\"id\":\"").append(ids[i]).append("\"}");
        }
        return json.append("]}").toString();
    }

    private static String metadata(String id, long internalDate, String labelIds, String from) {
        return "{\"id\":\"" + id + "\",\"internalDate\":\"" + internalDate + "\",\"labelIds\":" + labelIds
                + ",\"payload\":{\"headers\":[{\"name\":\"From\",\"value\":\"" + from + "\"}]}}";
    }

    /** Evita las esperas reales de backoff durante las pruebas. */
    private static final class ReglaSinEspera extends SReglaPapeleraGmail {
        private ReglaSinEspera(IGmailPapeleraConfiguracion configuraciones, IGmailPapeleraCategoria categorias,
                               IGmailPapeleraListaBlanca listaBlanca, IGmailMensajes mensajes,
                               SGmailMensajes servicioMensajes, SSuscripcionGmail suscripcionesGmail,
                               IFuentes fuentes, SRegistroPapeleraGmail registro, Executor executor,
                               RestClient.Builder constructor) {
            super(configuraciones, categorias, listaBlanca, mensajes, servicioMensajes, suscripcionesGmail,
                    fuentes, registro, executor, constructor);
        }

        @Override
        protected void dormir(long milisegundos) { }
    }
}
