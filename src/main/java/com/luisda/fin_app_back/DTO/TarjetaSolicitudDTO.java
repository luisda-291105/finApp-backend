package com.luisda.fin_app_back.DTO;

import java.time.LocalDate;

/** Campos editables; el número se admite solo para enmascararlo antes de persistir. */
public record TarjetaSolicitudDTO(String titular, String numero, LocalDate expiracion, String entidad) {}
