package com.luisda.fin_app_back.DTO;

import java.time.LocalDateTime;
public record MuestraRespuestaDTO(Long id, String usuarioId, String tipo, String titulo, String datos, LocalDateTime fechaCreacion) {}
