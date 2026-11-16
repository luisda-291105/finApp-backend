package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.IndicadorTrmDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SIndicadorTrmTest {
    private final ObjectMapper json = new ObjectMapper();

    private IndicadorTrmDTO mapear(String cuerpo) throws Exception {
        return SIndicadorTrm.mapear(json.readTree(cuerpo), SIndicadorTrm.FUENTE);
    }

    @Test
    void mapeaTrmVigenteConVariacionEHistorico() throws Exception {
        IndicadorTrmDTO dato = mapear("""
            [
              {"valor":"3895.42","vigenciadesde":"2026-11-15T00:00:00.000","vigenciahasta":null},
              {"valor":"3882.55","vigenciadesde":"2026-11-14T00:00:00.000","vigenciahasta":"2026-11-15T00:00:00.000"},
              {"valor":"3870.10","vigenciadesde":"2026-11-13T00:00:00.000","vigenciahasta":"2026-11-14T00:00:00.000"}
            ]""");
        assertEquals(new java.math.BigDecimal("3895.42"), dato.valor());
        assertEquals("2026-11-15", dato.fechaVigencia());
        assertEquals("USD", dato.monedaOrigen());
        assertEquals("COP", dato.monedaDestino());
        assertEquals(new java.math.BigDecimal("0.33"), dato.variacion());
        assertEquals(SIndicadorTrm.FUENTE, dato.fuente());
        assertEquals(3, dato.historico().size());
        assertEquals("2026-11-13", dato.historico().get(2).fecha());
        assertEquals(new java.math.BigDecimal("3870.10"), dato.historico().get(2).valor());
    }

    @Test
    void respuestasVaciasODanadasDevuelvenValorNulo() throws Exception {
        assertTrue(mapear("[]").valor() == null);
        assertTrue(mapear("[{\"valor\":\"\",\"vigenciadesde\":\"2026-11-15\"}]").valor() == null);
        assertTrue(mapear("[{\"valor\":\"-3\",\"vigenciadesde\":\"2026-11-15\"}]").valor() == null);
        assertNull(SIndicadorTrm.mapear(null, SIndicadorTrm.FUENTE).valor());
        assertNull(mapear("null").valor());
    }

    @Test
    void unicoPuntoValidoNoCalculaVariacion() throws Exception {
        IndicadorTrmDTO dato = mapear("[{\"valor\":\"3895.42\",\"vigenciadesde\":\"2026-11-15T00:00:00.000\"}]");
        assertEquals(new java.math.BigDecimal("3895.42"), dato.valor());
        assertNull(dato.variacion());
        assertEquals(1, dato.historico().size());
    }

    @Test
    void filasInvalidasSeOmitenDelHistorico() throws Exception {
        IndicadorTrmDTO dato = mapear("""
            [
              {"valor":"3895.42","vigenciadesde":"2026-11-15T00:00:00.000"},
              {"valor":"abc","vigenciadesde":"2026-11-14T00:00:00.000"},
              {"valor":"0","vigenciadesde":"2026-11-13T00:00:00.000"},
              {"valor":"3882.55","vigenciadesde":"2026-11-12T00:00:00.000"}
            ]""");
        assertEquals(2, dato.historico().size());
        assertEquals("2026-11-12", dato.historico().get(1).fecha());
        assertEquals(new java.math.BigDecimal("0.33"), dato.variacion());
    }
}
