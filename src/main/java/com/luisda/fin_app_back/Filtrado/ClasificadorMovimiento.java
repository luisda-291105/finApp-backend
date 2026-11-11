package com.luisda.fin_app_back.Filtrado;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Aplica reglas locales antes de delegar una clasificación incierta al cliente del modelo. */
@Component
public final class ClasificadorMovimiento {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClasificadorMovimiento.class);
    private static final BigDecimal CONFIANZA_MINIMA = new BigDecimal("0.75");
    private final ClienteModeloFiltro clienteModelo;
    private final ValidadorRespuesta validador;

    public ClasificadorMovimiento(ClienteModeloFiltro clienteModelo, ValidadorRespuesta validador) {
        this.clienteModelo = Objects.requireNonNull(clienteModelo, "clienteModelo");
        this.validador = Objects.requireNonNull(validador, "validador");
    }

    /** Clasifica primero con las reglas recibidas y solo consulta al modelo si ninguna coincide. */
    public ResultadoClasificacion clasificar(String remitente, String contenido, List<ReglaFiltro> reglas) {
        List<ReglaFiltro> reglasLocales = reglas == null ? List.of() : reglas;
        String remitenteNormalizado = NormalizadorFiltro.normalizar(remitente);
        String contenidoNormalizado = NormalizadorFiltro.normalizar(contenido);
        for (ReglaFiltro regla : reglasLocales) {
            if (regla != null && regla.coincide(remitenteNormalizado, contenidoNormalizado)) {
                return new ResultadoClasificacion(regla.idBolsillo(), regla.bolsillo(), null, null, null, BigDecimal.ONE, false);
            }
        }
        String respuesta;
        try {
            respuesta = clienteModelo.clasificar(PromptClasificador.construir(remitente, contenido));
        } catch (RuntimeException error) {
            LOGGER.warn("Fallo del cliente de modelo durante el filtrado (tipo: {})", error.getClass().getSimpleName());
            return revisarOtros();
        }
        try {
            Map<String, Object> datos = validador.extraerJson(respuesta);
            ResultadoClasificacion resultado = validador.validarClasificacion(datos);
            if (resultado.confianza().compareTo(CONFIANZA_MINIMA) < 0) return revisarOtros(resultado);
            return resultado;
        } catch (ValidadorRespuesta.RespuestaInvalidaException error) {
            LOGGER.warn("Respuesta inválida del clasificador ({})", error.getClass().getSimpleName());
            return revisarOtros();
        } catch (RuntimeException error) {
            LOGGER.error("Error inesperado al validar la respuesta del clasificador", error);
            return revisarOtros();
        }
    }

    private static ResultadoClasificacion revisarOtros() {
        return new ResultadoClasificacion(null, TipoBolsillo.OTROS, null, null, null, BigDecimal.ZERO, true);
    }

    private static ResultadoClasificacion revisarOtros(ResultadoClasificacion resultado) {
        return new ResultadoClasificacion(null, TipoBolsillo.OTROS, resultado.monto(), resultado.comercio(), resultado.fecha(),
                resultado.confianza(), true);
    }
}
