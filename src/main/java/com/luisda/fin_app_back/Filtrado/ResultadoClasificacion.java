package com.luisda.fin_app_back.Filtrado;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resultado normalizado de la clasificación de un mensaje entrante. */
public record ResultadoClasificacion(
        String idBolsillo,
        TipoBolsillo bolsillo,
        BigDecimal monto,
        String comercio,
        LocalDate fecha,
        BigDecimal confianza,
        boolean revisar
) { }
