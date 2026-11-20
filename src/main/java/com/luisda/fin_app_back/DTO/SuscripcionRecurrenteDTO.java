package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cobro recurrente de una suscripción con monto y fecha de cobro, o de un pago
 * periódico detectado en los movimientos de Contabilidad.
 * `origen` indica la fuente: MANUAL, GMAIL o MOVIMIENTOS.
 */
public record SuscripcionRecurrenteDTO(
        String id,
        String nombre,
        String categoria,
        BigDecimal monto,
        String frecuencia,
        int intervaloDias,
        LocalDate ultimoCobro,
        LocalDate proximoCobro,
        int cobrosDetectados,
        String origen
) { }
