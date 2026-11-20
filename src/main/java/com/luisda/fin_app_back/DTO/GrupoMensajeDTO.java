package com.luisda.fin_app_back.DTO;

import java.time.LocalDateTime;

/** Mensaje del chat de un grupo tal como lo consume la web; incluye el nombre del autor. */
public record GrupoMensajeDTO(String idMensaje, String idGrupo, String idUsuario,
                              String nombreAutor, String rol, String texto, LocalDateTime fecha) { }
