package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Suscripción unificada tal como se entrega al cliente.
 * Las suscripciones manuales usan nombre, categoría, monto, fecha de cobro e importancia;
 * las detectadas en Gmail usan remitente, etiquetas de la cuenta y datos de baja.
 */
public record SuscripcionDTO(
        String id,
        String origen,
        String nombre,
        String categoria,
        List<String> categorias,
        BigDecimal monto,
        LocalDate fechaCobro,
        String importancia,
        boolean activa,
        String correoRemitente,
        String cuentaGmailId,
        String cuentaGmail,
        String urlBaja,
        String mailtoBaja,
        boolean bajaConUnClic,
        Instant ultimaActividad,
        Instant fechaCreacion,
        Instant fechaActualizacion
) { }
