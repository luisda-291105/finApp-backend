package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Configuración de la regla automática de papelera de una cuenta Gmail (sección 4 del contrato):
 * las cinco categorías con su estado, la antigüedad mínima, el límite por ejecución y si la cuenta
 * tiene el permiso ampliado. `Solicitud` es el cuerpo del `PUT`.
 */
public record GmailPapeleraConfiguracionDTO(
        @JsonProperty("categorias") List<CategoriaConfigurada> categorias,
        @JsonProperty("antiguedad_minima_dias") int antiguedadMinimaDias,
        @JsonProperty("limite_por_ejecucion") int limitePorEjecucion,
        @JsonProperty("scope_autorizado") boolean scopeAutorizado
) {
    /** Una categoría configurable con su estado y si requiere advertencia (UPDATES). */
    public record CategoriaConfigurada(
            @JsonProperty("categoria") String categoria,
            @JsonProperty("activa") boolean activa,
            @JsonProperty("requiere_advertencia") boolean requiereAdvertencia,
            @JsonProperty("advertencia_aceptada") boolean advertenciaAceptada
    ) { }

    /**
     * Cuerpo del `PUT …/papelera/configuracion`. Los campos numéricos y la advertencia son
     * opcionales (`null` = conservar el valor actual o `false`).
     */
    public record Solicitud(
            @JsonProperty("categorias_activas") List<String> categoriasActivas,
            @JsonProperty("antiguedad_minima_dias") Integer antiguedadMinimaDias,
            @JsonProperty("limite_por_ejecucion") Integer limitePorEjecucion,
            @JsonProperty("advertencia_aceptada") Boolean advertenciaAceptada
    ) {
        /** Solicitud sin cambios: ninguna categoría activa y valores por defecto. */
        public static Solicitud vacia() {
            return new Solicitud(List.of(), null, null, null);
        }
    }
}
