package com.luisda.fin_app_back.Filtrado;

import java.text.Normalizer;
import java.util.Locale;

/** Normalización compartida para comparar reglas y construir su clave única. */
public final class NormalizadorFiltro {
    private NormalizadorFiltro() { }

    public static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
