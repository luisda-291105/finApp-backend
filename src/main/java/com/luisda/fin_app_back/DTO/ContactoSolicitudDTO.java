package com.luisda.fin_app_back.DTO;

/** Campos editables del contacto; la identidad propietaria siempre se obtiene de la sesión. */
public record ContactoSolicitudDTO(String nombre, String telefono, String notas) {}
