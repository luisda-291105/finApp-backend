package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GmailListaBlancaDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraConfiguracionDTO;
import com.luisda.fin_app_back.Model.MFuente;
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
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-33, RF-34, RF-36, RF-37, CD-04 — configuración por categoría (cinco categorías, defaults,
 * advertencia de UPDATES, validación de valores, `activada_en` al activar y permiso ampliado) y
 * CRUD de la lista blanca de remitentes.
 */
class SPapeleraGmailConfiguracionTest {

    private IGmailMensajes mensajes;
    private SGmailMensajes servicioMensajes;
    private IFuentes fuentes;
    private IGmailPapeleraConfiguracion configuraciones;
    private IGmailPapeleraCategoria categorias;
    private IGmailPapeleraListaBlanca listaBlanca;
    private SPapeleraGmail papelera;
    private MFuente cuenta;

    @BeforeEach
    void preparar() {
        mensajes = mock(IGmailMensajes.class);
        servicioMensajes = mock(SGmailMensajes.class);
        fuentes = mock(IFuentes.class);
        configuraciones = mock(IGmailPapeleraConfiguracion.class);
        categorias = mock(IGmailPapeleraCategoria.class);
        listaBlanca = mock(IGmailPapeleraListaBlanca.class);
        papelera = new SPapeleraGmail(mensajes, servicioMensajes, fuentes, configuraciones, categorias, listaBlanca,
                RestClient.builder());

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        cuenta = new MFuente("cuenta-1", "CORREO", "usuario@gmail.com", true, LocalDate.now());
        cuenta.setUsuario(usuario);
        cuenta.setAccessTokenCifrado("token-cifrado");
        cuenta.setExpiraEn(Instant.now().plusSeconds(3600));
        cuenta.setGmailScopeConcedido(MFuente.ALCANCE_GMAIL_MODIFICAR);

        when(configuraciones.save(any(MGmailPapeleraConfiguracion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(categorias.save(any(MGmailPapeleraCategoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(listaBlanca.save(any(MGmailPapeleraListaBlanca.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void laConfiguracionPorDefectoTraeLasCincoCategoriasInactivas() {
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.empty());
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of());

        GmailPapeleraConfiguracionDTO dto = papelera.configuracion(cuenta);

        assertEquals(List.of("CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL", "CATEGORY_FORUMS", "CATEGORY_UPDATES", "SPAM"),
                dto.categorias().stream().map(GmailPapeleraConfiguracionDTO.CategoriaConfigurada::categoria).toList());
        assertTrue(dto.categorias().stream().noneMatch(GmailPapeleraConfiguracionDTO.CategoriaConfigurada::activa));
        assertTrue(dto.categorias().stream().noneMatch(GmailPapeleraConfiguracionDTO.CategoriaConfigurada::advertenciaAceptada));
        assertFalse(dto.categorias().get(0).requiereAdvertencia());
        assertTrue(dto.categorias().get(3).requiereAdvertencia());
        assertEquals(7, dto.antiguedadMinimaDias());
        assertEquals(200, dto.limitePorEjecucion());
        assertTrue(dto.scopeAutorizado());
        // Creación perezosa de la configuración al leer.
        verify(configuraciones).save(any(MGmailPapeleraConfiguracion.class));
    }

    @Test
    void laConfiguracionReflejaLoGuardadoYElScopeDeLaCuenta() {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        config.setAntiguedadMinimaDias(14);
        config.setLimitePorEjecucion(50);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        MGmailPapeleraCategoria promo = new MGmailPapeleraCategoria("c1", cuenta, "CATEGORY_PROMOTIONS");
        promo.setActiva(true);
        promo.setActivadaEn(Instant.parse("2026-11-01T00:00:00Z"));
        MGmailPapeleraCategoria updates = new MGmailPapeleraCategoria("c2", cuenta, "CATEGORY_UPDATES");
        updates.setActiva(true);
        updates.setAdvertenciaAceptada(true);
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of(promo, updates));
        cuenta.setGmailScopeConcedido(null);

        GmailPapeleraConfiguracionDTO dto = papelera.configuracion(cuenta);

        assertEquals(14, dto.antiguedadMinimaDias());
        assertEquals(50, dto.limitePorEjecucion());
        assertFalse(dto.scopeAutorizado());
        assertTrue(dto.categorias().get(0).activa());
        assertTrue(dto.categorias().get(3).activa());
        assertTrue(dto.categorias().get(3).advertenciaAceptada());
        verify(configuraciones, never()).save(any(MGmailPapeleraConfiguracion.class));
    }

    @Test
    void activarUnaCategoriaRegistraElInstanteDeActivacion() {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of());

        papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_PROMOTIONS"), 7, 200, null));

        ArgumentCaptor<MGmailPapeleraCategoria> captor = ArgumentCaptor.forClass(MGmailPapeleraCategoria.class);
        verify(categorias, times(5)).save(captor.capture());
        MGmailPapeleraCategoria promo = fila(captor, "CATEGORY_PROMOTIONS");
        assertTrue(promo.isActiva());
        assertNotNull(promo.getActivadaEn());
        MGmailPapeleraCategoria spam = fila(captor, "SPAM");
        assertFalse(spam.isActiva());
        assertNull(spam.getActivadaEn());
        verify(configuraciones).save(config);
    }

    @Test
    void activarUpdatesSinAceptarLaAdvertenciaLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_UPDATES"), 7, 200, false)));

        verify(categorias, never()).save(any(MGmailPapeleraCategoria.class));
    }

    @Test
    void activarUpdatesConLaAdvertenciaAceptadaLaActiva() {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of());

        papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_UPDATES"), 7, 200, true));

        ArgumentCaptor<MGmailPapeleraCategoria> captor = ArgumentCaptor.forClass(MGmailPapeleraCategoria.class);
        verify(categorias, times(5)).save(captor.capture());
        MGmailPapeleraCategoria updates = fila(captor, "CATEGORY_UPDATES");
        assertTrue(updates.isActiva());
        assertTrue(updates.isAdvertenciaAceptada());
        assertNotNull(updates.getActivadaEn());
    }

    @Test
    void unaCategoriaDesconocidaLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_INVENTADA"), 7, 200, null)));
    }

    @Test
    void valoresInvalidosDeAntiguedadYLimiteLanzanExcepcion() {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));

        assertThrows(IllegalArgumentException.class, () -> papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of(), -1, 200, null)));
        assertThrows(IllegalArgumentException.class, () -> papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of(), 7, 0, null)));
    }

    @Test
    void activarSinPermisoAmpliadoLanzaRequiereScope() {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));

        assertThrows(SPapeleraGmail.RequiereScopeException.class, () -> papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_PROMOTIONS"), 7, 200, null)));

        verify(categorias, never()).save(any(MGmailPapeleraCategoria.class));
    }

    @Test
    void desactivarUnaCategoriaBorraElInstanteDeActivacion() {
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        MGmailPapeleraCategoria promo = new MGmailPapeleraCategoria("c1", cuenta, "CATEGORY_PROMOTIONS");
        promo.setActiva(true);
        promo.setActivadaEn(Instant.parse("2026-11-01T00:00:00Z"));
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of(promo));

        papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of(), 7, 200, null));

        assertFalse(promo.isActiva());
        assertNull(promo.getActivadaEn());
    }

    @Test
    void mantenerActivaUnaCategoriaConservaElInstanteDeActivacion() {
        Instant original = Instant.parse("2026-11-01T00:00:00Z");
        MGmailPapeleraConfiguracion config = new MGmailPapeleraConfiguracion(cuenta);
        when(configuraciones.findByFuente_IdFuente("cuenta-1")).thenReturn(Optional.of(config));
        MGmailPapeleraCategoria promo = new MGmailPapeleraCategoria("c1", cuenta, "CATEGORY_PROMOTIONS");
        promo.setActiva(true);
        promo.setActivadaEn(original);
        when(categorias.findByFuente_IdFuente("cuenta-1")).thenReturn(List.of(promo));

        papelera.actualizarConfiguracion(cuenta,
                new GmailPapeleraConfiguracionDTO.Solicitud(List.of("CATEGORY_PROMOTIONS"), 7, 200, null));

        assertTrue(promo.isActiva());
        assertEquals(original, promo.getActivadaEn());
    }

    @Test
    void listarLaListaBlancaDevuelveLosRemitentes() {
        MGmailPapeleraListaBlanca fila = new MGmailPapeleraListaBlanca("lb-1", cuenta, "banco@banco.com");
        when(listaBlanca.findByFuente_IdFuenteOrderByRemitenteAsc("cuenta-1")).thenReturn(List.of(fila));

        GmailListaBlancaDTO dto = papelera.listarListaBlanca(cuenta);

        assertEquals(1, dto.remitentes().size());
        assertEquals("lb-1", dto.remitentes().get(0).id());
        assertEquals("banco@banco.com", dto.remitentes().get(0).remitente());
    }

    @Test
    void agregarUnRemitenteValidoLoGuardaYDevuelveSuId() {
        when(listaBlanca.findByFuente_IdFuenteAndRemitente("cuenta-1", "banco@banco.com")).thenReturn(Optional.empty());

        GmailListaBlancaDTO.Remitente creado = papelera.agregarRemitente(cuenta, "  banco@banco.com  ");

        assertEquals("banco@banco.com", creado.remitente());
        assertNotNull(creado.id());
        verify(listaBlanca).save(any(MGmailPapeleraListaBlanca.class));
    }

    @Test
    void agregarUnRemitenteInvalidoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> papelera.agregarRemitente(cuenta, "   "));
        assertThrows(IllegalArgumentException.class, () -> papelera.agregarRemitente(cuenta, "sin-arroba"));
        assertThrows(IllegalArgumentException.class, () -> papelera.agregarRemitente(cuenta, null));

        verify(listaBlanca, never()).save(any(MGmailPapeleraListaBlanca.class));
    }

    @Test
    void agregarUnRemitenteYaExistenteNoLoDuplica() {
        MGmailPapeleraListaBlanca fila = new MGmailPapeleraListaBlanca("lb-1", cuenta, "banco@banco.com");
        when(listaBlanca.findByFuente_IdFuenteAndRemitente("cuenta-1", "banco@banco.com")).thenReturn(Optional.of(fila));

        GmailListaBlancaDTO.Remitente creado = papelera.agregarRemitente(cuenta, "banco@banco.com");

        assertEquals("lb-1", creado.id());
        verify(listaBlanca, never()).save(any(MGmailPapeleraListaBlanca.class));
    }

    @Test
    void eliminarUnRemitenteLoBorra() {
        MGmailPapeleraListaBlanca fila = new MGmailPapeleraListaBlanca("lb-1", cuenta, "banco@banco.com");
        when(listaBlanca.findByIdAndFuente_IdFuente("lb-1", "cuenta-1")).thenReturn(Optional.of(fila));

        papelera.eliminarRemitente(cuenta, "lb-1");

        verify(listaBlanca).delete(fila);
    }

    @Test
    void eliminarUnRemitenteAusenteLanzaNoEncontrado() {
        when(listaBlanca.findByIdAndFuente_IdFuente("lb-x", "cuenta-1")).thenReturn(Optional.empty());

        assertThrows(SPapeleraGmail.RemitenteNoEncontradoException.class, () -> papelera.eliminarRemitente(cuenta, "lb-x"));

        verify(listaBlanca, never()).delete(any(MGmailPapeleraListaBlanca.class));
    }

    private static MGmailPapeleraCategoria fila(ArgumentCaptor<MGmailPapeleraCategoria> captor, String categoria) {
        return captor.getAllValues().stream()
                .filter(fila -> categoria.equals(fila.getCategoria()))
                .findFirst()
                .orElseThrow();
    }
}
