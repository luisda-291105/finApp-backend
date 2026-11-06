package com.luisda.fin_app_back.DTO;

/** Representación pública del contacto sin exponer datos de su cuenta propietaria. */
public record ContactoDTO(String idContacto, String nombre, String telefono, String notas) {}
