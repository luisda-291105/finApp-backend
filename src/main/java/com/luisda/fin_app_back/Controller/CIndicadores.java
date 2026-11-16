package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.IndicadorTrmDTO;
import com.luisda.fin_app_back.DTO.RespuestaModalDTO;
import com.luisda.fin_app_back.Services.SIndicadorTrm;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de indicadores económicos para el dashboard, protegidos por sesión activa. */
@RestController
@Tag(name = "Indicadores", description = "Indicadores económicos vigentes para el dashboard.")
@RequestMapping("/api/indicadores")
public class CIndicadores {
    private final SIndicadorTrm trm;
    private final SUsuarioActivo usuarioActivo;

    public CIndicadores(SIndicadorTrm trm, SUsuarioActivo usuarioActivo) {
        this.trm = trm;
        this.usuarioActivo = usuarioActivo;
    }

    /** TRM oficial vigente (USD/COP) con variación e histórico reciente, cacheada en el servidor. */
    @GetMapping("/trm")
    public ResponseEntity<?> trm(HttpServletRequest request) {
        if (usuarioActivo.obtenerUsuario(request).isEmpty()) {
            return ResponseEntity.status(401).body(RespuestaModalDTO.error("Inicia sesión para continuar.", null));
        }
        IndicadorTrmDTO dato = trm.obtener();
        if (dato.valor() == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(RespuestaModalDTO.error("No hay datos disponibles de la TRM en este momento.", null));
        }
        return ResponseEntity.ok(RespuestaModalDTO.exito("TRM vigente.", dato));
    }
}
