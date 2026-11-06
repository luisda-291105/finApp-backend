package com.luisda.fin_app_back.DTO;

/** Propuesta de cambio que requiere confirmación explícita del usuario activo. */
public record AccionAsistentePendiente(String token, String descripcion) {}
