package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GoogleOAuthStateDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.SAuthGoogle;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth/google")
public class CAuthGoogle {
    private final SAuthGoogle authGoogle;

    @Value("${finapp.cors.allowed-origins:http://localhost:5173}") private String origenesPermitidos;
    @Value("${finapp.web.base-url:${WEB_APP_URL:http://localhost:5173}}") private String webBaseUrl;

    public CAuthGoogle(SAuthGoogle authGoogle) { this.authGoogle = authGoogle; }

    @GetMapping("/state")
    public ResponseEntity<?> crearEstado(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin
    ) {
        if (origin != null && Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim).noneMatch(origin::equals)) {
            return ResponseEntity.status(403).body(RespuestaModalDTO.error("Origen no autorizado para iniciar sesión.", null));
        }
        return ResponseEntity.ok(RespuestaModalDTO.exito("Solicitud OAuth preparada.",
                new GoogleOAuthStateDTO(authGoogle.crearEstado(request))));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request
    ) {
        String destino;
        try {
            if (error != null && !error.isBlank()) {
                authGoogle.cancelar(state, request);
                destino = urlLogin("cancelled");
            } else {
                authGoogle.autenticar(code, state, request);
                destino = urlLogin("success");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            destino = urlLogin("failed");
        } catch (RuntimeException exception) {
            destino = urlLogin("failed");
        }
        return ResponseEntity.status(302).location(URI.create(destino)).build();
    }

    private String urlLogin(String resultado) {
        String base = webBaseUrl == null ? "http://localhost:5173" : webBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/?google=" + URLEncoder.encode(resultado, StandardCharsets.UTF_8);
    }
}
