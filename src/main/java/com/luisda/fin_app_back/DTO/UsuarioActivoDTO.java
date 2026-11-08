package com.luisda.fin_app_back.DTO;

import java.time.LocalDate;

public record UsuarioActivoDTO(String idUsuario, String nombre, LocalDate fechaCreacion, String correo, String telefono) {}
