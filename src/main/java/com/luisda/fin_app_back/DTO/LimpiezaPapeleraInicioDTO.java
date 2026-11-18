package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta `202` del `POST …/papelera/limpieza` (sección 6 del contrato). Desviación intencional
 * frente al patrón `importar-lote`: se añade `estado` para exponer el progreso desde el inicio.
 */
public record LimpiezaPapeleraInicioDTO(
        @JsonProperty("lote_id") String loteId,
        @JsonProperty("estado") String estado
) { }
