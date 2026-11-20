package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo del `POST …/papelera/limpieza` (sección 6 del contrato): la vista previa referenciada, el
 * conteo que el usuario confirmó y la confirmación explícita. `total_estimado` y `confirmado` son
 * envoltorios para poder distinguir «ausente» de `0`/`false` y responder `400`.
 */
public record LimpiezaPapeleraDTO(
        @JsonProperty("vista_previa_id") String vistaPreviaId,
        @JsonProperty("total_estimado") Integer totalEstimado,
        @JsonProperty("confirmado") Boolean confirmado
) { }
