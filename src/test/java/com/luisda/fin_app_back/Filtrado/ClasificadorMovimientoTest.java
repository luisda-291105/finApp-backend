package com.luisda.fin_app_back.Filtrado;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClasificadorMovimientoTest {
    @Mock
    private ClienteModeloFiltro clienteModelo;

    private ClasificadorMovimiento crearClasificador() {
        return new ClasificadorMovimiento(clienteModelo, new ValidadorRespuesta(new ObjectMapper()));
    }

    @Test
    void reglaCoincidenteNoLlamaAlModelo() {
        var reglas = List.of(new ReglaFiltro(TipoBolsillo.GASTO, List.of("compra"), List.of("banco.ejemplo")));

        var resultado = crearClasificador().clasificar("", "Compra en el comercio", reglas);

        assertEquals(TipoBolsillo.GASTO, resultado.bolsillo());
        assertEquals(BigDecimal.ONE, resultado.confianza());
        assertFalse(resultado.revisar());
        verify(clienteModelo, never()).clasificar(anyString());
    }

    @Test
    void reglaDeRemitenteCoincideConElDominioCompleto() {
        var reglas = List.of(new ReglaFiltro(TipoBolsillo.GASTO, List.of(),
                List.of("alertasynotificacionesbancolombia.com")));

        var resultado = crearClasificador().clasificar(
                "Alertas Bancolombia <alertasynotificacionesbancolombia.com>", "Compra aprobada", reglas);

        assertEquals(TipoBolsillo.GASTO, resultado.bolsillo());
        assertFalse(resultado.revisar());
        verify(clienteModelo, never()).clasificar(anyString());
    }

    @Test
    void reglaDeRemitenteNoCoincideConUnFragmentoDelDominio() {
        when(clienteModelo.clasificar(anyString())).thenReturn("{\"bolsillo\":\"INGRESO\",\"confianza\":0.9}");
        var reglas = List.of(new ReglaFiltro(TipoBolsillo.GASTO, List.of(), List.of("bancolombia.com")));

        var resultado = crearClasificador().clasificar("alertasynotificacionesbancolombia.com", "Mensaje", reglas);

        assertEquals(TipoBolsillo.INGRESO, resultado.bolsillo());
        verify(clienteModelo).clasificar(anyString());
    }

    @Test
    void bajaConfianzaCaeEnOtrosYRequiereRevision() throws Exception {
        when(clienteModelo.clasificar(anyString())).thenReturn("{\"bolsillo\":\"GASTO\",\"confianza\":0.6}");

        var resultado = crearClasificador().clasificar("remitente", "mensaje", List.of());

        assertEquals(TipoBolsillo.OTROS, resultado.bolsillo());
        assertTrue(resultado.revisar());
    }

    @Test
    void errorDelClienteRegistraWarnYCaeEnOtrosConRevision() throws Exception {
        when(clienteModelo.clasificar(anyString())).thenThrow(new IllegalStateException("modelo no disponible"));
        Logger logger = (Logger) LoggerFactory.getLogger(ClasificadorMovimiento.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            var resultado = crearClasificador().clasificar("remitente", "mensaje", List.of());

            assertEquals(TipoBolsillo.OTROS, resultado.bolsillo());
            assertTrue(resultado.revisar());
            assertTrue(appender.list.stream().anyMatch(evento -> evento.getLevel() == Level.WARN
                    && evento.getFormattedMessage().contains("IllegalStateException")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
