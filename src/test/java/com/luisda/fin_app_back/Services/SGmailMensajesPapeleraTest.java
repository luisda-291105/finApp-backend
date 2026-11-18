package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RF-27/RF-28/RF-29 — un correo en el TRASH de Gmail no se vuelve a guardar ni reaparece en la
 * bandeja, y el descarte de la alerta de movimiento es persistente sin tocar los movimientos
 * contables ya registrados.
 */
class SGmailMensajesPapeleraTest {

    private IFuentes fuentes;
    private IGmailMensajes mensajes;
    private SGmailMensajes servicio;
    private MockRestServiceServer servidor;
    private MFuente cuenta;

    @BeforeEach
    void preparar() throws Exception {
        fuentes = mock(IFuentes.class);
        mensajes = mock(IGmailMensajes.class);
        servicio = new SGmailMensajes(fuentes, mensajes, mock(SSuscripcionGmail.class));

        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        inyectar("clienteHttp", constructor.build());
        // Credenciales en configuración para no depender del archivo privado real.
        inyectar("clientId", "cliente");
        inyectar("clientSecret", "secreto");

        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        cuenta = new MFuente("cuenta-1", "CORREO", "usuario@gmail.com", true, LocalDate.now());
        cuenta.setUsuario(usuario);

        when(fuentes.existsById("cuenta-1")).thenReturn(true);
    }

    @Test
    void unCorreoEnTrashNoSeGuardaYSeEliminaDeLaCache() throws Exception {
        servidor.expect(requestTo(containsString("/messages/m1"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mensaje("m1", Instant.now().toEpochMilli(), "[\"IMPORTANT\",\"TRASH\"]"),
                        MediaType.APPLICATION_JSON));

        servicio.guardarMensaje(cuenta, "token", "m1");

        verify(mensajes, never()).save(any(MGmailMensaje.class));
        verify(mensajes).deleteByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1");
        servidor.verify();
    }

    @Test
    void unCorreoEnLaBandejaSeSigueGuardando() throws Exception {
        servidor.expect(requestTo(containsString("/messages/m2"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mensaje("m2", Instant.now().toEpochMilli(), "[\"IMPORTANT\",\"INBOX\"]"),
                        MediaType.APPLICATION_JSON));
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m2")).thenReturn(Optional.empty());
        when(mensajes.save(any(MGmailMensaje.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.guardarMensaje(cuenta, "token", "m2");

        verify(mensajes).save(any(MGmailMensaje.class));
        servidor.verify();
    }

    @Test
    void descartarLaAlertaEsPersistenteYSinTocarElMovimientoContable() {
        MGmailMensaje entidad = new MGmailMensaje("fila-1", cuenta, "m1", Instant.now());
        entidad.setMovimientoAutomaticoEstado("REGISTRADO");
        entidad.setMovimientoAutomaticoId("mov-1");
        entidad.setMovimientoAlertaPendiente(true);
        when(mensajes.findByCuenta_IdFuenteAndGoogleMessageId("cuenta-1", "m1")).thenReturn(Optional.of(entidad));
        when(mensajes.save(any(MGmailMensaje.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.descartarAlertaMovimiento(cuenta, "m1");

        assertTrue(entidad.isAlertaMovimientoDescartada());
        assertFalse(entidad.isMovimientoAlertaPendiente());
        // El movimiento contable ya registrado permanece intacto (RF-27).
        assertEquals("mov-1", entidad.getMovimientoAutomaticoId());
        assertEquals("REGISTRADO", entidad.getMovimientoAutomaticoEstado());
        verify(mensajes).save(entidad);
    }

    private static String mensaje(String id, long internalDate, String labelIds) {
        return "{\"id\":\"" + id + "\",\"internalDate\":\"" + internalDate + "\",\"labelIds\":" + labelIds
                + ",\"snippet\":\"Hola\",\"payload\":{\"mimeType\":\"text/plain\",\"body\":{\"data\":\"\"},"
                + "\"headers\":[{\"name\":\"From\",\"value\":\"promo@tienda.com\"},"
                + "{\"name\":\"Subject\",\"value\":\"Oferta\"},{\"name\":\"To\",\"value\":\"usuario@gmail.com\"}]}}";
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = SGmailMensajes.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(servicio, valor);
    }
}
