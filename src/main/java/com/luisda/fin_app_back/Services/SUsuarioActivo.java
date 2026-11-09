package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class SUsuarioActivo {
    private final IUsuario usuarios;
    private final JwtTokenService tokens;
    public SUsuarioActivo(IUsuario usuarios, JwtTokenService tokens) {
        this.usuarios = usuarios;
        this.tokens = tokens;
    }
    /** Devuelve únicamente el identificador validado del JWT, sin cargar el perfil. */
    public Optional<String> obtenerIdUsuario(HttpServletRequest request) {
        return tokens.obtenerIdUsuario(request);
    }
    public Optional<MUsuario> obtenerUsuario(HttpServletRequest request) {
        return obtenerIdUsuario(request).flatMap(usuarios::findById);
    }
}
