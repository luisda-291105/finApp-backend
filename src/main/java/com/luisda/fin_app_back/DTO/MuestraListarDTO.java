package com.luisda.fin_app_back.DTO;

import java.time.LocalDateTime;
public record MuestraListarDTO(Long id, String tipo, String titulo, String datos, LocalDateTime fechaCreacion) {}
