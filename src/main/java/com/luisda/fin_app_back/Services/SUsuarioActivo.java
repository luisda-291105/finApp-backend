package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class SUsuarioActivo {
    public static final String CLAVE_SESION = "usuarioActivoId";
    private final IUsuario usuarios;
    public SUsuarioActivo(IUsuario usuarios) { this.usuarios = usuarios; }
    /** Devuelve únicamente el identificador de la sesión, sin consultar ni cargar el perfil del usuario. */
    public Optional<String> obtenerIdUsuario(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return Optional.empty();
        Object id = session.getAttribute(CLAVE_SESION);
        return id instanceof String value && !value.isBlank() ? Optional.of(value) : Optional.empty();
    }
    public Optional<MUsuario> obtenerUsuario(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return Optional.empty();
        Object id = session.getAttribute(CLAVE_SESION);
        return id instanceof String value ? usuarios.findById(value) : Optional.empty();
    }
}
