package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
import com.luisda.fin_app_back.Filtrado.TipoBolsillo;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SMovimientoContableTest {
    private static final String USUARIO = "usuario-1";

    private IMovimientoContable movimientos;
    private IBolsillo bolsillos;
    private SBolsillo servicioBolsillo;
    private SMovimientoContable servicio;
    private MBolsillo porRevisar;

    @BeforeEach
    void prepararDependencias() {
        movimientos = mock(IMovimientoContable.class);
        bolsillos = mock(IBolsillo.class);
        IUsuario usuarios = mock(IUsuario.class);
        ITarjeta tarjetas = mock(ITarjeta.class);
        servicioBolsillo = mock(SBolsillo.class);
        SReglaFiltro reglasFiltro = mock(SReglaFiltro.class);
        servicio = new SMovimientoContable(movimientos, bolsillos, usuarios, tarjetas, servicioBolsillo, reglasFiltro);
        porRevisar = bolsillo("bolsillo-revisar", "Por revisar", "OTROS", true);

        when(usuarios.existsById(USUARIO)).thenReturn(true);
        when(movimientos.existsByOrigenTipoAndOrigenId("GMAIL", "correo-1")).thenReturn(false);
        when(bolsillos.findByIdBolsilloAndIdUsuario("bolsillo-revisar", USUARIO))
                .thenReturn(Optional.of(porRevisar));
        when(servicioBolsillo.obtenerOCrearPorRevisar(USUARIO)).thenReturn(porRevisar);
        when(movimientos.save(any(MMovimientoContable.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void conDosBolsillosGastoActivosGuardaGlobalConfirmado() {
        MBolsillo gasto1 = bolsillo("gasto-1", "Compras", "GASTO", true);
        MBolsillo gasto2 = bolsillo("gasto-2", "Hogar", "GASTO", true);
        prepararBolsillosClasificables(List.of(gasto1, gasto2));

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals(null, resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals(null, captor.getValue().getBolsillo());
        assertEquals(USUARIO, captor.getValue().getIdUsuario());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    @Test
    void conUnBolsilloGastoActivoGuardaConfirmadoEnEseBolsillo() {
        MBolsillo gasto = bolsillo("gasto-1", "Compras", "GASTO", true);
        prepararBolsillosClasificables(List.of(gasto));

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals("gasto-1", resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals("gasto-1", captor.getValue().getBolsillo().getIdBolsillo());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    @Test
    void unBolsilloGastoInactivoNoSeUsaYSeGuardaGlobalConfirmado() {
        MBolsillo gastoInactivo = bolsillo("gasto-inactivo", "Compras", "GASTO", false);
        prepararBolsillosClasificables(List.of(gastoInactivo));

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals(null, resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals(null, captor.getValue().getBolsillo());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    @Test
    void sinBolsillosGastoCompatiblesSeGuardaGlobalConfirmado() {
        prepararBolsillosClasificables(List.of());

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals(null, resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals(null, captor.getValue().getBolsillo());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    private MovimientoContableDTO importarConClasificacionConfiable() {
        ResultadoClasificacion clasificacion = new ResultadoClasificacion(
                null, TipoBolsillo.GASTO, null, null, null, BigDecimal.ONE, false);
        return servicio.crearAutomaticamenteDesdeGmail(USUARIO, "GASTO", LocalDate.of(2026, 11, 10),
                new BigDecimal("25000"), "Compras", "Mercado", "correo-1", "alertas@bancolombia.com", clasificacion)
                .orElseThrow();
    }

    private void prepararBolsillosClasificables(List<MBolsillo> propios) {
        when(bolsillos.findByIdUsuarioOrderByFechaCreacionDesc(USUARIO)).thenReturn(propios);
        propios.forEach(bolsillo -> when(bolsillos.findByIdBolsilloAndIdUsuario(bolsillo.getIdBolsillo(), USUARIO))
                .thenReturn(Optional.of(bolsillo)));
    }

    private static MBolsillo bolsillo(String id, String nombre, String tipo, boolean activo) {
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(id);
        bolsillo.setIdUsuario(USUARIO);
        bolsillo.setNombre(nombre);
        bolsillo.setTipo(tipo);
        bolsillo.setEstado(activo);
        return bolsillo;
    }
}
