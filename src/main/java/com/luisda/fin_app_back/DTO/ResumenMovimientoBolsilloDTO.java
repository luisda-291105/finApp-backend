package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;

public record ResumenMovimientoBolsilloDTO(
        String idBolsillo,
        String nombreBolsillo,
        long cantidad,
        BigDecimal ingresos,
        BigDecimal gastos,
        BigDecimal ahorros,
        BigDecimal saldo
) {}
