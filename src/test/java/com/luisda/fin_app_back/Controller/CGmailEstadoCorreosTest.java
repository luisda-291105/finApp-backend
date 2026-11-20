package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Services.SClasificadorMovimiento;
import com.luisda.fin_app_back.Services.SGmailEntradas;
import com.luisda.fin_app_back.Services.SGmailMensajes;
import com.luisda.fin_app_back.Services.SImportacionLoteGmail;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SSuscripcionGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CGmailEstadoCorreosTest {

    private CGmail construido(IFuentes fuentes, SGmailEntradas entradas, SUsuarioActivo usuarioActivo) {
        return new CGmail(fuentes, mock(IGmailMensajes.class), mock(SSuscripcionGmail.class), entradas,
                mock(SGmailMensajes.class), usuarioActivo, mock(SMovimientoContable.class),
                mock(SClasificadorMovimiento.class), mock(SImportacionLoteGmail.class));
    }

    private MFuente cuentaConectada() {
        MFuente cuenta = new MFuente();
        cuenta.setIdFuente("cuenta-1");
        cuenta.setTipo("CORREO");
        cuenta.setContenido("usuario@gmail.com");
        cuenta.setRefreshTokenCifrado("token-cifrado");
        cuenta.setGmailSincronizadoEn(Instant.parse("2026-11-17T20:00:00Z"));
        cuenta.setGmailWatchExpiraEn(Instant.parse("2026-11-18T20:00:00Z"));
        return cuenta;
    }

    @Test
    @SuppressWarnings("unchecked")
    void devuelveElEstadoDeLaColaYLaSincronizacion() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        SGmailEntradas entradas = mock(SGmailEntradas.class);
        SUsuarioActivo usuarioActivo = mock(SUsuarioActivo.class);
        CGmail gmail = construido(fuentes, entradas, usuarioActivo);
        MFuente cuenta = cuentaConectada();
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.of(cuenta));
        when(entradas.estado("cuenta-1")).thenReturn(new SGmailEntradas.EstadoCola(
                2, 1, 3, "token expirado", Instant.parse("2026-11-17T19:55:00Z")));
        Method metodo = CGmail.class.getDeclaredMethod("consultarEstadoCorreos", String.class, HttpServletRequest.class);
        metodo.setAccessible(true);

        ResponseEntity<Map<String, Object>> respuesta =
                (ResponseEntity<Map<String, Object>>) metodo.invoke(gmail, "cuenta-1", mock(HttpServletRequest.class));

        assertEquals(200, respuesta.getStatusCode().value());
        Map<String, Object> cuerpo = respuesta.getBody();
        assertEquals("cuenta-1", cuerpo.get("cuenta_id"));
        assertEquals("usuario@gmail.com", cuerpo.get("correo"));
        assertEquals(true, cuerpo.get("actualizando"));
        assertEquals(2L, cuerpo.get("en_espera"));
        assertEquals(1L, cuerpo.get("procesando"));
        assertEquals(3L, cuerpo.get("errores"));
        assertEquals("token expirado", cuerpo.get("ultimo_error"));
        assertEquals("2026-11-17T20:00:00Z", cuerpo.get("ultima_sincronizacion"));
        assertEquals("2026-11-18T20:00:00Z", cuerpo.get("watch_expira_en"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void responde404SinConexionGmail() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        SUsuarioActivo usuarioActivo = mock(SUsuarioActivo.class);
        CGmail gmail = construido(fuentes, mock(SGmailEntradas.class), usuarioActivo);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        MFuente cuenta = new MFuente();
        cuenta.setIdFuente("cuenta-1");
        cuenta.setTipo("CORREO");
        when(fuentes.findByIdFuenteAndUsuario_IdUsuario("cuenta-1", "usuario-1")).thenReturn(Optional.of(cuenta));
        Method metodo = CGmail.class.getDeclaredMethod("consultarEstadoCorreos", String.class, HttpServletRequest.class);
        metodo.setAccessible(true);

        ResponseEntity<Map<String, Object>> respuesta =
                (ResponseEntity<Map<String, Object>>) metodo.invoke(gmail, "cuenta-1", mock(HttpServletRequest.class));

        assertEquals(404, respuesta.getStatusCode().value());
        assertTrue(respuesta.getBody().containsKey("message"));
        assertFalse(respuesta.getBody().containsKey("actualizando"));
    }
}
