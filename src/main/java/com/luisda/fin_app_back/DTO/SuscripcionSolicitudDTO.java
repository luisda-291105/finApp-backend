package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos que envía el cliente para registrar o editar una suscripción manual.
 * `importancia` y `prioridad` aceptan "Alta", "Media" o "Baja" (sin distinguir mayúsculas)
 * y se normalizan a ALTA/MEDIA/BAJA. `apodo` es un alias opcional para reconocer la suscripción.
 * `activa` es opcional: al crear vale true y al editar se conserva el valor actual si se omite.
 */
public record SuscripcionSolicitudDTO(
        String nombre,
        BigDecimal monto,
        LocalDate fechaCobro,
        String categoria,
        String importancia,
        Boolean activa,
        String prioridad,
        String apodo
) {
    /** Constructor compatible con clientes que aún no envían prioridad ni apodo. */
    public SuscripcionSolicitudDTO(String nombre, BigDecimal monto, LocalDate fechaCobro,
                                   String categoria, String importancia, Boolean activa) {
        this(nombre, monto, fechaCobro, categoria, importancia, activa, null, null);
    }
}
