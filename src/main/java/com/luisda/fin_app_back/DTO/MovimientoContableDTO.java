package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoContableDTO(
        String idMovimiento,
        String idBolsillo,
        String nombreBolsillo,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String categoria,
        String concepto,
        String origenTipo,
        String origenId,
        String origenNombre
) {}
