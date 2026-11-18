package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Services.SPapeleraGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * @uso               Expone la Papelera de correos de una cuenta Gmail
 * @capa              Controlador
 * @responsabilidades Sesión, propiedad de la cuenta y traducción de errores de la Papelera a
 *                    `{ "message": … }` + flag (`requires_reconnect` / `requires_scope_authorization` /
 *                    `mail_unavailable`)
 * @datos             Peticiones HTTP bajo `/api/gmail/cuentas/{cuentaId}` (papelera, mover y restaurar)
 *                    y DTOs de papelera
 * @dependencias      IFuentes, SPapeleraGmail, SUsuarioActivo
 * @usadoPor          Frontend de correos (vista Papelera y detalle del correo)
 */
@RestController
@RequestMapping("/api/gmail/cuentas/{cuentaId}")
@Tag(name = "Papelera Gmail", description = "Papelera de correos de Gmail")
public class CPapeleraGmail {

    private final IFuentes fuentes;
    private final SPapeleraGmail papelera;
    private final SUsuarioActivo usuarioActivo;

    public CPapeleraGmail(IFuentes fuentes, SPapeleraGmail papelera, SUsuarioActivo usuarioActivo) {
        this.fuentes = fuentes;
        this.papelera = papelera;
        this.usuarioActivo = usuarioActivo;
    }

    /** Lista en vivo los correos en TRASH de la cuenta indicada, con paginación por `page_token`. */
    @GetMapping("/papelera")
    public ResponseEntity<?> listar(@PathVariable String cuentaId,
                                    @RequestParam(value = "page_token", required = false) String pageToken,
                                    HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            return ResponseEntity.ok(papelera.listar(resolucion.cuenta(), pageToken));
        } catch (SPapeleraGmail.CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (RestClientResponseException error) {
            return respuestaFalloGmail(error, "No se pudo consultar la papelera de Gmail.");
        } catch (IOException | GeneralSecurityException error) {
            return respuestaRenovacionFallida();
        }
    }

    /** Mueve a la Papelera de Gmail un correo, desde su detalle (RF-01..RF-05). */
    @PostMapping("/mensajes/{mensajeId}/papelera")
    public ResponseEntity<?> mover(@PathVariable String cuentaId, @PathVariable String mensajeId,
                                   HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            return ResponseEntity.ok(papelera.mover(resolucion.cuenta(), mensajeId));
        } catch (SPapeleraGmail.CorreoPurgadoException error) {
            return respuestaPurgado();
        } catch (SPapeleraGmail.CorreoNoDisponibleException error) {
            return respuestaNoDisponible();
        } catch (SPapeleraGmail.CorreoNoEncontradoException error) {
            return respuestaMensajeAjeno();
        } catch (SPapeleraGmail.CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (RestClientResponseException error) {
            return respuestaFalloGmail(error, "No se pudo mover el correo a la papelera de Gmail.");
        } catch (IOException | GeneralSecurityException error) {
            return respuestaRenovacionFallida();
        }
    }

    /** Restaura desde la Papelera de Gmail un correo, desde su fila (RF-13..RF-20). */
    @PostMapping("/mensajes/{mensajeId}/restaurar")
    public ResponseEntity<?> restaurar(@PathVariable String cuentaId, @PathVariable String mensajeId,
                                       HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            return ResponseEntity.ok(papelera.restaurar(resolucion.cuenta(), mensajeId));
        } catch (SPapeleraGmail.CorreoPurgadoException error) {
            return respuestaPurgado();
        } catch (SPapeleraGmail.CorreoNoDisponibleException error) {
            return respuestaNoDisponible();
        } catch (SPapeleraGmail.CorreoNoEncontradoException error) {
            return respuestaMensajeAjeno();
        } catch (SPapeleraGmail.CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (RestClientResponseException error) {
            return respuestaFalloGmail(error, "No se pudo restaurar el correo desde la papelera de Gmail.");
        } catch (IOException | GeneralSecurityException error) {
            return respuestaRenovacionFallida();
        }
    }

    /** Sesión activa, cuenta Gmail propia conectada y permiso ampliado concedido. */
    private Resolucion resolverCuenta(HttpServletRequest request, String cuentaId) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return new Resolucion(null, ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa.")));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return new Resolucion(null, ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No se encontró esa cuenta Gmail conectada.")));
        }
        if (!cuenta.tienePermisoModificarGmail()) return new Resolucion(null, respuestaScopeNoAutorizado());
        return new Resolucion(cuenta, null);
    }

    private ResponseEntity<Map<String, Object>> respuestaPurgado() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "message", "Este correo ya fue eliminado definitivamente por Gmail y no se puede recuperar."
        ));
    }

    private ResponseEntity<Map<String, Object>> respuestaNoDisponible() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "mail_unavailable", true,
                "message", "Este correo ya no está disponible en la papelera."
        ));
    }

    private ResponseEntity<Map<String, Object>> respuestaMensajeAjeno() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", "Ese correo no pertenece a esta cuenta Gmail."
        ));
    }

    private ResponseEntity<Map<String, Object>> respuestaFalloGmail(RestClientResponseException error, String predeterminado) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", mensajeErrorGoogle(error, predeterminado)));
    }

    private ResponseEntity<Map<String, Object>> respuestaRenovacionFallida() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "No se pudo renovar la conexión de Gmail."));
    }

    private ResponseEntity<Map<String, Object>> respuestaReconexionRequerida() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "requires_reconnect", true,
                "message", "La autorización de esta cuenta Gmail ya no se puede renovar. Vuelve a conectarla desde Configuración."
        ));
    }

    /** 409 cuando Google no concedió el permiso ampliado (RF-23/RF-24), distinto de la reconexión. */
    private ResponseEntity<Map<String, Object>> respuestaScopeNoAutorizado() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "requires_scope_authorization", true,
                "message", "FinApp necesita permiso para modificar tus correos (además de leerlos) en esta cuenta. "
                        + "Autoriza el permiso ampliado para usar la Papelera."
        ));
    }

    private String mensajeErrorGoogle(RestClientResponseException error, String predeterminado) {
        if (error.getStatusCode().value() == 401) return "La autorización de Gmail venció. Conecta Gmail de nuevo.";
        if (error.getStatusCode().value() == 429) return "Google limitó temporalmente las consultas. Espera un momento y vuelve a intentar.";
        return error.getStatusCode().value() == 403
                ? "Google denegó el permiso solicitado. Revisa los permisos concedidos y la configuración de la API."
                : predeterminado;
    }

    /** Cuenta resuelta o la respuesta de error que debe devolverse tal cual. */
    private record Resolucion(MFuente cuenta, ResponseEntity<?> error) { }
}
