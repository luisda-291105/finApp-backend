package com.luisda.fin_app_back.DTO;

/** Campos editables de un bolsillo; el propietario se toma de la sesión. */
public record BolsilloSolicitudDTO(String nombre, Boolean estado) {}
