package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.ImportacionLoteGmailDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
import com.luisda.fin_app_back.Filtrado.TipoBolsillo;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SImportacionLoteGmailTest {
    private static final String USUARIO = "usuario-1";
    private static final String CUENTA = "cuenta-1";
    private SMovimientoContable movimientos;
    private SClasificadorMovimiento clasificador;
    private SImportacionLoteGmail servicio;

    @BeforeEach
    void preparar() {
        movimientos = mock(SMovimientoContable.class);
        clasificador = mock(SClasificadorMovimiento.class);
        servicio = new SImportacionLoteGmail(clasificador, movimientos,
                new ParserMovimientoBancolombia(), Runnable::run);
        when(movimientos.correosImportados(anyString(), anyList())).thenReturn(List.of());
    }

    @Test
    void aceptaRemitenteExactoYExtraeGastoDeCuerpoSinMarca() {
        var entrada = mensaje("msg-1", SImportacionLoteGmail.REMITENTE_OFICIAL,
                "pagaste $13,000.00 por codigo QR desde tu cuenta *5201 a la llave @fritoselflaco el 10/11/2026");
        when(clasificador.clasificar(USUARIO, SImportacionLoteGmail.REMITENTE_OFICIAL,
                "Alerta\n" + entrada.contenido())).thenReturn(clasificacion(false));
        when(movimientos.crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(Optional.of(dto(EstadoRevisionMovimiento.CONFIRMADO)));

        var inicio = servicio.iniciar(USUARIO, CUENTA, SImportacionLoteGmail.REMITENTE_OFICIAL, List.of(entrada));
        var estado = servicio.consultar(USUARIO, CUENTA, inicio.loteId()).orElseThrow();

        assertEquals(1, estado.importados());
        assertEquals("IMPORTADO", estado.mensajes().getFirst().estado());
        verify(movimientos).crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
    }

    @Test
    void rechazaRemitenteDeSuscripcionDistintoDelOficial() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.iniciar(USUARIO, CUENTA, "info@mercadeo.correobancolombia.com", List.of()));
        verify(movimientos, never()).crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
    }

    @Test
    void omiteDuplicadoSinInvocarClasificador() {
        when(movimientos.correosImportados(USUARIO, List.of(CUENTA + ":msg-dup")))
                .thenReturn(List.of(CUENTA + ":msg-dup"));

        var inicio = servicio.iniciar(USUARIO, CUENTA, SImportacionLoteGmail.REMITENTE_OFICIAL,
                List.of(mensaje("msg-dup", SImportacionLoteGmail.REMITENTE_OFICIAL, "pagaste $1,000.00")));
        var estado = servicio.consultar(USUARIO, CUENTA, inicio.loteId()).orElseThrow();

        assertEquals(1, estado.duplicados());
        assertEquals("DUPLICADO", estado.mensajes().getFirst().estado());
        verify(clasificador, never()).clasificar(anyString(), anyString(), anyString());
    }

    @Test
    void clasificacionNoConfiableSeRegistraComoPorRevisar() {
        var entrada = mensaje("msg-review", SImportacionLoteGmail.REMITENTE_OFICIAL, "pagaste $2,000.00");
        when(clasificador.clasificar(anyString(), anyString(), anyString())).thenReturn(clasificacion(true));
        when(movimientos.crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(Optional.of(dto(EstadoRevisionMovimiento.POR_REVISAR)));

        var inicio = servicio.iniciar(USUARIO, CUENTA, SImportacionLoteGmail.REMITENTE_OFICIAL, List.of(entrada));
        var estado = servicio.consultar(USUARIO, CUENTA, inicio.loteId()).orElseThrow();

        assertEquals(1, estado.porRevisar());
        assertEquals("POR_REVISAR", estado.mensajes().getFirst().estado());
    }

    @Test
    void errorEnUnMensajeNoDetieneElRestoDelLote() {
        var primero = mensaje("msg-error", SImportacionLoteGmail.REMITENTE_OFICIAL, "pagaste $2,000.00");
        var segundo = mensaje("msg-ok", SImportacionLoteGmail.REMITENTE_OFICIAL, "pagaste $3,000.00");
        when(clasificador.clasificar(anyString(), anyString(), anyString())).thenReturn(clasificacion(false));
        when(movimientos.crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Fallo de persistencia"))
                .thenReturn(Optional.of(dto(EstadoRevisionMovimiento.CONFIRMADO)));

        var inicio = servicio.iniciar(USUARIO, CUENTA, SImportacionLoteGmail.REMITENTE_OFICIAL, List.of(primero, segundo));
        var estado = servicio.consultar(USUARIO, CUENTA, inicio.loteId()).orElseThrow();

        assertEquals(1, estado.errores());
        assertEquals(1, estado.importados());
        assertEquals(2, estado.completados());
    }

    @Test
    void pasaElMontoDe13000ConDosDecimalesAlServicioContable() {
        var entrada = mensaje("msg-monto", SImportacionLoteGmail.REMITENTE_OFICIAL,
                "pagaste $13,000.00 por codigo QR desde tu cuenta *5201 a la llave @fritoselflaco el 10/11/2026");
        when(clasificador.clasificar(anyString(), anyString(), anyString())).thenReturn(clasificacion(false));
        when(movimientos.crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(Optional.of(dto(EstadoRevisionMovimiento.CONFIRMADO)));

        servicio.iniciar(USUARIO, CUENTA, SImportacionLoteGmail.REMITENTE_OFICIAL, List.of(entrada));

        ArgumentCaptor<BigDecimal> monto = ArgumentCaptor.forClass(BigDecimal.class);
        verify(movimientos).crearAutomaticamenteDesdeGmail(anyString(), anyString(), any(), monto.capture(), anyString(),
                anyString(), anyString(), anyString(), any());
        assertEquals(new BigDecimal("13000.00"), monto.getValue());
    }

    private static ImportacionLoteGmailDTO.Mensaje mensaje(String id, String remitente, String contenido) {
        return new ImportacionLoteGmailDTO.Mensaje(id, remitente, "Alerta", LocalDate.of(2026, 11, 10), contenido, null);
    }

    private static ResultadoClasificacion clasificacion(boolean revisar) {
        return new ResultadoClasificacion(null, revisar ? TipoBolsillo.OTROS : TipoBolsillo.GASTO,
                null, null, null, revisar ? BigDecimal.ZERO : BigDecimal.ONE, revisar);
    }

    private static MovimientoContableDTO dto(EstadoRevisionMovimiento estado) {
        return new MovimientoContableDTO("mov-1", "bolsillo-1", "Compras", LocalDate.of(2026, 11, 10),
                new BigDecimal("13000.00"), "GASTO", "Compras", "Alerta", "GMAIL", "cuenta-1:msg", "alertas",
                estado);
    }
}
