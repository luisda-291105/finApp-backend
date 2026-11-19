package com.luisda.fin_app_back.DTO;

import java.time.LocalDateTime;

/** Mensaje del asistente dentro del grupo, tal como lo consume la web. */
public record GrupoMensajeDTO(String idMensaje, String idGrupo, String idUsuario,
                              String rol, String texto, LocalDateTime fecha) { }
