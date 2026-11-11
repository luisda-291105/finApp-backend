package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;

public record ResumenGlobalContabilidadDTO(
        BigDecimal ingresos,
        BigDecimal gastos,
        BigDecimal ahorros,
        BigDecimal transferencias,
        BigDecimal saldoAhorros,
        long porRevisar,
        BigDecimal montoPorRevisar
) {}
