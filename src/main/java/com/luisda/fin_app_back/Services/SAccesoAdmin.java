package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * @uso               Autenticación administrativa respaldada por secretos del entorno
 * @capa              Servicio
 * @responsabilidades Validar LOGIN_USER/LOGIN_PASSWORD y resolver la cuenta que representa la sesión
 * @datos             Credenciales administrativas en .env o variables de entorno
 * @dependencias      SConfiguracionEntorno, IUsuario
 * @usadoPor          CUsuario
 */
@Service
public class SAccesoAdmin {
    public static final String CLAVE_USUARIO = "LOGIN_USER";
    public static final String CLAVE_PASSWORD = "LOGIN_PASSWORD";
    public static final String CLAVE_USUARIO_ID = "FINAPP_ADMIN_USER_ID";

    private final SConfiguracionEntorno entorno;
    private final IUsuario usuarios;

    public SAccesoAdmin(SConfiguracionEntorno entorno, IUsuario usuarios) {
        this.entorno = entorno;
        this.usuarios = usuarios;
    }

    /** Indica si el acceso administrativo está configurado en el entorno. */
    public boolean disponible() {
        return entorno.configurada(CLAVE_USUARIO) && entorno.configurada(CLAVE_PASSWORD);
    }

    /**
     * Valida las credenciales administrativas contra la configuración del entorno.
     * Devuelve la cuenta de base de datos a la que corresponde la sesión.
     */
    public Optional<MUsuario> autenticar(String usuario, String password) {
        if (!disponible()) return Optional.empty();
        String esperadoUsuario = entorno.valor(CLAVE_USUARIO);
        String esperadaPassword = entorno.valor(CLAVE_PASSWORD);
        boolean coincideUsuario = MessageDigest.isEqual(
                esperadoUsuario.getBytes(StandardCharsets.UTF_8),
                (usuario == null ? "" : usuario.trim()).getBytes(StandardCharsets.UTF_8));
        boolean coincidePassword = MessageDigest.isEqual(
                esperadaPassword.getBytes(StandardCharsets.UTF_8),
                (password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
        if (!coincideUsuario || !coincidePassword) return Optional.empty();
        return resolverUsuario();
    }

    /**
     * Resuelve la cuenta que representa la sesión administrativa.
     * Prioridad: FINAPP_ADMIN_USER_ID y, si no existe, el propio LOGIN_USER.
     */
    private Optional<MUsuario> resolverUsuario() {
        String identificador = entorno.valor(CLAVE_USUARIO_ID);
        if (identificador != null) {
            Optional<MUsuario> porId = usuarios.findById(identificador);
            if (porId.isPresent()) return porId;
            if (identificador.contains("@")) {
                Optional<MUsuario> porCorreo = usuarios.findByCorreoIgnoreCase(identificador);
                if (porCorreo.isPresent()) return porCorreo;
            }
        }
        String alias = entorno.valor(CLAVE_USUARIO);
        Optional<MUsuario> porAlias = usuarios.findById(alias);
        if (porAlias.isPresent()) return porAlias;
        if (alias.contains("@")) {
            Optional<MUsuario> porCorreo = usuarios.findByCorreoIgnoreCase(alias);
            if (porCorreo.isPresent()) return porCorreo;
        }
        List<MUsuario> porNombre = usuarios.findByNombreIgnoreCase(alias);
        return porNombre.size() == 1 ? Optional.of(porNombre.get(0)) : Optional.empty();
    }
}
