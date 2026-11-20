package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.ResumenGlobalContabilidadDTO;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
import com.luisda.fin_app_back.Filtrado.TipoBolsillo;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.MTarjeta;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private ITarjeta tarjetas;

    @BeforeEach
    void prepararDependencias() {
        movimientos = mock(IMovimientoContable.class);
        bolsillos = mock(IBolsillo.class);
        IUsuario usuarios = mock(IUsuario.class);
        tarjetas = mock(ITarjeta.class);
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
    void conDosBolsillosGastoActivosUsaElFijoGastos() {
        MBolsillo gasto1 = bolsillo("gasto-1", "Compras", "GASTO", true);
        MBolsillo gasto2 = bolsillo("gasto-2", "Hogar", "GASTO", true);
        prepararBolsillosClasificables(List.of(gasto1, gasto2));

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals("fijo-gasto", resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals("fijo-gasto", captor.getValue().getBolsillo().getIdBolsillo());
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
    void unBolsilloGastoInactivoNoSeUsaYSeAsignaAlFijoGastos() {
        MBolsillo gastoInactivo = bolsillo("gasto-inactivo", "Compras", "GASTO", false);
        prepararBolsillosClasificables(List.of(gastoInactivo));

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals("fijo-gasto", resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals("fijo-gasto", captor.getValue().getBolsillo().getIdBolsillo());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    @Test
    void sinBolsillosGastoCompatiblesSeAsignaAlFijoGastos() {
        prepararBolsillosClasificables(List.of());

        MovimientoContableDTO resultado = importarConClasificacionConfiable();

        ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
        verify(movimientos).save(captor.capture());
        assertEquals("fijo-gasto", resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, captor.getValue().getEstadoRevision());
        assertEquals("fijo-gasto", captor.getValue().getBolsillo().getIdBolsillo());
        verify(servicioBolsillo, never()).obtenerOCrearPorRevisar(USUARIO);
    }

    @Test
    void actualizarRechazaAsignarElMovimientoAlBolsilloPorRevisar() {
        MBolsillo revisarPorTipo = bolsillo("bolsillo-por-revisar", "Por revisar", "POR_REVISAR", true);
        when(bolsillos.findByIdBolsilloAndIdUsuario("bolsillo-por-revisar", USUARIO))
                .thenReturn(Optional.of(revisarPorTipo));
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento("mov-1");
        movimiento.setIdUsuario(USUARIO);
        movimiento.setTipo("GASTO");
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.POR_REVISAR);
        when(movimientos.buscarPropioIncluyendoGlobal("mov-1", USUARIO)).thenReturn(Optional.of(movimiento));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizar(USUARIO, "mov-1", solicitudGmail("bolsillo-por-revisar")));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizar(USUARIO, "mov-1", solicitudGmail("bolsillo-revisar")));
        verify(movimientos, never()).save(any(MMovimientoContable.class));
    }

    @Test
    void resumenGlobalSumaElGastoQuePasaPorElBolsilloGastos() {
        MBolsillo gastos = bolsillo("gastos", "Gastos", "GASTO", true);
        when(bolsillos.findByIdUsuarioOrderByFechaCreacionDesc(USUARIO)).thenReturn(List.of(gastos));
        when(movimientos.resumirPorMesIncluyendoGlobales(List.of("gastos"), USUARIO, true))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 11, "INGRESO", 2L, new BigDecimal("2985989.00")},
                        new Object[]{2026, 11, "TRANSFERENCIA", 11L, new BigDecimal("2515000.00")}));
        when(movimientos.resumirGastoBolsilloPorMes(USUARIO))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2026, 11, new BigDecimal("2624212.00")}));
        when(movimientos.contarPorRevisarIncluyendoGlobales(List.of("gastos"), USUARIO)).thenReturn(0L);
        when(movimientos.sumarPorRevisarPorMes(List.of("gastos"), 2026, 11)).thenReturn(BigDecimal.ZERO);

        ResumenGlobalContabilidadDTO resultado = servicio.resumenGlobal(USUARIO, YearMonth.of(2026, 11));

        assertEquals(0, new BigDecimal("2624212.00").compareTo(resultado.gastos()));
        assertEquals(0, new BigDecimal("2515000.00").compareTo(resultado.transferencias()));
        assertEquals(0, new BigDecimal("2985989.00").compareTo(resultado.ingresos()));
    }

    @Test
    void crearGmailPorRevisarReclasificaEnvioACuentaPropiaComoTransferencia() {
        when(tarjetas.findByUsuario_IdUsuario(USUARIO)).thenReturn(List.of(tarjetaPropia("**** 5201")));
        when(movimientos.findFragmentoPorOrigenId("cuenta:correo-propia")).thenReturn(
                "Transferiste $1000000 desde tu cuenta *5201 a la cuenta *25310487123 el 16/11/2026.");

        MovimientoContableDTO resultado = servicio.crearGmailPorRevisar(USUARIO,
                solicitudGmailImportada("cuenta:correo-propia"));

        assertEquals("TRANSFERENCIA", resultado.tipo());
        assertEquals(EstadoRevisionMovimiento.POR_REVISAR, resultado.estadoRevision());
    }

    @Test
    void crearGmailPorRevisarReclasificaComoTransferenciaElOrigenDeCuentaPropiaAunqueElDestinoSeaTercero() {
        when(tarjetas.findByUsuario_IdUsuario(USUARIO)).thenReturn(List.of(tarjetaPropia("**** 5201")));
        when(movimientos.findFragmentoPorOrigenId("cuenta:correo-origen-propio")).thenReturn(
                "Transferiste $50000 desde tu cuenta *5201 a la cuenta *3107378636 el 14/11/2026.");

        MovimientoContableDTO resultado = servicio.crearGmailPorRevisar(USUARIO,
                solicitudGmailImportada("cuenta:correo-origen-propio"));

        assertEquals("TRANSFERENCIA", resultado.tipo());
    }

    @Test
    void crearGmailPorRevisarConservaComoGastoLaCompraSinCuentaPropia() {
        when(tarjetas.findByUsuario_IdUsuario(USUARIO)).thenReturn(List.of(tarjetaPropia("**** 5201")));
        when(movimientos.findFragmentoPorOrigenId("cuenta:correo-compra")).thenReturn(
                "Compraste $16057,00 en FARM PASTEUR con tu T.Deb *9389, el 14/11/2026.");

        MovimientoContableDTO resultado = servicio.crearGmailPorRevisar(USUARIO,
                solicitudGmailImportada("cuenta:correo-compra"));

        assertEquals("GASTO", resultado.tipo());
    }

    @Test
    void confirmarGmailGlobalAceptaTransferenciaRevisada() {
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento("mov-transferencia");
        movimiento.setIdUsuario(USUARIO);
        movimiento.setBolsillo(porRevisar);
        movimiento.setTipo("TRANSFERENCIA");
        movimiento.setFecha(LocalDate.of(2026, 11, 10));
        movimiento.setMonto(new BigDecimal("1000000"));
        movimiento.setCategoria("Transferencia");
        movimiento.setConcepto("Transferencia");
        movimiento.setOrigenTipo("GMAIL");
        movimiento.setOrigenId("cuenta:correo-propia");
        movimiento.setOrigenNombre("banColombia");
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.POR_REVISAR);
        when(movimientos.buscarPropioIncluyendoGlobal("mov-transferencia", USUARIO))
                .thenReturn(Optional.of(movimiento));

        MovimientoContableDTO resultado = servicio.confirmarGmailGlobal(USUARIO, "mov-transferencia");

        assertEquals("TRANSFERENCIA", resultado.tipo());
        assertEquals(null, resultado.idBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, resultado.estadoRevision());
    }

    @Test
    void importarAutomaticamenteReclasificaEnvioACuentaPropiaComoTransferencia() {
        when(tarjetas.findByUsuario_IdUsuario(USUARIO)).thenReturn(List.of(tarjetaPropia("**** 5201")));
        when(movimientos.findFragmentoPorOrigenId("cuenta:correo-auto")).thenReturn(
                "Transferiste $1000000 desde tu cuenta *5201 a la cuenta *25310487123 el 16/11/2026.");
        ResultadoClasificacion clasificacion = new ResultadoClasificacion(
                null, TipoBolsillo.GASTO, null, null, null, BigDecimal.ONE, false);

        MovimientoContableDTO resultado = servicio.crearAutomaticamenteDesdeGmail(USUARIO, "GASTO",
                LocalDate.of(2026, 11, 10), new BigDecimal("1000000"), "Transferencia", "Transferencia",
                "cuenta:correo-auto", "banColombia", clasificacion).orElseThrow();

        assertEquals("TRANSFERENCIA", resultado.tipo());
        assertEquals(EstadoRevisionMovimiento.POR_REVISAR, resultado.estadoRevision());
    }

    private static MovimientoContableSolicitudDTO solicitudGmail(String idBolsillo) {
        return new MovimientoContableSolicitudDTO(idBolsillo, "GASTO", LocalDate.of(2026, 11, 10),
                new BigDecimal("20000"), "Compras", "Alerta", "GMAIL", "correo-1", "banColombia");
    }

    private static MovimientoContableSolicitudDTO solicitudGmailImportada(String origenId) {
        return new MovimientoContableSolicitudDTO("bolsillo-revisar", "GASTO", LocalDate.of(2026, 11, 10),
                new BigDecimal("1000000"), "Transferencia", "Transferencia", "GMAIL", origenId, "banColombia");
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
        MBolsillo fijoGasto = bolsillo("fijo-gasto", "Gastos", "GASTO", true);
        when(servicioBolsillo.listar(USUARIO)).thenReturn(List.of(fijoGasto));
    }

    private static MTarjeta tarjetaPropia(String numero) {
        return new MTarjeta(
                "tarjeta-1", "Luis Daniel", numero, LocalDate.of(2034, 12, 1), "123", "Bancolombia");
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
