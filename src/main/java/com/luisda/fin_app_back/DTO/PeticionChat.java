package com.luisda.fin_app_back.DTO;

import java.util.List;

/** Petición efímera del asistente; no se persiste en la base de datos. */
public record PeticionChat(String mensaje, List<TurnoChat> historial) {}
