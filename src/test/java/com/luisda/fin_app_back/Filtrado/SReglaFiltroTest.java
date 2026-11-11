package com.luisda.fin_app_back.Filtrado;

import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.TipoReglaFiltro;
import com.luisda.fin_app_back.Repository.IReglaFiltro;
import com.luisda.fin_app_back.Services.SReglaFiltro;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SReglaFiltroTest {
    @Test
    void noInsertaUnaReglaDuplicada() {
        IReglaFiltro repositorio = mock(IReglaFiltro.class);
        SReglaFiltro servicio = new SReglaFiltro(repositorio);
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo("bolsillo-1");
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setBolsillo(bolsillo);
        movimiento.setConcepto("Compras");
        movimiento.setOrigenNombre("alertasynotificacionesbancolombia.com");
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.POR_REVISAR);
        when(repositorio.existsByBolsillo_IdBolsilloAndTipoAndValorNormalizado(
                "bolsillo-1", TipoReglaFiltro.REMITENTE, "alertasynotificacionesbancolombia.com")).thenReturn(true);

        servicio.registrarCorreccion(movimiento);

        verify(repositorio, never()).save(any());
    }

    @Test
    void aprendeRemitenteEnLugarDeConceptoGenerico() {
        IReglaFiltro repositorio = mock(IReglaFiltro.class);
        SReglaFiltro servicio = new SReglaFiltro(repositorio);
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo("bolsillo-1");
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setBolsillo(bolsillo);
        movimiento.setConcepto("Compras");
        movimiento.setOrigenNombre("alertasynotificacionesbancolombia.com");
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.POR_REVISAR);

        when(repositorio.existsByBolsillo_IdBolsilloAndTipoAndValorNormalizado(
                "bolsillo-1", TipoReglaFiltro.REMITENTE, "alertasynotificacionesbancolombia.com")).thenReturn(false);

        servicio.registrarCorreccion(movimiento);

        org.mockito.ArgumentCaptor<com.luisda.fin_app_back.Model.MReglaFiltro> captor =
                org.mockito.ArgumentCaptor.forClass(com.luisda.fin_app_back.Model.MReglaFiltro.class);
        verify(repositorio).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(TipoReglaFiltro.REMITENTE, captor.getValue().getTipo());
        org.junit.jupiter.api.Assertions.assertEquals("alertasynotificacionesbancolombia.com", captor.getValue().getValor());
    }
}
