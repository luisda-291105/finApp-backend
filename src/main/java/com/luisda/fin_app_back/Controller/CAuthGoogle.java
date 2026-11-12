package com.luisda.fin_app_back.Controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.luisda.fin_app_back.DTO.GoogleCompletarRegistroDTO;
import com.luisda.fin_app_back.DTO.GoogleOAuthStateDTO;
import com.luisda.fin_app_back.DTO.GoogleRegistroPendienteDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.DTO.UsuarioActivoDTO;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Services.SAuthGoogle;
import com.luisda.fin_app_back.Services.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@Tag(name = "Autenticación Google", description = "Conexión, callback y registro OAuth de Google.")
@RequestMapping("/api/auth/google")
public class CAuthGoogle {
    private final SAuthGoogle authGoogle;
    private final JwtTokenService tokens;

    @Value("${finapp.cors.allowed-origins:http://localhost:5173}") private String origenesPermitidos;
    @Value("${finapp.web.base-url:${WEB_APP_URL:http://localhost:5173}}") private String webBaseUrl;

    public CAuthGoogle(SAuthGoogle authGoogle, JwtTokenService tokens) {
        this.authGoogle = authGoogle;
        this.tokens = tokens;
    }

    @GetMapping("/state")
    public ResponseEntity<?> crearEstado(
            @RequestParam(name = "modo", defaultValue = "login") String modo,
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin
    ) {
        if (origin != null && Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim).noneMatch(origin::equals)) {
            return ResponseEntity.status(403).body(RespuestaModalDTO.error("Origen no autorizado para iniciar sesión.", null));
        }
        return ResponseEntity.ok(RespuestaModalDTO.exito("Solicitud OAuth preparada.",
                new GoogleOAuthStateDTO(authGoogle.crearEstado(modo, request))));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request
    ) {
        String destino;
        ResponseCookie cookie = null;
        try {
            if (error != null && !error.isBlank()) {
                authGoogle.cancelar(state, request);
                destino = urlLogin("cancelled");
            } else {
                var resultado = authGoogle.procesarCallback(code, state, request);
                if (resultado instanceof SAuthGoogle.ResultadoCallbackGoogle.LoginExitoso exito) {
                    cookie = tokens.cookie(tokens.emitir(exito.usuario().getIdUsuario()));
                    destino = urlLogin("success");
                } else if (resultado instanceof SAuthGoogle.ResultadoCallbackGoogle.NoRegistrado) {
                    destino = urlLogin("not_registered");
                } else if (resultado instanceof SAuthGoogle.ResultadoCallbackGoogle.YaRegistrado) {
                    destino = urlLogin("already_registered");
                } else if (resultado instanceof SAuthGoogle.ResultadoCallbackGoogle.CorreoYaRegistrado) {
                    destino = urlLogin("email_exists");
                } else if (resultado instanceof SAuthGoogle.ResultadoCallbackGoogle.RegistroPendiente) {
                    destino = urlLogin("pending_registration");
                } else {
                    destino = urlLogin("failed");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            destino = urlLogin("failed");
        } catch (RuntimeException exception) {
            destino = urlLogin("failed");
        }
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(302).location(URI.create(destino));
        if (cookie != null) respuesta.header(HttpHeaders.SET_COOKIE, cookie.toString());
        return respuesta.build();
    }

    @GetMapping("/registro-pendiente")
    public ResponseEntity<?> registroPendiente(HttpServletRequest request) {
        SAuthGoogle.RegistroGooglePendiente pendiente = authGoogle.obtenerRegistroPendiente(request);
        if (pendiente == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(RespuestaModalDTO.error("No hay un registro de Google pendiente o la sesión expiró.", null));
        }
        return ResponseEntity.ok(RespuestaModalDTO.exito("Datos de Google verificados.",
                new GoogleRegistroPendienteDTO(pendiente.correo(), pendiente.nombre())));
    }

    @PostMapping("/completar-registro")
    public ResponseEntity<?> completarRegistro(@RequestBody GoogleCompletarRegistroDTO datos, HttpServletRequest request) {
        try {
            MUsuario usuario = authGoogle.completarRegistro(datos, request);
            ResponseCookie cookie = tokens.cookie(tokens.emitir(usuario.getIdUsuario()));
            UsuarioActivoDTO activo = new UsuarioActivoDTO(usuario.getIdUsuario(), usuario.getNombre(),
                    usuario.getFechaCreacion(), usuario.getCorreo(), usuario.getTelefono());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(RespuestaModalDTO.exito("Registro con Google completado con éxito.", activo));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(RespuestaModalDTO.error(error.getMessage(), null));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RespuestaModalDTO.error("No se pudo completar el registro con Google.", null));
        }
    }

    private String urlLogin(String resultado) {
        String base = webBaseUrl == null ? "http://localhost:5173" : webBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/login?google=" + URLEncoder.encode(resultado, StandardCharsets.UTF_8);
    }
}
