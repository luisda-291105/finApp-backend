package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Suscripción recurrente inferida únicamente de gastos reales registrados. */
public record SuscripcionRecurrenteDTO(
        String id,
        String nombre,
        String categoria,
        BigDecimal montoPromedio,
        String frecuencia,
        int intervaloDias,
        LocalDate ultimoCobro,
        LocalDate proximoCobro,
        int cobrosDetectados
) { }
