package com.luisda.fin_app_back.Services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserMovimientoBancolombiaTest {
    private final ParserMovimientoBancolombia parser = new ParserMovimientoBancolombia();

    @Test
    void extraeCompraSoloCuandoElCuerpoIdentificaBancolombiaYElMonto() {
        var resultado = parser.extraer("Alerta", "Bancolombia: Realizaste una compra por valor de $ 125.000.");

        assertTrue(resultado.isPresent());
        assertEquals("GASTO", resultado.get().tipo());
        assertEquals(new BigDecimal("125000.00"), resultado.get().monto());
    }

    @Test
    void extraeAbonoComoIngresoConSeparadorDecimalColombiano() {
        var resultado = parser.extraer("Notificación", "Bancolombia: Recibiste un abono por $ 2.450.500,50.");

        assertTrue(resultado.isPresent());
        assertEquals("INGRESO", resultado.get().tipo());
        assertEquals(new BigDecimal("2450500.50"), resultado.get().monto());
    }

    @Test
    void noClasificaPorRemitenteNiPorUnAsuntoSinContenidoFinanciero() {
        assertTrue(parser.extraer("Bancolombia compra por $ 500", "Tu estado de cuenta está disponible.").isEmpty());
        assertTrue(parser.extraer("Compra por $ 500", "Realizaste una compra por $ 500.").isEmpty());
    }

    @Test
    void noClasificaCorreosSinOperacionUnivocaOMontoEtiquetado() {
        assertTrue(parser.extraer("Alerta", "Bancolombia: Recibiste un abono y realizaste una compra por $ 500.").isEmpty());
        assertTrue(parser.extraer("Alerta", "Bancolombia: Realizaste una compra en la tienda.").isEmpty());
    }
}
