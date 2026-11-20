package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.LimpiezaPapeleraDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraEstadoDTO;
import com.luisda.fin_app_back.DTO.LimpiezaPapeleraInicioDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraDTO;
import com.luisda.fin_app_back.DTO.VistaPreviaPapeleraRespuestaDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Services.SPapeleraGmail;
import com.luisda.fin_app_back.Services.SReglaPapeleraGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-37/RF-38/RF-39 — endpoints de vista previa y limpieza explícita: sesión/ownership, permiso
 * ampliado, validación de la confirmación (`400`), vista previa inexistente (`409`),
 * `conteo_cambiado` (`409`), `limpieza_en_curso` (`409`), cuota agotada (`429`), cola ocupada
 * (`503`) y seguimiento (`200`/`404`).
 */
class CPapeleraGmailLimpiezaTest {

    private IFuentes fuentes;
    private SPapeleraGmail papelera;
    private SReglaPapeleraGmail regla;
    private SUsuarioActivo usuarioActivo;
    private CPapeleraGmail controlador;
    private MFuente cuenta;

    @BeforeEach
    void preparar() {
        fuentes = mock(IFuentes.class);
        papelera = mock(SPapeleraGmail.class);
        regla = mock(SReglaPapeleraGmail.class);
        usuarioActivo = mock(SUsuarioActivo.class);
        controlador = new CPapeleraGmail(fuentes, papelera, regla, usuarioActivo);

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));

        cuenta = new MFuente("cuenta-1", "CORREO", "usuario@gmail.com", true, LocalDate.now());
        cuenta.setAccessTokenCifrado("token-cifrado");
        cuenta.setExpiraEn(Instant.now().plusSeconds(3600));
        cuenta.setGmailScopeConcedido(MFuente.ALCANCE_GMAIL_MODIFICAR);
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.of(cuenta));
    }

    private static VistaPreviaPapeleraRespuestaDTO vistaPrevia() {
        return new VistaPreviaPapeleraRespuestaDTO("vp-1", 2,
                List.of(new VistaPreviaPapeleraRespuestaDTO.CategoriaVistaPrevia("CATEGORY_PROMOTIONS", 2,
                        List.of(new VistaPreviaPapeleraRespuestaDTO.RemitenteVistaPrevia("promo@tienda.com", 2)))),
                List.of(new VistaPreviaPapeleraRespuestaDTO.ExclusionPorMotivo("STARRED", 1)));
    }

    @Test
    void sinSesionVistaPreviaResponde401() {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1",
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7), mock(HttpServletRequest.class));

        assertEquals(401, respuesta.getStatusCode().value());
    }

    @Test
    void cuentaAjenaVistaPreviaResponde404() {
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1",
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7), mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void vistaPreviaSinPermisoAmpliadoResponde409RequiresScopeAuthorization() {
        cuenta.setGmailScopeConcedido("https://www.googleapis.com/auth/gmail.readonly");

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1",
                new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7), mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("requires_scope_authorization"));
        assertFalse(cuerpo.containsKey("requires_reconnect"));
    }

    @Test
    void vistaPreviaResponde200ConElDto() throws Exception {
        VistaPreviaPapeleraDTO solicitud = new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7);
        when(regla.vistaPrevia(cuenta, solicitud)).thenReturn(vistaPrevia());

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        VistaPreviaPapeleraRespuestaDTO cuerpo = (VistaPreviaPapeleraRespuestaDTO) respuesta.getBody();
        assertEquals("vp-1", cuerpo.vistaPreviaId());
        assertEquals(2, cuerpo.totalEstimado());
    }

    @Test
    void vistaPreviaConCategoriaInvalidaResponde400() throws Exception {
        VistaPreviaPapeleraDTO solicitud = new VistaPreviaPapeleraDTO(List.of("CATEGORY_INVENTADA"), 7);
        when(regla.vistaPrevia(cuenta, solicitud))
                .thenThrow(new IllegalArgumentException("Categoría de Gmail no válida."));

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
    }

    @Test
    void vistaPreviaConCuotaAgotadaResponde429() throws Exception {
        VistaPreviaPapeleraDTO solicitud = new VistaPreviaPapeleraDTO(List.of("CATEGORY_PROMOTIONS"), 7);
        when(regla.vistaPrevia(cuenta, solicitud))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        ResponseEntity<?> respuesta = controlador.vistaPrevia("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(429, respuesta.getStatusCode().value());
        assertTrue(((Map<?, ?>) respuesta.getBody()).containsKey("message"));
    }

    @Test
    void limpiezaSinVistaPreviaIdResponde400YNoMueveNada() throws Exception {
        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1",
                new LimpiezaPapeleraDTO(null, 5, true), mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        verify(regla, never()).iniciarLimpieza(any(), any());
    }

    @Test
    void limpiezaSinTotalEstimadoResponde400() throws Exception {
        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1",
                new LimpiezaPapeleraDTO("vp-1", null, true), mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        verify(regla, never()).iniciarLimpieza(any(), any());
    }

    @Test
    void limpiezaSinConfirmarResponde400() throws Exception {
        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1",
                new LimpiezaPapeleraDTO("vp-1", 5, false), mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        verify(regla, never()).iniciarLimpieza(any(), any());
    }

    @Test
    void limpiezaSinCuerpoResponde400() throws Exception {
        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", null, mock(HttpServletRequest.class));

        assertEquals(400, respuesta.getStatusCode().value());
        verify(regla, never()).iniciarLimpieza(any(), any());
    }

    @Test
    void limpiezaConVistaPreviaInexistenteResponde409() throws Exception {
        LimpiezaPapeleraDTO solicitud = new LimpiezaPapeleraDTO("vp-x", 5, true);
        when(regla.iniciarLimpieza(cuenta, solicitud))
                .thenThrow(new SReglaPapeleraGmail.VistaPreviaNoEncontradaException());

        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertFalse(cuerpo.containsKey("conteo_cambiado"));
    }

    @Test
    void unaSegundaLimpiezaSimultaneaResponde409LimpiezaEnCurso() throws Exception {
        LimpiezaPapeleraDTO solicitud = new LimpiezaPapeleraDTO("vp-1", 5, true);
        when(regla.iniciarLimpieza(cuenta, solicitud)).thenThrow(new SReglaPapeleraGmail.LimpiezaEnCursoException());

        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("limpieza_en_curso"));
    }

    @Test
    void unConteoDistintoResponde409ConteoCambiadoConElNuevoConteo() throws Exception {
        LimpiezaPapeleraDTO solicitud = new LimpiezaPapeleraDTO("vp-1", 5, true);
        when(regla.iniciarLimpieza(cuenta, solicitud)).thenThrow(new SReglaPapeleraGmail.ConteoCambiadoException(3));

        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(409, respuesta.getStatusCode().value());
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertEquals(true, cuerpo.get("conteo_cambiado"));
        assertEquals(3, cuerpo.get("nuevo_conteo"));
        assertEquals(5, cuerpo.get("total_estimado"));
    }

    @Test
    void unaLimpiezaValidaResponde202ConElLote() throws Exception {
        LimpiezaPapeleraDTO solicitud = new LimpiezaPapeleraDTO("vp-1", 2, true);
        when(regla.iniciarLimpieza(cuenta, solicitud))
                .thenReturn(new LimpiezaPapeleraInicioDTO("lote-1", "EN_PROCESO"));

        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(202, respuesta.getStatusCode().value());
        LimpiezaPapeleraInicioDTO cuerpo = (LimpiezaPapeleraInicioDTO) respuesta.getBody();
        assertEquals("lote-1", cuerpo.loteId());
        assertEquals("EN_PROCESO", cuerpo.estado());
    }

    @Test
    void unaColaOcupadaResponde503() throws Exception {
        LimpiezaPapeleraDTO solicitud = new LimpiezaPapeleraDTO("vp-1", 2, true);
        when(regla.iniciarLimpieza(cuenta, solicitud)).thenThrow(new RejectedExecutionException());

        ResponseEntity<?> respuesta = controlador.iniciarLimpieza("cuenta-1", solicitud, mock(HttpServletRequest.class));

        assertEquals(503, respuesta.getStatusCode().value());
        assertTrue(((Map<?, ?>) respuesta.getBody()).containsKey("message"));
    }

    @Test
    void elSeguimientoDeUnLoteDesconocidoResponde404() {
        when(regla.consultarLimpieza(cuenta, "lote-x")).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controlador.consultarLimpieza("cuenta-1", "lote-x", mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
    }

    @Test
    void elSeguimientoDeUnLoteResponde200ConLosConteos() {
        when(regla.consultarLimpieza(cuenta, "lote-1")).thenReturn(Optional.of(
                new LimpiezaPapeleraEstadoDTO("lote-1", "COMPLETADO", 28, 27, 1, 0, false)));

        ResponseEntity<?> respuesta = controlador.consultarLimpieza("cuenta-1", "lote-1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        LimpiezaPapeleraEstadoDTO cuerpo = (LimpiezaPapeleraEstadoDTO) respuesta.getBody();
        assertEquals("COMPLETADO", cuerpo.estado());
        assertEquals(27, cuerpo.movidos());
        assertEquals(1, cuerpo.omitidos());
        assertEquals(0, cuerpo.fallidos());
    }

    @Test
    void sinSesionElSeguimientoResponde401() {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        assertEquals(401, controlador.consultarLimpieza("cuenta-1", "lote-1", mock(HttpServletRequest.class))
                .getStatusCode().value());
        verify(regla, never()).consultarLimpieza(any(), anyString());
    }
}
