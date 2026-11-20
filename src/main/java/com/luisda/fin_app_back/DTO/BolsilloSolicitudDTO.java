package com.luisda.fin_app_back.DTO;

import java.math.BigDecimal;
import java.util.List;

/** Campos editables de un bolsillo; el propietario se toma de la sesión. */
public record BolsilloSolicitudDTO(String nombre, Boolean estado, BigDecimal montoObjetivo, String tipo, String descripcion, List<String> origenes) {}
