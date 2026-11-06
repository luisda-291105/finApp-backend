package com.luisda.fin_app_back.DTO;

import java.time.LocalDate;

/** Datos seguros de una tarjeta; nunca contiene CVV ni PAN completo. */
public record TarjetaDTO(String idTarjeta, String titular, String numero, LocalDate expiracion, String entidad) {}
