package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoContableDTO(
        String idMovimiento,
        String idBolsillo,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String categoria,
        String concepto
) {}
