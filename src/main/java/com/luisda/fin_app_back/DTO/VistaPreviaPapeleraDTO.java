package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Cuerpo del `POST …/papelera/vista-previa` (sección 5 del contrato): las categorías a evaluar y la
 * antigüedad mínima (días) sobre las que se calcula el `total_estimado` de la limpieza explícita.
 */
public record VistaPreviaPapeleraDTO(
        @JsonProperty("categorias") List<String> categorias,
        @JsonProperty("antiguedad_minima_dias") int antiguedadMinimaDias
) { }
