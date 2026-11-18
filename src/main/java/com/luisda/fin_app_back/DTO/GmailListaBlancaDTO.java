package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Lista blanca de remitentes de una cuenta Gmail (sección 7 del contrato). `Remitente` es a la vez
 * el elemento del listado y la respuesta del `POST`; `Solicitud` es el cuerpo del `POST`.
 */
public record GmailListaBlancaDTO(
        @JsonProperty("remitentes") List<Remitente> remitentes
) {
    /** Remitente exento de la regla automática. */
    public record Remitente(
            @JsonProperty("id") String id,
            @JsonProperty("remitente") String remitente
    ) { }

    /** Cuerpo del `POST …/papelera/lista-blanca`. */
    public record Solicitud(
            @JsonProperty("remitente") String remitente
    ) { }
}
