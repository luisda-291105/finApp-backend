package com.luisda.fin_app_back.DTO;

import java.time.LocalDateTime;

/** Miembro de un grupo tal como lo consume la web. */
public record GrupoMiembroDTO(String idUsuario, String nombre, String correo, String telefono,
                              String rol, LocalDateTime fecha) { }
