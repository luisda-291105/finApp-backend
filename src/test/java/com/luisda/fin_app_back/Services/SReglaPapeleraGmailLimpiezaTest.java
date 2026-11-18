package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.LimpiezaPapeleraDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraEstadoDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraInicioDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraRespuestaDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MGmailPapeleraConfiguracion;
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
 * RF-35..RF-39, CD-04 — vista previa y limpieza explícita: la vista previa aplica TODAS las
 * exclusiones y la lista blanca y agrupa remitentes por categoría; la limpieza exige un
 * `vista_previa_id` válido, recalcula el conteo (`conteo_cambiado`), impone «una limpieza por
 * cuenta» (`limpieza_en_curso`), respeta el límite por ejecución con remanente y deja los movidos
 * en la papelera ante un fallo parcial sin duplicar movimientos.
 */
class SReglaPapeleraGmailLimpiezaTest {

    private IGmailPapeleraConfiguracion configuraciones;
    private IGmailPapeleraCategoria categorias;
    private IGmailPapeleraListaBlanca listaBlanca;
    private IGmailMensajes mensajes;
    private SGmailMensajes servicioMensajes;
    private SSuscripcionGmail suscripcionesGmail;
    private IFuentes fuentes;
    private SRegistroPapeleraGmail registro;
    private ExecutorPendiente executor;
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
        registro = new SRegistroPapeleraGmail();
        executor = new ExecutorPendiente();
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        regla = new ReglaLimpiezaSinEspera(configuraciones, categorias, listaBlanca, mensajes, servicioMensajes,
                suscripcionesGmail, fuentes, registro, executor, constructor);

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
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.empty());
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of());
    }

    @Test
    void laVistaPreviaDevuelveElegiblesRemitentesAgrupadosYExcluidosPorMotivo() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "category:CATEGORY_PROMOTIONS%20in:inbox%20-in:trash%20older_than:7d"))
                .andRespond(withSuccess(lista("m1", "m2", "m3", "m4"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m3?")))
                .andRespond(withSuccess(metadata("m3", fecha, "[\"INBOX\",\"STARRED\",\"CATEGORY_PROMOTIONS\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m4?")))
                .andRespond(withSuccess(metadata("m4", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "nu@nu.com.co"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages?"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "category:CATEGORY_SOCIAL%20in:inbox%20-in:trash%20older_than:7d"))
                .andRespond(withSuccess(lista("m5"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m5?")))
                .andRespond(withSuccess(metadata("m5", fecha, "[\"INBOX\",\"CATEGORY_SOCIAL\"]", "notificaciones@red.com"),
                        MediaType.APPLICATION_JSON));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL"), 7));

        assertEquals(3, vista.totalEstimado());
        assertEquals(2, vista.porCategoria().size());
        assertEquals("CATEGORY_PROMOTIONS", vista.porCategoria().get(0).categoria());
        assertEquals(2, vista.porCategoria().get(0).cantidad());
        assertEquals(1, vista.porCategoria().get(0).remitentes().size());
        assertEquals("promo@tienda.com", vista.porCategoria().get(0).remitentes().get(0).remitente());
        assertEquals(2, vista.porCategoria().get(0).remitentes().get(0).cantidad());
        assertEquals("CATEGORY_SOCIAL", vista.porCategoria().get(1).categoria());
        assertEquals("notificaciones@red.com", vista.porCategoria().get(1).remitentes().get(0).remitente());
        assertEquals(1, vista.excluidosPorMotivo().stream()
                .filter(e -> e.motivo().equals("STARRED")).mapToInt(e -> e.cantidad()).sum());
        assertEquals(1, vista.excluidosPorMotivo().stream()
                .filter(e -> e.motivo().equals("REMITENTE_PRIORITARIO")).mapToInt(e -> e.cantidad()).sum());
        assertTrue(registro.obtenerVistaPrevia(vista.vistaPreviaId(), "usuario-1", "cuenta-1").isPresent());
        servidor.verify();
    }

    @Test
    void laVistaPreviaExcluyeCorreosConEtiquetaPersonalizadaListaBlancaYMovimientoRegistrado() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2", "m3"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\",\"Label_9\"]", "promo@tienda.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "blanco@banco.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m3?")))
                .andRespond(withSuccess(metadata("m3", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "mov@tienda.com"),
                        MediaType.APPLICATION_JSON));
        when(listaBlanca.findByFuente_IdFuenteOrderByRemitenteAsc("cuenta-1"))
                .thenReturn(List.of(new com.luisda.fin_app_back.Model.MGmailPapeleraListaBlanca(
                        "lb-1", cuenta, "blanco@banco.com")));
        MGmailMensaje registrado = new MGmailMensaje("fila-1", cuenta, "m3", Instant.ofEpochMilli(fecha));
        registrado.setMovimientoAutomaticoEstado("REGISTRADO");
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m3")).thenReturn(Optional.of(registrado));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));

        assertEquals(0, vista.totalEstimado());
        assertTrue(vista.excluidosPorMotivo().stream().anyMatch(e -> e.motivo().equals("ETIQUETA_PERSONALIZADA")));
        assertTrue(vista.excluidosPorMotivo().stream().anyMatch(e -> e.motivo().equals("LISTA_BLANCA")));
        assertTrue(vista.excluidosPorMotivo().stream().anyMatch(e -> e.motivo().equals("MOVIMIENTO_REGISTRADO")));
        servidor.verify();
    }

    @Test
    void laVistaPreviaRechazaCategoriasDesconocidas() {
        assertThrows(IllegalArgumentException.class, () -> regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_INVENTADA"), 7)));
    }

    @Test
    void laLimpiezaConVistaPreviaInexistenteRespondeError() {
        assertThrows(SReglaPapeleraGmail.VistaPreviaNoEncontradaException.class,
                () -> regla.iniciarLimpieza(cuenta, new LimpiezaPapeleraDTO("inexistente", 3, true)));
    }

    @Test
    void laLimpiezaConConteoDistintoAlConfirmadoReportaElNuevoConteo() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        // Vista previa: dos elegibles.
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "a@b.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "c@d.com"),
                        MediaType.APPLICATION_JSON));
        // Limpieza: solo uno sigue elegible.
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "a@b.com"),
                        MediaType.APPLICATION_JSON));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));
        assertEquals(2, vista.totalEstimado());

        SReglaPapeleraGmail.ConteoCambiadoException error = assertThrows(
                SReglaPapeleraGmail.ConteoCambiadoException.class,
                () -> regla.iniciarLimpieza(cuenta, new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 2, true)));

        assertEquals(1, error.getNuevoConteo());
        verify(mensajes, never()).save(any(MGmailMensaje.class));
        assertTrue(registro.intentarBloquearLimpieza("cuenta-1"), "el bloqueo debe liberarse tras el 409");
        servidor.verify();
    }

    @Test
    void laLimpiezaMueveYMarcaCadaCorreoConMotivoLimpieza() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        esperarVistaYLimpiezaDosCorreos(fecha);
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andExpect(jsonPath("$.addLabelIds", contains("TRASH")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m2")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));
        LimpiezaPapeleraInicioDTO inicio = regla.iniciarLimpieza(cuenta,
                new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 2, true));
        assertEquals("EN_PROCESO", inicio.estado());
        executor.correrTodas();

        ArgumentCaptor<MGmailMensaje> captor = ArgumentCaptor.forClass(MGmailMensaje.class);
        verify(mensajes, org.mockito.Mockito.times(2)).save(captor.capture());
        for (MGmailMensaje guardado : captor.getAllValues()) {
            assertEquals("EN_PAPELERA", guardado.getPapeleraEstado());
            assertTrue(guardado.isPapeleraMovidoAutomaticamente());
            assertEquals("LIMPIEZA", guardado.getPapeleraMotivo());
            assertEquals("CATEGORY_PROMOTIONS", guardado.getPapeleraCategoria());
        }
        LimpiezaPapeleraEstadoDTO estado = regla.consultarLimpieza(cuenta, inicio.loteId()).orElseThrow();
        assertEquals("COMPLETADO", estado.estado());
        assertEquals(2, estado.total());
        assertEquals(2, estado.movidos());
        assertEquals(0, estado.omitidos());
        assertEquals(0, estado.fallidos());
        servidor.verify();
    }

    @Test
    void unaSegundaLimpiezaSimultaneaReportaLimpiezaEnCurso() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        esperarUnaListaYUnaMeta(fecha, "m1", "a@b.com");
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));
        regla.iniciarLimpieza(cuenta, new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 1, true));

        assertThrows(SReglaPapeleraGmail.LimpiezaEnCursoException.class,
                () -> regla.iniciarLimpieza(cuenta, new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 1, true)));

        executor.correrTodas();
        assertTrue(registro.intentarBloquearLimpieza("cuenta-1"), "el bloqueo debe liberarse al terminar");
        servidor.verify();
    }

    @Test
    void respetaElLimitePorEjecucionYReflejaElRemanente() throws Exception {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        config.setLimitePorEjecucion(1);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        esperarVistaYLimpiezaDosCorreos(fecha);
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));
        LimpiezaPapeleraInicioDTO inicio = regla.iniciarLimpieza(cuenta,
                new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 2, true));
        executor.correrTodas();

        LimpiezaPapeleraEstadoDTO estado = regla.consultarLimpieza(cuenta, inicio.loteId()).orElseThrow();
        assertEquals("COMPLETADO", estado.estado());
        assertEquals(2, estado.total());
        assertEquals(1, estado.movidos());
        assertEquals(1, estado.omitidos());
        assertTrue(estado.limiteEjecucionAlcanzado());
        servidor.verify();
    }

    @Test
    void unFalloParcialDejaLosMovidosEnPapeleraYLosFallidosReintentables() throws Exception {
        long fecha = Instant.now().minus(Duration.ofDays(10)).toEpochMilli();
        esperarVistaYLimpiezaDosCorreos(fecha);
        servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids", contains("m1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        for (int intento = 0; intento < 6; intento++) {
            servidor.expect(requestTo(containsString("/messages/batchModify"))).andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        VistaPreviaPapeleraRespuestaDTO vista = regla.vistaPrevia(cuenta,
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7));
        LimpiezaPapeleraInicioDTO inicio = regla.iniciarLimpieza(cuenta,
                new LimpiezaPapeleraDTO(vista.vistaPreviaId(), 2, true));
        executor.correrTodas();

        LimpiezaPapeleraEstadoDTO estado = regla.consultarLimpieza(cuenta, inicio.loteId()).orElseThrow();
        assertEquals("COMPLETADO", estado.estado());
        assertEquals(2, estado.total());
        assertEquals(1, estado.movidos());
        assertEquals(1, estado.fallidos());
        // Solo el correo movido se marca localmente; el fallido queda reintentable sin duplicar.
        ArgumentCaptor<MGmailMensaje> captor = ArgumentCaptor.forClass(MGmailMensaje.class);
        verify(mensajes).save(captor.capture());
        assertEquals("m1", captor.getValue().getGoogleMessageId());
        servidor.verify();
    }

    @Test
    void elSeguimientoDeUnLoteDesconocidoNoDevuelveEstado() {
        assertTrue(regla.consultarLimpieza(cuenta, "inexistente").isEmpty());
    }

    /** Prepara la vista previa (m1, m2) y la re-evaluación en vivo de la limpieza para dos correos. */
    private void esperarVistaYLimpiezaDosCorreos(long fecha) {
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "a@b.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "c@d.com"),
                        MediaType.APPLICATION_JSON));
        // Re-evaluación de la limpieza.
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista("m1", "m2"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m1?")))
                .andRespond(withSuccess(metadata("m1", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "a@b.com"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/m2?")))
                .andRespond(withSuccess(metadata("m2", fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", "c@d.com"),
                        MediaType.APPLICATION_JSON));
    }

    /** Prepara la vista previa y la re-evaluación de la limpieza para un único correo. */
    private void esperarUnaListaYUnaMeta(long fecha, String id, String remitente) {
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista(id), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/" + id + "?")))
                .andRespond(withSuccess(metadata(id, fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", remitente),
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages?")))
                .andRespond(withSuccess(lista(id), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/messages/" + id + "?")))
                .andRespond(withSuccess(metadata(id, fecha, "[\"INBOX\",\"CATEGORY_PROMOTIONS\"]", remitente),
                        MediaType.APPLICATION_JSON));
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

    /** Ejecuta las tareas en línea cuando la prueba lo pide, para inspeccionar el bloqueo. */
    private static final class ExecutorPendiente implements Executor {
        private final List<Runnable> tareas = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tareas.add(command);
        }

        void correrTodas() {
            List<Runnable> copia = new ArrayList<>(tareas);
            tareas.clear();
            copia.forEach(Runnable::run);
        }
    }

    /** Evita esperas reales y divide los lotes de uno en uno para probar el fallo parcial. */
    private static final class ReglaLimpiezaSinEspera extends SReglaPapeleraGmail {
        private ReglaLimpiezaSinEspera(IGmailPapeleraConfiguracion configuraciones,
                                       IGmailPapeleraCategoria categorias, IGmailPapeleraListaBlanca listaBlanca,
                                       IGmailMensajes mensajes, SGmailMensajes servicioMensajes,
                                       SSuscripcionGmail suscripcionesGmail, IFuentes fuentes,
                                       SRegistroPapeleraGmail registro, Executor executor,
                                       RestClient.Builder constructor) {
            super(configuraciones, categorias, listaBlanca, mensajes, servicioMensajes, suscripcionesGmail,
                    fuentes, registro, executor, constructor);
        }

        @Override
        protected void dormir(long milisegundos) { }

        @Override
        protected int tamanoLote() { return 1; }
    }
}
