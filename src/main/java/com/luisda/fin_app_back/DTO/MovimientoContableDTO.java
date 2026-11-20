package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;

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
        String origenNombre,
        EstadoRevisionMovimiento estadoRevision,
        String idTarjeta
) {
    public MovimientoContableDTO(String idMovimiento, String idBolsillo, String nombreBolsillo,
            LocalDate fecha, BigDecimal monto, String tipo, String categoria, String concepto,
            String origenTipo, String origenId, String origenNombre, EstadoRevisionMovimiento estadoRevision) {
        this(idMovimiento, idBolsillo, nombreBolsillo, fecha, monto, tipo, categoria, concepto,
                origenTipo, origenId, origenNombre, estadoRevision, null);
    }
}
