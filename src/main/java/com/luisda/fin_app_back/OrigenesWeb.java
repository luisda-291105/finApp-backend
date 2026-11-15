package com.luisda.fin_app_back;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @uso               Compara los orígenes de las peticiones con los permitidos
 * @capa              Web
 * @responsabilidades Normalizar scheme, host, puerto y barra final antes de comparar
 * @datos             Orígenes permitidos desde finapp.cors.allowed-origins
 * @dependencias      Ninguno
 * @usadoPor          CorsConfig, ValidacionOrigenInterceptor
 */
public final class OrigenesWeb {

    private OrigenesWeb() { }

    /** Normaliza una lista separada por comas en orígenes comparables. */
    public static List<String> normalizarLista(String origenesSeparados) {
        List<String> normalizados = new ArrayList<>();
        if (origenesSeparados == null || origenesSeparados.isBlank()) return normalizados;
        for (String origen : origenesSeparados.split(",")) {
            String normalizado = normalizar(origen);
            if (normalizado != null) normalizados.add(normalizado);
        }
        return normalizados;
    }

    /**
     * Normaliza un origen: minúsculas, sin barra final y sin espacios.
     * El esquema y el host no distinguen mayúsculas; el puerto sí se conserva.
     */
    public static String normalizar(String origen) {
        if (origen == null) return null;
        String limpio = origen.trim().toLowerCase(Locale.ROOT);
        if (limpio.isEmpty()) return null;
        while (limpio.endsWith("/")) limpio = limpio.substring(0, limpio.length() - 1);
        return limpio.isEmpty() ? null : limpio;
    }

    /** Indica si el origen recibido está en la lista de permitidos. */
    public static boolean permitido(String origen, List<String> permitidos) {
        String normalizado = normalizar(origen);
        if (normalizado == null) return false;
        return permitidos.contains(normalizado);
    }
}
