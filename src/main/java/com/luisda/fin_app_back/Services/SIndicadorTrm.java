package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.IndicadorTrmDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * @uso               Consulta la TRM oficial vigente (COP por 1 USD) para el dashboard
 * @capa              Servicio
 * @responsabilidades Consultar datos abiertos (SFC/Socrata), cachear con TTL, calcular variación
 *                    y servir el último valor válido si la fuente externa falla
 * @datos             Solo datos públicos de la TRM; no lee ni expone credenciales
 * @dependencias      RestClient (datos.gov.co), Jackson
 * @usadoPor          CIndicadores
 */
@Service
public class SIndicadorTrm {
    private static final Logger LOGGER = LoggerFactory.getLogger(SIndicadorTrm.class);
    public static final String FUENTE = "Superintendencia Financiera de Colombia (datos.gov.co)";
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    private static final int MAX_PUNTOS = 30;

    private final ObjectMapper json = new ObjectMapper();

    @Value("${finapp.trm.url:https://www.datos.gov.co/resource/32sa-8pi3.json}")
    private String urlFuente;

    @Value("${finapp.trm.cache-seconds:21600}")
    private long ttlSegundos;

    private volatile RestClient clienteHttp;
    private volatile Instant expiracionCache = Instant.EPOCH;
    private volatile IndicadorTrmDTO ultimoValido;

    /** Devuelve la TRM vigente desde la caché si está fresca; si no, consulta la fuente y como respaldo entrega el último valor válido. */
    public IndicadorTrmDTO obtener() {
        IndicadorTrmDTO cacheado = ultimoValido;
        if (cacheado != null && cacheado.valor() != null && Instant.now().isBefore(expiracionCache)) return cacheado;
        try {
            IndicadorTrmDTO fresco = consultarFuente();
            ultimoValido = fresco;
            expiracionCache = Instant.now().plusSeconds(ttlSegundos);
            return fresco;
        } catch (Exception error) {
            // Registro interno sin detalles sensibles; al cliente solo llega un valor nulo controlado.
            LOGGER.warn("No se pudo consultar la TRM vigente: {}", error.getMessage());
            if (cacheado != null && cacheado.valor() != null) return cacheado;
            return IndicadorTrmDTO.sinDatos(FUENTE);
        }
    }

    IndicadorTrmDTO consultarFuente() throws Exception {
        if (urlFuente == null || urlFuente.isBlank()) return IndicadorTrmDTO.sinDatos(FUENTE);
        String hoy = LocalDate.now(ZONA_COLOMBIA).toString();
        URI destino = UriComponentsBuilder.fromUriString(urlFuente)
                .queryParam("$limit", MAX_PUNTOS)
                .queryParam("$order", "vigenciadesde DESC")
                .queryParam("$where", "vigenciadesde<='" + hoy + "'")
                .build()
                .encode()
                .toUri();
        String cuerpo = clienteHttp().get()
                .uri(destino)
                .retrieve()
                .body(String.class);
        return mapear(cuerpo == null ? null : json.readTree(cuerpo), FUENTE);
    }

    /** Normaliza la respuesta Socrata al contrato del dashboard y calcula la variación porcentual vs. el punto anterior. */
    static IndicadorTrmDTO mapear(JsonNode filas, String fuente) {
        if (filas == null || !filas.isArray() || filas.isEmpty()) return IndicadorTrmDTO.sinDatos(fuente);
        JsonNode vigente = filas.get(0);
        BigDecimal valor = decimal(vigente.get("valor"));
        if (valor == null || valor.signum() <= 0) return IndicadorTrmDTO.sinDatos(fuente);

        List<IndicadorTrmDTO.PuntoHistoricoDTO> historico = new ArrayList<>();
        for (JsonNode fila : filas) {
            BigDecimal punto = decimal(fila.get("valor"));
            String fecha = fechaFila(fila.get("vigenciadesde"));
            if (punto == null || punto.signum() <= 0 || fecha == null) continue;
            historico.add(new IndicadorTrmDTO.PuntoHistoricoDTO(fecha, punto));
        }
        if (historico.isEmpty()) return IndicadorTrmDTO.sinDatos(fuente);

        BigDecimal variacion = null;
        if (historico.size() > 1) {
            BigDecimal anterior = historico.get(1).valor();
            variacion = historico.get(0).valor().subtract(anterior)
                    .divide(anterior, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new IndicadorTrmDTO(valor, fechaFila(vigente.get("vigenciadesde")), "USD", "COP",
                variacion, fuente, historico);
    }

    private static BigDecimal decimal(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) return null;
        try { return new BigDecimal(nodo.asText().trim()); } catch (NumberFormatException error) { return null; }
    }

    private static String fechaFila(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) return null;
        String texto = nodo.asText();
        return texto.length() >= 10 ? texto.substring(0, 10) : texto;
    }

    private RestClient clienteHttp() {
        RestClient cliente = clienteHttp;
        if (cliente == null) {
            synchronized (this) {
                if (clienteHttp == null) {
                    HttpClient clienteJdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteJdk);
                    fabrica.setReadTimeout(Duration.ofSeconds(10));
                    clienteHttp = RestClient.builder().requestFactory(fabrica).build();
                }
                cliente = clienteHttp;
            }
        }
        return cliente;
    }
}
