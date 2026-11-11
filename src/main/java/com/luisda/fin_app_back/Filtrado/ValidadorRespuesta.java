package com.luisda.fin_app_back.Filtrado;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Extrae y normaliza la respuesta JSON del clasificador sin ejecutar su contenido. */
@Component
public final class ValidadorRespuesta {
    private static final int MAXIMO_JSON_EXTRAIDO = 4_000;
    private static final Pattern OBJETO_JSON = Pattern.compile("(?s)\\{[^{}]{1," + MAXIMO_JSON_EXTRAIDO + "}\\}");
    private static final TypeReference<Map<String, Object>> MAPA_JSON = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidadorRespuesta() {
        this(new ObjectMapper());
    }

    public ValidadorRespuesta(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parsea una respuesta JSON o recupera un objeto JSON plano de texto envolvente.
     * La extracción admite objetos de hasta 4000 caracteres sin llaves anidadas.
     */
    public Map<String, Object> extraerJson(String respuesta) {
        if (respuesta == null || respuesta.isBlank()) throw new RespuestaInvalidaException("La respuesta del modelo está vacía.");
        String texto = respuesta.trim();
        try {
            Map<String, Object> datos = objectMapper.readValue(texto, MAPA_JSON);
            if (datos == null) throw new RespuestaInvalidaException("La respuesta JSON debe ser un objeto.");
            return datos;
        } catch (JsonProcessingException respuestaNoEsObjetoJson) {
            Matcher matcher = OBJETO_JSON.matcher(texto);
            while (matcher.find()) {
                if (estaDentroDeOtroObjeto(texto, matcher.start())) continue;
                try {
                    return objectMapper.readValue(matcher.group(), MAPA_JSON);
                } catch (JsonProcessingException objetoNoValido) {
                    // Continúa buscando dentro del límite definido para el objeto.
                }
            }
            throw new RespuestaInvalidaException("La respuesta no contiene un objeto JSON válido.", respuestaNoEsObjetoJson);
        }
    }

    /** Normaliza campos de clasificación y limita la confianza al intervalo [0, 1]. */
    public ResultadoClasificacion validarClasificacion(Map<String, ?> datos) {
        if (datos == null) throw new RespuestaInvalidaException("No se recibieron datos de clasificación.");
        TipoBolsillo bolsillo = normalizarBolsillo(datos.get("bolsillo"));
        BigDecimal monto = decimal(datos.get("monto"));
        BigDecimal confianza = limitarConfianza(decimal(datos.get("confianza")));
        String comercio = texto(datos.get("comercio"));
        LocalDate fecha = fecha(datos.get("fecha"));
        return new ResultadoClasificacion(null, bolsillo, monto, comercio, fecha, confianza, false);
    }

    private static TipoBolsillo normalizarBolsillo(Object valor) {
        return TipoBolsillo.parse(valor);
    }

    /**
     * Convierte un monto COP a BigDecimal. Si hay coma y punto, el último separador es decimal;
     * con uno solo, tres cifras a la derecha indican miles y una o dos indican decimales.
     */
    private static BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal decimal) return decimal;
        if (valor instanceof Number numero) return new BigDecimal(numero.toString());
        if (valor == null) return null;
        String texto = valor.toString().trim().replaceAll("(?i)COP", "")
                .replace("$", "").replaceAll("\\s+", "");
        if (texto.isEmpty()) return null;
        try {
            int ultimaComa = texto.lastIndexOf(',');
            int ultimoPunto = texto.lastIndexOf('.');
            if (ultimaComa >= 0 && ultimoPunto >= 0) {
                char decimal = ultimaComa > ultimoPunto ? ',' : '.';
                char miles = decimal == ',' ? '.' : ',';
                texto = texto.replace(String.valueOf(miles), "");
                if (decimal == ',') texto = texto.replace(',', '.');
            } else {
                char separador = ultimaComa >= 0 ? ',' : '.';
                int indice = Math.max(ultimaComa, ultimoPunto);
                if (indice >= 0) {
                    int cifrasDerecha = texto.length() - indice - 1;
                    if (cifrasDerecha == 3 || cifrasDerecha == 0) {
                        texto = texto.replace(String.valueOf(separador), "");
                    } else if (cifrasDerecha <= 2) {
                        texto = texto.replace(String.valueOf(separador), "");
                        texto = texto.substring(0, texto.length() - cifrasDerecha) + "."
                                + texto.substring(texto.length() - cifrasDerecha);
                    } else {
                        return null;
                    }
                }
            }
            return new BigDecimal(texto);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static BigDecimal limitarConfianza(BigDecimal confianza) {
        if (confianza == null) return BigDecimal.ZERO;
        if (confianza.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (confianza.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return confianza;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static LocalDate fecha(Object valor) {
        if (valor == null || valor.toString().isBlank()) return null;
        try {
            return LocalDate.parse(valor.toString().trim());
        } catch (DateTimeParseException ignorado) {
            return null;
        }
    }

    private static boolean estaDentroDeOtroObjeto(String texto, int inicio) {
        int profundidad = 0;
        for (int indice = 0; indice < inicio; indice++) {
            char caracter = texto.charAt(indice);
            if (caracter == '{') profundidad++;
            else if (caracter == '}' && profundidad > 0) profundidad--;
        }
        return profundidad > 0;
    }

    /** Error controlado para respuestas vacías o que no contienen un objeto JSON. */
    public static final class RespuestaInvalidaException extends IllegalArgumentException {
        public RespuestaInvalidaException(String mensaje) { super(mensaje); }
        public RespuestaInvalidaException(String mensaje, Throwable causa) { super(mensaje, causa); }
    }
}
