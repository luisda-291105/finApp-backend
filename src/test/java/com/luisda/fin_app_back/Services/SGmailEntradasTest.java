package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailEntradaPendiente;
import com.luisda.fin_app_back.Repository.IGmailEntradaPendiente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SGmailEntradasTest {

    private final IGmailEntradaPendiente repositorio = mock(IGmailEntradaPendiente.class);
    private final SGmailEntradas cola = new SGmailEntradas(repositorio);

    private MFuente cuenta() {
        MFuente cuenta = new MFuente();
        cuenta.setIdFuente("cuenta-1");
        return cuenta;
    }

    @Test
    void encolaUnMensajePorCorreoYEvitaDuplicados() {
        when(repositorio.existsByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "msg-1")).thenReturn(true);
        cola.encolarMensaje(cuenta(), "msg-1");
        verify(repositorio, never()).save(any());

        when(repositorio.existsByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "msg-2")).thenReturn(false);
        cola.encolarMensaje(cuenta(), "msg-2");

        ArgumentCaptor<MGmailEntradaPendiente> captor = ArgumentCaptor.forClass(MGmailEntradaPendiente.class);
        verify(repositorio).save(captor.capture());
        MGmailEntradaPendiente guardada = captor.getValue();
        assertEquals("msg-2", guardada.getGoogleMessageId());
        assertEquals(MGmailEntradaPendiente.ESTADO_EN_ESPERA, guardada.getEstado());
        assertEquals(0, guardada.getIntentos());
        assertNotNull(guardada.getId());
        assertNotNull(guardada.getFechaCreacion());
    }

    @Test
    void ignoraMensajesVaciosYCuentasNulas() {
        cola.encolarMensaje(null, "msg");
        cola.encolarMensaje(cuenta(), "  ");
        cola.encolarMensaje(cuenta(), null);
        cola.encolarNotificacion(null, "historial");
        verify(repositorio, never()).save(any());
    }

    @Test
    void encolaLaNotificacionSinMensajeParaExpandirlaDespues() {
        cola.encolarNotificacion(cuenta(), "12345");

        ArgumentCaptor<MGmailEntradaPendiente> captor = ArgumentCaptor.forClass(MGmailEntradaPendiente.class);
        verify(repositorio).save(captor.capture());
        MGmailEntradaPendiente guardada = captor.getValue();
        assertNull(guardada.getGoogleMessageId());
        assertEquals("12345", guardada.getHistoryId());
        assertEquals(MGmailEntradaPendiente.ESTADO_EN_ESPERA, guardada.getEstado());
    }

    @Test
    void reintentaConBackoffHastaElLimiteYQuedaEnErrorTerminal() {
        MGmailEntradaPendiente entrada = new MGmailEntradaPendiente();
        entrada.setId("entrada-1");
        entrada.setEstado(MGmailEntradaPendiente.ESTADO_PROCESANDO);
        entrada.setIntentos(0);

        for (int intento = 1; intento < SGmailEntradas.MAX_INTENTOS; intento++) {
            cola.marcarError(entrada, "fallo " + intento);
            assertEquals(MGmailEntradaPendiente.ESTADO_EN_ESPERA, entrada.getEstado());
            assertNotNull(entrada.getFechaUltimoIntento());
        }
        cola.marcarError(entrada, "último fallo");

        assertEquals(MGmailEntradaPendiente.ESTADO_ERROR, entrada.getEstado());
        assertEquals(SGmailEntradas.MAX_INTENTOS, entrada.getIntentos());
        assertEquals("último fallo", entrada.getUltimoError());
        verify(repositorio, times(SGmailEntradas.MAX_INTENTOS)).save(entrada);
    }

    @Test
    void acortaElMensajeDeErrorGuardado() {
        MGmailEntradaPendiente entrada = new MGmailEntradaPendiente();
        entrada.setEstado(MGmailEntradaPendiente.ESTADO_PROCESANDO);
        cola.marcarError(entrada, "x".repeat(5000));
        assertEquals(2000, entrada.getUltimoError().length());
    }

    @Test
    void pendientesRespetaElBackoffDeLosReintentos() {
        MGmailEntradaPendiente nueva = new MGmailEntradaPendiente();
        nueva.setId("nueva");
        nueva.setEstado(MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        nueva.setIntentos(0);
        nueva.setFechaCreacion(Instant.now());

        MGmailEntradaPendiente enBackoff = new MGmailEntradaPendiente();
        enBackoff.setId("en-backoff");
        enBackoff.setEstado(MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        enBackoff.setIntentos(2);
        enBackoff.setFechaUltimoIntento(Instant.now());
        enBackoff.setFechaCreacion(Instant.now().minusSeconds(60));

        when(repositorio.findTop200ByEstadoOrderByFechaCreacionAsc(MGmailEntradaPendiente.ESTADO_EN_ESPERA))
                .thenReturn(List.of(enBackoff, nueva));

        assertEquals(List.of(nueva), cola.pendientes(10));

        enBackoff.setFechaUltimoIntento(Instant.now().minusSeconds(3600));
        assertEquals(List.of(enBackoff, nueva), cola.pendientes(10));
        assertEquals(List.of(enBackoff), cola.pendientes(1));
    }

    @Test
    void marcarProcesadoEliminaLaEntrada() {
        MGmailEntradaPendiente entrada = new MGmailEntradaPendiente();
        entrada.setId("entrada-1");
        cola.marcarProcesado(entrada);
        verify(repositorio).delete(entrada);
    }

    @Test
    void devuelveALaColaLasEntradasAtascadasEnProcesando() {
        MGmailEntradaPendiente atascada = new MGmailEntradaPendiente();
        atascada.setId("atascada");
        atascada.setEstado(MGmailEntradaPendiente.ESTADO_PROCESANDO);
        atascada.setFechaUltimoIntento(Instant.now().minusSeconds(11 * 60));
        MGmailEntradaPendiente enCurso = new MGmailEntradaPendiente();
        enCurso.setId("en-curso");
        enCurso.setEstado(MGmailEntradaPendiente.ESTADO_PROCESANDO);
        enCurso.setFechaUltimoIntento(Instant.now().minusSeconds(60));
        when(repositorio.findTop200ByEstadoOrderByFechaCreacionAsc(MGmailEntradaPendiente.ESTADO_PROCESANDO))
                .thenReturn(List.of(atascada, enCurso));

        cola.recuperarAtascadas();

        assertEquals(MGmailEntradaPendiente.ESTADO_EN_ESPERA, atascada.getEstado());
        assertEquals(MGmailEntradaPendiente.ESTADO_PROCESANDO, enCurso.getEstado());
        verify(repositorio).save(atascada);
        verify(repositorio, never()).save(enCurso);
    }

    @Test
    void estadoResumeLaColaYExponeElUltimoError() {
        when(repositorio.countByCuenta_IdFuenteAndEstado("cuenta-1", MGmailEntradaPendiente.ESTADO_EN_ESPERA))
                .thenReturn(3L);
        when(repositorio.countByCuenta_IdFuenteAndEstado("cuenta-1", MGmailEntradaPendiente.ESTADO_PROCESANDO))
                .thenReturn(1L);
        when(repositorio.countByCuenta_IdFuenteAndEstado("cuenta-1", MGmailEntradaPendiente.ESTADO_ERROR))
                .thenReturn(2L);
        MGmailEntradaPendiente conError = new MGmailEntradaPendiente();
        conError.setUltimoError("token expirado");
        conError.setFechaUltimoIntento(Instant.now());
        when(repositorio.findTop5ByCuenta_IdFuenteAndEstadoOrderByFechaUltimoIntentoDesc(
                "cuenta-1", MGmailEntradaPendiente.ESTADO_ERROR)).thenReturn(List.of(conError));

        SGmailEntradas.EstadoCola estado = cola.estado("cuenta-1");

        assertEquals(3, estado.enEspera());
        assertEquals(1, estado.procesando());
        assertEquals(2, estado.errores());
        assertEquals("token expirado", estado.ultimoError());
        assertNotNull(estado.ultimoIntento());
        assertEquals(true, estado.ocupada());
    }

    @Test
    void estadoSinCorreosNoConsultaErroresYQuedaLibre() {
        SGmailEntradas.EstadoCola estado = cola.estado("cuenta-1");

        assertEquals(0, estado.enEspera());
        assertEquals(0, estado.procesando());
        assertEquals(0, estado.errores());
        assertNull(estado.ultimoError());
        assertEquals(false, estado.ocupada());
        verify(repositorio, never()).findTop5ByCuenta_IdFuenteAndEstadoOrderByFechaUltimoIntentoDesc(any(), any());
    }
}
