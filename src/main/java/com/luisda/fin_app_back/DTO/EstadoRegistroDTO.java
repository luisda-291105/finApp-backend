package com.luisda.fin_app_back.DTO;

/** Estado inicial del registro: dice si ya existe al menos una cuenta en la aplicación. */
public record EstadoRegistroDTO(boolean hayUsuarios) {
}
