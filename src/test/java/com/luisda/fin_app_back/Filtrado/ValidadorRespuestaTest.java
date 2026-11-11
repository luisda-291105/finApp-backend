package com.luisda.fin_app_back.Filtrado;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorRespuestaTest {
    private final ValidadorRespuesta validador = new ValidadorRespuesta(new ObjectMapper());

    @Test
    void extraeJsonDirecto() {
        assertEquals("GASTO", validador.extraerJson("{\"bolsillo\":\"GASTO\"}").get("bolsillo"));
    }

    @Test
    void extraeJsonDentroDeTexto() {
        assertEquals("INGRESO", validador.extraerJson("Resultado: {\"bolsillo\":\"INGRESO\"} fin").get("bolsillo"));
    }

    @Test
    void extraeJsonDeBloqueMarkdown() {
        assertEquals("OTROS", validador.extraerJson("```json\n{\"bolsillo\":\"OTROS\"}\n```").get("bolsillo"));
    }

    @Test
    void rechazaObjetoJsonAnidadoDentroDeTexto() {
        assertThrows(ValidadorRespuesta.RespuestaInvalidaException.class,
                () -> validador.extraerJson("Resultado: {\"bolsillo\":{\"tipo\":\"GASTO\"}}"));
    }

    @Test
    void rechazaRespuestaSinJson() {
        assertThrows(ValidadorRespuesta.RespuestaInvalidaException.class,
                () -> validador.extraerJson("No pude clasificar este mensaje."));
    }

    @Test
    void rechazaJsonValidoQueNoSeaObjeto() {
        assertThrows(ValidadorRespuesta.RespuestaInvalidaException.class,
                () -> validador.extraerJson("null"));
    }

    @Test
    void normalizaBolsilloInvalidoAMasOtros() {
        var resultado = validador.validarClasificacion(Map.of("bolsillo", "AHORRO", "monto", "1250.50", "confianza", 0.8));
        assertEquals(TipoBolsillo.OTROS, resultado.bolsillo());
        assertEquals(new BigDecimal("1250.50"), resultado.monto());
    }

    @Test
    void interpretaMontosCopConSeparadoresColombianosYEstadounidenses() {
        assertEquals(new BigDecimal("13000.00"), monto("13,000.00"));
        assertEquals(new BigDecimal("13000.00"), monto("13.000,00"));
        assertEquals(new BigDecimal("5200"), monto("5.200"));
        assertEquals(new BigDecimal("5200"), monto("5,200"));
        assertEquals(new BigDecimal("5.2"), monto("5,2"));
        assertEquals(new BigDecimal("5200"), monto("5200"));
    }

    @Test
    void limitaConfianzaAlIntervaloCeroAUno() {
        assertEquals(BigDecimal.ONE, validador.validarClasificacion(Map.of("confianza", 1.7)).confianza());
        assertEquals(BigDecimal.ZERO, validador.validarClasificacion(Map.of("confianza", -0.4)).confianza());
    }

    private BigDecimal monto(String valor) {
        return validador.validarClasificacion(Map.of("monto", valor)).monto();
    }
}
