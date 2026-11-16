package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * @uso               Datos normalizados de la TRM (USD/COP) vigente que consume el dashboard
 * @capa              DTO
 * @responsabilidades Exponer la TRM con contrato estable e independiente de la fuente externa
 * @datos             Valor en COP por 1 USD, fecha de vigencia, variación porcentual e histórico reciente
 * @dependencias      Ninguno
 * @usadoPor          SIndicadorTrm, CIndicadores
 */
public record IndicadorTrmDTO(BigDecimal valor, String fechaVigencia, String monedaOrigen, String monedaDestino,
                              BigDecimal variacion, String fuente, List<PuntoHistoricoDTO> historico) {

    public record PuntoHistoricoDTO(String fecha, BigDecimal valor) {}

    /** Valor no disponible: el frontend muestra «Sin datos disponibles». */
    public static IndicadorTrmDTO sinDatos(String fuente) {
        return new IndicadorTrmDTO(null, null, "USD", "COP", null, fuente, List.of());
    }
}
