package com.luisda.fin_app_back.DTO;

/** Resumen público mínimo de un usuario para buscar e invitar a un grupo. */
public record GrupoUsuarioResumenDTO(String idUsuario, String nombre, String correo,
                                     String telefono) { }
