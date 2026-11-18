package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Papelera de una cuenta Gmail: correos que están en el TRASH de Gmail, con el marcador local
 * del movimiento automático y el token de la página siguiente (sección 3 del contrato).
 */
public record GmailPapeleraDTO(
        @JsonProperty("cuenta_id") String cuentaId,
        @JsonProperty("correos") List<CorreoPapelera> correos,
        @JsonProperty("total") int total,
        @JsonProperty("siguiente_page_token") String siguientePageToken
) {
    /**
     * Correo en la papelera. `mensaje_id` ES el identificador de Gmail del mensaje; el marcador
     * de movimiento automático proviene de la fila local de `gmail_mensaje` cuando existe.
     */
    public record CorreoPapelera(
            @JsonProperty("mensaje_id") String mensajeId,
            @JsonProperty("remitente") String remitente,
            @JsonProperty("asunto") String asunto,
            @JsonProperty("fecha") String fecha,
            @JsonProperty("eliminacion_estimada_en") String eliminacionEstimadaEn,
            @JsonProperty("movido_automaticamente") boolean movidoAutomaticamente,
            @JsonProperty("motivo_automatico") String motivoAutomatico,
            @JsonProperty("categoria") String categoria
    ) { }
}
