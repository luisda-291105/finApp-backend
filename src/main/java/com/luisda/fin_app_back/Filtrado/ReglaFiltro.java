package com.luisda.fin_app_back.Filtrado;

import java.util.List;
import java.util.regex.Pattern;

/** Regla inmutable que compila una vez sus palabras clave y remitentes. */
public final class ReglaFiltro {
    private final TipoBolsillo bolsillo;
    private final String idBolsillo;
    private final List<Pattern> palabrasClave;
    private final List<Pattern> remitentes;

    public ReglaFiltro(TipoBolsillo bolsillo, List<String> palabrasClave, List<String> remitentes) {
        this(null, bolsillo, palabrasClave, remitentes);
    }

    public ReglaFiltro(String idBolsillo, TipoBolsillo bolsillo, List<String> palabrasClave, List<String> remitentes) {
        this.idBolsillo = idBolsillo;
        this.bolsillo = bolsillo == null ? TipoBolsillo.OTROS : bolsillo;
        this.palabrasClave = compilar(palabrasClave);
        this.remitentes = compilar(remitentes);
    }

    public TipoBolsillo bolsillo() {
        return bolsillo;
    }

    public String idBolsillo() { return idBolsillo; }

    /** Evalúa texto ya normalizado para no repetir trabajo por cada regla. */
    boolean coincide(String remitenteNormalizado, String contenidoNormalizado) {
        return coincideAlguna(palabrasClave, contenidoNormalizado)
                || coincideAlguna(remitentes, remitenteNormalizado);
    }

    private static List<Pattern> compilar(List<String> valores) {
        if (valores == null || valores.isEmpty()) return List.of();
        return valores.stream()
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .map(NormalizadorFiltro::normalizar)
                .map(valor -> Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(valor) + "(?![\\p{L}\\p{N}_])"))
                .toList();
    }

    private static boolean coincideAlguna(List<Pattern> patrones, String textoNormalizado) {
        return !textoNormalizado.isEmpty() && patrones.stream().anyMatch(patron -> patron.matcher(textoNormalizado).find());
    }
}
