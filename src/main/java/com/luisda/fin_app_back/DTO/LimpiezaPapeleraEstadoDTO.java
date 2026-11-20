package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del `GET …/papelera/limpieza/{loteId}` (sección 6 del contrato): el estado del lote y
 * los conteos de correos movidos, omitidos por el límite por ejecución y fallidos (reintentables).
 * `limite_ejecucion_alcanzado` refleja si quedó remanente (RF-40).
 */
public record LimpiezaPapeleraEstadoDTO(
        @JsonProperty("lote_id") String loteId,
        @JsonProperty("estado") String estado,
        @JsonProperty("total") int total,
        @JsonProperty("movidos") int movidos,
        @JsonProperty("omitidos") int omitidos,
        @JsonProperty("fallidos") int fallidos,
        @JsonProperty("limite_ejecucion_alcanzado") boolean limiteEjecucionAlcanzado
) { }
