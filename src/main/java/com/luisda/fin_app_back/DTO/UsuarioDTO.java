package com.luisda.fin_app_back.DTO;

import com.luisda.fin_app_back.Model.MUsuario;
import java.time.LocalDate;

/** Campos públicos de usuario que pueden devolverse a la aplicación web. */
public record UsuarioDTO(String idUsuario, String nombre, LocalDate fechaCreacion) {
    public static UsuarioDTO desde(MUsuario usuario) {
        if (usuario == null) return null;
        return new UsuarioDTO(usuario.getIdUsuario(), usuario.getNombre(), usuario.getFechaCreacion());
    }
}
