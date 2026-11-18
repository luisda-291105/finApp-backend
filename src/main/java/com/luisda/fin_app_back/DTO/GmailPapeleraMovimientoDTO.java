package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resultado de mover a la papelera o restaurar un correo (secciones 1 y 2 del contrato).
 * Al mover se incluye `movido_automaticamente` (siempre `false` en la acción manual); al
 * restaurar ese campo se omite porque no aplica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GmailPapeleraMovimientoDTO(
        @JsonProperty("mensaje_id") String mensajeId,
        @JsonProperty("estado") String estado,
        @JsonProperty("movido_automaticamente") Boolean movidoAutomaticamente
) { }
