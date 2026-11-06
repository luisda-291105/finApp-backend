package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoContableSolicitudDTO(
        String tipo,
        LocalDate fecha,
        BigDecimal monto,
        String categoria,
        String concepto
) {}
