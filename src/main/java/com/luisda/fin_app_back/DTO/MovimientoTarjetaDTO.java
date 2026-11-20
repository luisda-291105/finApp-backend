package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @uso               Movimiento de contabilidad vinculado a una tarjeta
 * @capa              DTO
 * @responsabilidades Representar el historial de pagos de una tarjeta sin exponer el bolsillo ni datos internos
 * @datos             Identificadores, fecha, monto, tipo, categoría, concepto, origen y estado de revisión
 * @usadoPor          CTarjeta
 */
public record MovimientoTarjetaDTO(String idMovimiento, LocalDate fecha, BigDecimal monto, String tipo,
                                   String categoria, String concepto, String origenTipo, String origenNombre,
                                   String estadoRevision) {}
