package com.luisda.fin_app_back.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;

public record UsuarioLoginDTO(@JsonAlias("idUsuario") String identificador, String contrasena) {}
