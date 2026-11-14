package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos que envía el cliente para registrar o editar una suscripción manual.
 * `importancia` acepta "Alta" o "Baja" (sin distinguir mayúsculas) y se normaliza a ALTA/BAJA.
 * `activa` es opcional: al crear vale true y al editar se conserva el valor actual si se omite.
 */
public record SuscripcionSolicitudDTO(
        String nombre,
        BigDecimal monto,
        LocalDate fechaCobro,
        String categoria,
        String importancia,
        Boolean activa
) { }
