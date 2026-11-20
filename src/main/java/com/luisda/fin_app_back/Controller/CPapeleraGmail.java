package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GmailListaBlancaDTO;
import com.luisda.fin_app_back.DTO.GmailPapeleraConfiguracionDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Services.SPapeleraGmail;
import com.luisda.fin_app_back.Services.SReglaPapeleraGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * @uso               Expone la Papelera de correos de una cuenta Gmail
 * @capa              Controlador
 * @responsabilidades Sesión, propiedad de la cuenta y traducción de errores de la Papelera a
 *                    `{ "message": … }` + flag (`requires_reconnect` / `requires_scope_authorization` /
 *                    `mail_unavailable`), además de la configuración por categoría y la lista blanca
 * @datos             Peticiones HTTP bajo `/api/gmail/cuentas/{cuentaId}` (papelera, mover, restaurar,
 *                    configuración y lista blanca) y DTOs de papelera
 * @dependencias      IFuentes, SPapeleraGmail, SUsuarioActivo
 * @usadoPor          Frontend de correos (vista Papelera y detalle del correo)
 */
@RestController
@RequestMapping("/api/gmail/cuentas/{cuentaId}")
@Tag(name = "Papelera Gmail", description = "Papelera de correos de Gmail")
public class CPapeleraGmail {

    private final IFuentes fuentes;
    private final SPapeleraGmail papelera;
    private final SReglaPapeleraGmail regla;
    private final SUsuarioActivo usuarioActivo;

    public CPapeleraGmail(IFuentes fuentes, SPapeleraGmail papelera, SReglaPapeleraGmail regla,
                          SUsuarioActivo usuarioActivo) {
        this.fuentes = fuentes;
        this.papelera = papelera;
        this.regla = regla;
        this.usuarioActivo = usuarioActivo;
    }

    /** Lista en vivo los correos en TRASH de la cuenta indicada, con paginación por `page_token`. */
    @GetMapping("/papelera")
    @Operation(summary = "Lista la papelera en vivo",
            description = "Consulta Gmail en vivo (messages.list?q=in:trash) y devuelve los correos en la "
                    + "Papelera de la cuenta, incluidos los nunca sincronizados. Pagina con el parámetro "
                    + "page_token y responde siguiente_page_token. Una papelera vacía responde 200 con "
                    + "correos: []; un fallo de Gmail responde 502/503 y no se presenta como papelera vacía. "
                    + "Requiere el permiso ampliado (409 requires_scope_authorization si falta).")
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
    @Operation(summary = "Mueve un correo a la papelera",
            description = "Mueve a la Papelera de Gmail el correo indicado (messages.modify addLabelIds:[TRASH]); "
                    + "es la única vía manual y opera sobre la cuenta de la ruta. Es idempotente: repetirlo sobre "
                    + "un correo ya en papelera responde 200 sin efectos adicionales. Un correo purgado responde "
                    + "410; uno ya no disponible, 409 mail_unavailable; cuenta ajena, 404; sin sesión, 401. "
                    + "Requiere el permiso ampliado (409 requires_scope_authorization si falta).")
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
    @Operation(summary = "Restaura un correo desde la papelera",
            description = "Devuelve a la bandeja de Gmail un correo que está en la Papelera (messages.modify "
                    + "removeLabelIds:[TRASH]) y lo exime de la regla automática. Es idempotente: repetirlo sobre "
                    + "un correo ya en bandeja responde 200 sin efectos adicionales. Un correo purgado responde "
                    + "410; uno ya no disponible, 409 mail_unavailable; cuenta ajena, 404; sin sesión, 401. "
                    + "Requiere el permiso ampliado (409 requires_scope_authorization si falta).")
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

    /** Lee la configuración por categoría de la cuenta (RF-33). No exige el permiso ampliado. */
    @GetMapping("/papelera/configuracion")
    @Operation(summary = "Lee la configuración de la papelera",
            description = "Devuelve las cinco categorías (PROMOTIONS, SOCIAL, FORUMS, UPDATES con "
                    + "requiere_advertencia: true, SPAM), la antigüedad mínima, el límite por ejecución y "
                    + "scope_autorizado de la cuenta. No exige el permiso ampliado.")
    public ResponseEntity<?> configuracion(@PathVariable String cuentaId, HttpServletRequest request) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        return ResponseEntity.ok(papelera.configuracion(resolucion.cuenta()));
    }

    /** Actualiza la configuración por categoría de la cuenta (RF-33/RF-34/RF-37). */
    @PutMapping("/papelera/configuracion")
    @Operation(summary = "Actualiza la configuración de la papelera",
            description = "Actualiza las categorías activas, la antigüedad mínima, el límite por ejecución y la "
                    + "aceptación de advertencias. Activar una categoría solo afecta a los correos recibidos desde "
                    + "ese momento. advertencia_aceptada es obligatoria (true) cuando categorias_activas incluye "
                    + "CATEGORY_UPDATES; si falta o es false, responde 400. Valores inválidos responden 400 y "
                    + "activar categorías sin el permiso ampliado responde 409 requires_scope_authorization.")
    public ResponseEntity<?> actualizarConfiguracion(@PathVariable String cuentaId,
                                                     @RequestBody(required = false) GmailPapeleraConfiguracionDTO.Solicitud solicitud,
                                                     HttpServletRequest request) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            return ResponseEntity.ok(papelera.actualizarConfiguracion(resolucion.cuenta(), solicitud));
        } catch (SPapeleraGmail.RequiereScopeException error) {
            return respuestaScopeNoAutorizado();
        } catch (IllegalArgumentException error) {
            return respuestaSolicitudInvalida(error.getMessage());
        }
    }

    /** Lista los remitentes exentos de la regla automática (RF-36). No exige el permiso ampliado. */
    @GetMapping("/papelera/lista-blanca")
    @Operation(summary = "Lista la lista blanca de remitentes",
            description = "Devuelve los remitentes exentos de la regla automática de la cuenta. No exige el "
                    + "permiso ampliado.")
    public ResponseEntity<?> listarListaBlanca(@PathVariable String cuentaId, HttpServletRequest request) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        return ResponseEntity.ok(papelera.listarListaBlanca(resolucion.cuenta()));
    }

    /** Añade un remitente a la lista blanca de la cuenta (RF-36). */
    @PostMapping("/papelera/lista-blanca")
    @Operation(summary = "Añade un remitente a la lista blanca",
            description = "Añade un remitente que nunca se moverá por la regla automática. Responde 201 con el "
                    + "remitente creado; un remitente inválido responde 400. No exige el permiso ampliado.")
    public ResponseEntity<?> agregarRemitente(@PathVariable String cuentaId,
                                              @RequestBody(required = false) GmailListaBlancaDTO.Solicitud solicitud,
                                              HttpServletRequest request) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            GmailListaBlancaDTO.Remitente creado = papelera.agregarRemitente(resolucion.cuenta(),
                    solicitud == null ? null : solicitud.remitente());
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (IllegalArgumentException error) {
            return respuestaSolicitudInvalida(error.getMessage());
        }
    }

    /** Quita un remitente de la lista blanca de la cuenta (RF-36). */
    @DeleteMapping("/papelera/lista-blanca/{remitenteId}")
    @Operation(summary = "Quita un remitente de la lista blanca",
            description = "Elimina de la lista blanca el remitente indicado. Responde 204 sin cuerpo; si el "
                    + "remitente no está en la lista, 404. No exige el permiso ampliado.")
    public ResponseEntity<?> eliminarRemitente(@PathVariable String cuentaId, @PathVariable String remitenteId,
                                               HttpServletRequest request) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            papelera.eliminarRemitente(resolucion.cuenta(), remitenteId);
            return ResponseEntity.noContent().build();
        } catch (SPapeleraGmail.RemitenteNoEncontradoException error) {
            return respuestaRemitenteAusente();
        }
    }

    /** Vista previa de la limpieza explícita: elegibles, remitentes por categoría y excluidos (RF-39). */
    @PostMapping("/papelera/vista-previa")
    @Operation(summary = "Genera la vista previa de la limpieza",
            description = "Aplica todas las exclusiones y la lista blanca y devuelve cuántos correos se moverían, "
                    + "una muestra de remitentes agrupados por categoría y los excluidos por motivo. Fija los "
                    + "parámetros evaluados y el total_estimado en un vista_previa_id. Es obligatoria antes de la "
                    + "limpieza explícita e informativa antes de activar una categoría. Requiere el permiso "
                    + "ampliado (409 requires_scope_authorization); con cuota agotada responde 429.")
    public ResponseEntity<?> vistaPrevia(@PathVariable String cuentaId,
                                         @RequestBody(required = false) VistaPreviaPapeleraDTO solicitud,
                                         HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        try {
            return ResponseEntity.ok(regla.vistaPrevia(resolucion.cuenta(), solicitud));
        } catch (IllegalArgumentException error) {
            return respuestaSolicitudInvalida(error.getMessage());
        } catch (SPapeleraGmail.CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (RestClientResponseException error) {
            return respuestaFalloGmail(error, "No se pudo evaluar la vista previa de la papelera.");
        } catch (IOException | GeneralSecurityException error) {
            return respuestaRenovacionFallida();
        }
    }

    /** Inicia la limpieza explícita de correos ya recibidos, tras confirmación (RF-37/RF-38). */
    @PostMapping("/papelera/limpieza")
    @Operation(summary = "Inicia la limpieza explícita en lote",
            description = "Mueve los correos ya recibidos según la vista previa referenciada, tras confirmación. "
                    + "Exige vista_previa_id, total_estimado y confirmado=true (si no, 400); un vista_previa_id "
                    + "inexistente o expirado responde 409. Responde 202 con lote_id y estado EN_PROCESO. Solo una "
                    + "limpieza por cuenta (409 limpieza_en_curso); si el conteo real difiere del confirmado, 409 "
                    + "conteo_cambiado; si se alcanza el límite con remanente, 409 limite_ejecucion_alcanzado; "
                    + "requiere el permiso ampliado (409 requires_scope_authorization) y con cuota agotada, 429.")
    public ResponseEntity<?> iniciarLimpieza(@PathVariable String cuentaId,
                                             @RequestBody(required = false) LimpiezaPapeleraDTO solicitud,
                                             HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        if (solicitud == null || solicitud.vistaPreviaId() == null || solicitud.vistaPreviaId().isBlank()
                || solicitud.totalEstimado() == null || !Boolean.TRUE.equals(solicitud.confirmado())) {
            return respuestaSolicitudInvalida(
                    "La limpieza exige vista_previa_id, total_estimado y confirmado=true.");
        }
        try {
            return ResponseEntity.accepted().body(regla.iniciarLimpieza(resolucion.cuenta(), solicitud));
        } catch (SReglaPapeleraGmail.VistaPreviaNoEncontradaException error) {
            return respuestaVistaPreviaExpirada();
        } catch (SReglaPapeleraGmail.LimpiezaEnCursoException error) {
            return respuestaLimpiezaEnCurso();
        } catch (SReglaPapeleraGmail.ConteoCambiadoException error) {
            return respuestaConteoCambiado(error.getNuevoConteo(), solicitud.totalEstimado());
        } catch (SPapeleraGmail.CuentaGmailRequiereReconectarException error) {
            return respuestaReconexionRequerida();
        } catch (RejectedExecutionException error) {
            return respuestaColaOcupada();
        } catch (RestClientResponseException error) {
            return respuestaFalloGmail(error, "No se pudo iniciar la limpieza de la papelera.");
        } catch (IOException | GeneralSecurityException error) {
            return respuestaRenovacionFallida();
        }
    }

    /** Estado de un lote de limpieza de la cuenta (RF-38/CD-04). */
    @GetMapping("/papelera/limpieza/{loteId}")
    @Operation(summary = "Consulta el estado de una limpieza",
            description = "Devuelve el estado del lote de limpieza (EN_PROCESO, COMPLETADO o FALLIDO) y los "
                    + "conteos total, movidos, omitidos y fallidos. Responde 404 si el lote no existe o no es de "
                    + "la cuenta. Requiere el permiso ampliado (409 requires_scope_authorization si falta).")
    public ResponseEntity<?> consultarLimpieza(@PathVariable String cuentaId, @PathVariable String loteId,
                                               HttpServletRequest request) {
        Resolucion resolucion = resolverCuenta(request, cuentaId);
        if (resolucion.error() != null) return resolucion.error();
        return regla.consultarLimpieza(resolucion.cuenta(), loteId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(this::respuestaLoteNoEncontrado);
    }

    /** Sesión activa, cuenta Gmail propia conectada y permiso ampliado concedido. */
    private Resolucion resolverCuenta(HttpServletRequest request, String cuentaId) {
        Resolucion resolucion = resolverCuentaSinPermiso(request, cuentaId);
        if (resolucion.error() != null) return resolucion;
        if (!resolucion.cuenta().tienePermisoModificarGmail()) return new Resolucion(null, respuestaScopeNoAutorizado());
        return resolucion;
    }

    /** Sesión activa y cuenta Gmail propia conectada, sin exigir el permiso ampliado. */
    private Resolucion resolverCuentaSinPermiso(HttpServletRequest request, String cuentaId) {
        var usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return new Resolucion(null, ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa.")));
        MFuente cuenta = fuentes.findByIdFuenteAndUsuario_IdUsuario(cuentaId, usuario.getIdUsuario()).orElse(null);
        if (cuenta == null || !"CORREO".equalsIgnoreCase(cuenta.getTipo()) || !cuenta.tieneConexionGmail()) {
            return new Resolucion(null, ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No se encontró esa cuenta Gmail conectada.")));
        }
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

    /** 400 cuando la solicitud de configuración o lista blanca trae valores no válidos. */
    private ResponseEntity<Map<String, Object>> respuestaSolicitudInvalida(String mensaje) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", mensaje == null || mensaje.isBlank() ? "La solicitud enviada no es válida." : mensaje
        ));
    }

    private ResponseEntity<Map<String, Object>> respuestaRemitenteAusente() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", "Ese remitente no está en la lista blanca de la cuenta."
        ));
    }

    private ResponseEntity<Map<String, Object>> respuestaFalloGmail(RestClientResponseException error, String predeterminado) {
        HttpStatus estado = error.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(estado).body(Map.of("message", mensajeErrorGoogle(error, predeterminado)));
    }

    /** 409 cuando el `vista_previa_id` de la limpieza no existe o expiró (RF-39). */
    private ResponseEntity<Map<String, Object>> respuestaVistaPreviaExpirada() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "La vista previa ya no está vigente. Genera una nueva antes de limpiar."
        ));
    }

    /** 409 cuando ya hay una limpieza en curso para la cuenta (RF-38). */
    private ResponseEntity<Map<String, Object>> respuestaLimpiezaEnCurso() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "limpieza_en_curso", true,
                "message", "Ya hay una limpieza en curso para esta cuenta. Espera a que termine."
        ));
    }

    /** 409 cuando el conteo real difiere del confirmado; exige una nueva vista previa (RF-38). */
    private ResponseEntity<Map<String, Object>> respuestaConteoCambiado(int nuevoConteo, Integer totalEstimado) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "conteo_cambiado", true,
                "nuevo_conteo", nuevoConteo,
                "total_estimado", totalEstimado,
                "message", "El número de correos cambió desde la vista previa. Genera una nueva y confírmala."
        ));
    }

    /** 404 cuando el lote de limpieza no existe o no es de la cuenta. */
    private ResponseEntity<Map<String, Object>> respuestaLoteNoEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", "No se encontró ese lote de limpieza para esta cuenta."
        ));
    }

    /** 503 cuando la cola de limpiezas está ocupada y no se pudo encolar el lote. */
    private ResponseEntity<Map<String, Object>> respuestaColaOcupada() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "La cola de limpiezas está ocupada. Espera un momento y vuelve a intentar."
        ));
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
