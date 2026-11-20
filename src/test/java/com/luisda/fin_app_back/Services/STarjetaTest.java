package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.TarjetaSolicitudDTO;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class STarjetaTest {
    private static final String USUARIO = "usuario-1";

    private ITarjeta tarjetas;
    private IUsuario usuarios;
    private STarjeta servicio;

    @BeforeEach
    void preparar() {
        tarjetas = mock(ITarjeta.class);
        usuarios = mock(IUsuario.class);
        servicio = new STarjeta(tarjetas, usuarios, mock(IMovimientoContable.class));
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(mock(MUsuario.class)));
        when(tarjetas.save(any(MTarjeta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(tarjetas.findByIdTarjetaAndUsuario_IdUsuario(any(), any())).thenAnswer(invocacion -> {
            MTarjeta existente = new MTarjeta(
                    invocacion.getArgument(0),
                    "Titular",
                    "**** 1111",
                    LocalDate.now().withDayOfMonth(1),
                    "",
                    "Entidad");
            return Optional.of(existente);
        });
    }

    private TarjetaSolicitudDTO solicitud(String tipoCuenta) {
        return new TarjetaSolicitudDTO(
                "Ana Prueba",
                "4111111111111111",
                LocalDate.now().withDayOfMonth(1).plusMonths(1),
                "Banco Nacional",
                tipoCuenta);
    }

    @Test
    void normalizarTipoCuentaAceptaValoresValidos() {
        assertEquals("AHORRO", STarjeta.normalizarTipoCuenta("ahorro"));
        assertEquals("CREDITO", STarjeta.normalizarTipoCuenta("Crédito"));
        assertEquals("CREDITO", STarjeta.normalizarTipoCuenta("CREDITO"));
        assertEquals("CORRIENTE", STarjeta.normalizarTipoCuenta("Corriente"));
    }

    @Test
    void crearGuardaElTipoDeCuentaNormalizado() {
        MTarjeta creada = servicio.crear(USUARIO, solicitud("Crédito"));

        assertEquals("CREDITO", creada.getTipoCuenta());
        verify(tarjetas).save(any(MTarjeta.class));
    }

    @Test
    void crearRechazaTipoDeCuentaAusente() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(USUARIO, solicitud(null)));
        verify(tarjetas, never()).save(any(MTarjeta.class));
    }

    @Test
    void crearRechazaTipoDeCuentaInvalido() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(USUARIO, solicitud("DÉBITO")));
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(USUARIO, solicitud("")));
        verify(tarjetas, never()).save(any(MTarjeta.class));
    }

    @Test
    void actualizarReemplazaElTipoDeCuenta() {
        MTarjeta actualizada = servicio.actualizar("tarjeta-1", USUARIO, solicitud("Corriente"));

        assertEquals("CORRIENTE", actualizada.getTipoCuenta());
        verify(tarjetas).save(any(MTarjeta.class));
    }
}
