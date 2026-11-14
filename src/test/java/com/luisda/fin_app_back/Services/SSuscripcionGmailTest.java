package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MSuscripcion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import com.luisda.fin_app_back.Repository.ISuscripcion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SSuscripcionGmailTest {

    private ISuscripcion repositorio;
    private SSuscripcionGmail servicio;
    private MFuente cuenta;

    @BeforeEach
    void preparar() {
        repositorio = mock(ISuscripcion.class);
        servicio = new SSuscripcionGmail(repositorio);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("usuario-1");
        cuenta = new MFuente("fuente-1", "CORREO", "correo@gmail.com", true, java.time.LocalDate.now());
        cuenta.setUsuario(usuario);
        when(repositorio.save(any(MSuscripcion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void guardaLaSuscripcionDetectadaEnElCorreo() throws Exception {
        when(repositorio.findByCuenta_IdFuenteAndCorreoRemitente("fuente-1", "remitente@promo.com"))
                .thenReturn(Optional.empty());

        servicio.guardar(cuenta, "Promociones <remitente@promo.com>", "<https://baja.example.com/x>",
                "List-Unsubscribe=One-Click", List.of("CATEGORY_PROMOTIONS"), Instant.now());

        org.mockito.ArgumentCaptor<MSuscripcion> captor = org.mockito.ArgumentCaptor.forClass(MSuscripcion.class);
        verify(repositorio).save(captor.capture());
        MSuscripcion guardada = captor.getValue();
        assertEquals(OrigenSuscripcion.GMAIL, guardada.getOrigen());
        assertEquals("remitente@promo.com", guardada.getCorreoRemitente());
        assertEquals("usuario-1", guardada.getUsuario().getIdUsuario());
        assertTrue(guardada.getCategoriasJson().contains("CATEGORY_PROMOTIONS"));
    }

    @Test
    void actualizaLaSuscripcionCuandoElRemitenteYaExistia() throws Exception {
        MSuscripcion existente = new MSuscripcion();
        existente.setId("gmail-1");
        existente.setUsuario(cuenta.getUsuario());
        existente.setCuenta(cuenta);
        existente.setOrigen(OrigenSuscripcion.GMAIL);
        existente.setCorreoRemitente("remitente@promo.com");
        existente.setUltimaActividad(Instant.now().minusSeconds(60));
        when(repositorio.findByCuenta_IdFuenteAndCorreoRemitente("fuente-1", "remitente@promo.com"))
                .thenReturn(Optional.of(existente));

        servicio.guardar(cuenta, "Promociones <remitente@promo.com>", "<https://baja.example.com/y>",
                null, List.of("CATEGORY_UPDATES"), Instant.now());

        org.mockito.ArgumentCaptor<MSuscripcion> captor = org.mockito.ArgumentCaptor.forClass(MSuscripcion.class);
        verify(repositorio).save(captor.capture());
        assertEquals("gmail-1", captor.getValue().getId());
        assertTrue(captor.getValue().getCategoriasJson().contains("CATEGORY_UPDATES"));
    }

    @Test
    void ignoraCorreosSinBajaOficial() throws Exception {
        servicio.guardar(cuenta, "Remitente <remitente@promo.com>", "", null,
                List.of("CATEGORY_PROMOTIONS"), Instant.now());

        verify(repositorio, never()).save(any(MSuscripcion.class));
    }
}
