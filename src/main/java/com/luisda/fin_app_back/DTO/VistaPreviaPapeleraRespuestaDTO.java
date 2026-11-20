package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta `200` del `POST …/papelera/vista-previa` (sección 5 del contrato): el identificador que
 * fija los parámetros evaluados, el total de correos elegibles, los remitentes agrupados por
 * categoría y el conteo de correos excluidos por cada motivo de RF-35/RF-36.
 */
public record VistaPreviaPapeleraRespuestaDTO(
        @JsonProperty("vista_previa_id") String vistaPreviaId,
        @JsonProperty("total_estimado") int totalEstimado,
        @JsonProperty("por_categoria") List<CategoriaVistaPrevia> porCategoria,
        @JsonProperty("excluidos_por_motivo") List<ExclusionPorMotivo> excluidosPorMotivo
) {
    /** Correos elegibles de una categoría con sus remitentes agrupados. */
    public record CategoriaVistaPrevia(
            @JsonProperty("categoria") String categoria,
            @JsonProperty("cantidad") int cantidad,
            @JsonProperty("remitentes") List<RemitenteVistaPrevia> remitentes
    ) { }

    /** Remitente y cuántos correos elegibles aporta a una categoría. */
    public record RemitenteVistaPrevia(
            @JsonProperty("remitente") String remitente,
            @JsonProperty("cantidad") int cantidad
    ) { }

    /** Motivo de exclusión (RF-35/RF-36) y cuántos correos descarta. */
    public record ExclusionPorMotivo(
            @JsonProperty("motivo") String motivo,
            @JsonProperty("cantidad") int cantidad
    ) { }
}
