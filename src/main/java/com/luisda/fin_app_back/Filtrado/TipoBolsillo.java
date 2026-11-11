package com.luisda.fin_app_back.Filtrado;

import java.util.Locale;

/** Tipos de clasificación aceptados por la capa de filtrado. */
public enum TipoBolsillo {
    GASTO,
    INGRESO,
    OTROS;

    /** Convierte valores libres a un tipo conocido; los valores desconocidos quedan como OTROS. */
    public static TipoBolsillo parse(Object valor) {
        if (valor == null) return OTROS;
        try {
            return valueOf(valor.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return OTROS;
        }
    }
}
