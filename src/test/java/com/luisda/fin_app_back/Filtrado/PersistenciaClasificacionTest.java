package com.luisda.fin_app_back.Filtrado;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.ResumenGlobalContabilidadDTO;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.MReglaFiltro;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Model.TipoReglaFiltro;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IReglaFiltro;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Services.SClasificadorMovimiento;
import com.luisda.fin_app_back.Services.JwtTokenService;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersistenciaClasificacionTest {
    @Autowired private EntityManager entityManager;
    @Autowired private IReglaFiltro reglas;
    @Autowired private IMovimientoContable movimientos;
    @Autowired private IBolsillo bolsillos;
    @Autowired private SMovimientoContable servicioMovimientos;
    @Autowired private MockMvc mockMvc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private JwtTokenService jwtTokens;

    @Test
    void gastoConfiableSinBolsilloCompatibleSeGuardaGlobalYNoAfectaSaldoAhorro() {
        MBolsillo ahorro = prepararBolsillo("AHORRO");
        var clasificacion = new ResultadoClasificacion(null, TipoBolsillo.GASTO,
                new BigDecimal("13000.00"), "Alerta bancaria", LocalDate.now(),
                new BigDecimal("0.95"), false);

        var creado = servicioMovimientos.crearAutomaticamenteDesdeGmail("usuario-regla", "GASTO",
                LocalDate.of(2026, 11, 10), new BigDecimal("13000.00"), "Compras y pagos",
                "Alerta bancaria", "global-gmail-13000", "alertasynotificaciones@banco.test", clasificacion)
                .orElseThrow();
        entityManager.flush();
        entityManager.clear();

        MMovimientoContable guardado = movimientos.findById(creado.idMovimiento()).orElseThrow();
        assertEquals(null, guardado.getBolsillo());
        assertEquals("usuario-regla", guardado.getIdUsuario());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, guardado.getEstadoRevision());
        assertEquals(0, movimientos.calcularSaldoDisponible(ahorro.getIdBolsillo(), null).compareTo(BigDecimal.ZERO));
    }

    @Test
    void movimientoGlobalSeIncluyeUnaSolaVezEnResumenMensualDelPropietario() {
        MBolsillo ahorro = prepararBolsillo("AHORRO");
        var clasificacion = new ResultadoClasificacion(null, TipoBolsillo.GASTO,
                new BigDecimal("13000.00"), "Alerta bancaria", LocalDate.now(),
                new BigDecimal("0.95"), false);
        servicioMovimientos.crearAutomaticamenteDesdeGmail("usuario-regla", "GASTO",
                LocalDate.of(2026, 5, 10), new BigDecimal("13000.00"), "Compras y pagos",
                "Compra", "global-summary-13000", "alertasynotificaciones@banco.test", clasificacion);
        entityManager.flush();

        ResumenGlobalContabilidadDTO resumen = servicioMovimientos.resumenGlobal("usuario-regla", YearMonth.of(2026, 5));

        assertEquals(new BigDecimal("13000.00"), resumen.gastos());
        assertEquals(BigDecimal.ZERO, resumen.ingresos());
        assertEquals(BigDecimal.ZERO, movimientos.calcularSaldoDisponible(ahorro.getIdBolsillo(), null));
    }

    @Test
    void ingresoGlobalConfiableSeIncluyeEnElCardDeIngresosDelMes() {
        prepararBolsillo("AHORRO");
        var clasificacion = new ResultadoClasificacion(null, TipoBolsillo.INGRESO,
                new BigDecimal("25000.00"), "Transferencia recibida", LocalDate.now(),
                new BigDecimal("0.95"), false);

        var creado = servicioMovimientos.crearAutomaticamenteDesdeGmail("usuario-regla", "INGRESO",
                LocalDate.of(2026, 11, 10), new BigDecimal("25000.00"), "Ingresos",
                "Recibiste una transferencia", "global-ingreso-25000", "alertasynotificaciones@banco.test", clasificacion)
                .orElseThrow();
        entityManager.flush();
        entityManager.clear();

        MMovimientoContable guardado = movimientos.findById(creado.idMovimiento()).orElseThrow();
        ResumenGlobalContabilidadDTO resumen = servicioMovimientos.resumenGlobal("usuario-regla", YearMonth.of(2026, 11));

        assertEquals(null, guardado.getBolsillo());
        assertEquals("usuario-regla", guardado.getIdUsuario());
        assertEquals("INGRESO", guardado.getTipo());
        assertEquals(new BigDecimal("25000.00"), resumen.ingresos());
        assertEquals(BigDecimal.ZERO, resumen.gastos());
    }

    @Test
    void sugerenciaGmailSeGuardaPorRevisarYSeConfirmaGlobalSinTocarAhorro() {
        MBolsillo ahorro = prepararBolsillo("AHORRO");
        MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(null, "GASTO",
                LocalDate.of(2026, 11, 9), new BigDecimal("300000.00"), "Retiros", "Retiro Bancolombia",
                "GMAIL", "cuenta-retiro:mensaje-1", "alertasynotificaciones@ayn.notificacionesbancolombia.com");

        var pendiente = servicioMovimientos.crearGmailPorRevisar("usuario-regla", solicitud);
        entityManager.flush();
        assertEquals(EstadoRevisionMovimiento.POR_REVISAR, pendiente.estadoRevision());
        assertEquals(0, movimientos.calcularSaldoDisponible(ahorro.getIdBolsillo(), null).compareTo(BigDecimal.ZERO));

        var confirmado = servicioMovimientos.confirmarGmailGlobal("usuario-regla", pendiente.idMovimiento());
        entityManager.flush();
        entityManager.clear();
        MMovimientoContable guardado = movimientos.findById(confirmado.idMovimiento()).orElseThrow();
        ResumenGlobalContabilidadDTO resumen = servicioMovimientos.resumenGlobal("usuario-regla", YearMonth.of(2026, 11));

        assertEquals(null, guardado.getBolsillo());
        assertEquals("GASTO", guardado.getTipo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, guardado.getEstadoRevision());
        assertEquals(new BigDecimal("300000.00"), resumen.gastos());
        assertEquals(0, movimientos.calcularSaldoDisponible(ahorro.getIdBolsillo(), null).compareTo(BigDecimal.ZERO));
    }

    @Test
    void movimientoConBolsilloCompatibleValidoConservaLaRutaActual() {
        MBolsillo gasto = prepararBolsillo("GASTO");
        var clasificacion = new ResultadoClasificacion(gasto.getIdBolsillo(), TipoBolsillo.GASTO,
                new BigDecimal("5000.00"), "Comercio", LocalDate.now(), new BigDecimal("0.95"), false);

        var creado = servicioMovimientos.crearAutomaticamenteDesdeGmail("usuario-regla", "GASTO",
                LocalDate.now(), new BigDecimal("5000.00"), "Compras y pagos", "Compra", "global-gmail-compatible",
                "notificaciones@banco.test", clasificacion).orElseThrow();

        MMovimientoContable guardado = movimientos.findById(creado.idMovimiento()).orElseThrow();
        assertEquals(gasto.getIdBolsillo(), guardado.getBolsillo().getIdBolsillo());
        assertEquals(EstadoRevisionMovimiento.CONFIRMADO, guardado.getEstadoRevision());
    }

    @Test
    void reglaPersistidaCoincideSinLlamarAlClienteDelModelo() {
        MBolsillo bolsillo = prepararBolsillo("GASTO");
        MReglaFiltro regla = new MReglaFiltro();
        regla.setId("regla-1");
        regla.setBolsillo(bolsillo);
        regla.setTipo(TipoReglaFiltro.PALABRA_CLAVE);
        regla.setValor("mercado");
        regla.setFechaCreacion(LocalDateTime.now());
        reglas.saveAndFlush(regla);

        ClienteModeloFiltro cliente = Mockito.mock(ClienteModeloFiltro.class);
        ClasificadorMovimiento clasificador = new ClasificadorMovimiento(cliente,
                new ValidadorRespuesta(new ObjectMapper()));
        SClasificadorMovimiento servicio = new SClasificadorMovimiento(reglas, clasificador);

        ResultadoClasificacion resultado = servicio.clasificar("usuario-regla", "", "Compra en el mercado");

        assertEquals(bolsillo.getIdBolsillo(), resultado.idBolsillo());
        assertEquals(TipoBolsillo.GASTO, resultado.bolsillo());
        verify(cliente, never()).clasificar(anyString());
    }

    @Test
    void movimientoPorRevisarQuedaFueraDelSaldoDisponible() {
        MBolsillo bolsillo = prepararBolsillo("GASTO");
        entityManager.persist(movimiento(bolsillo, "mov-confirmado", new BigDecimal("100"), null));
        entityManager.persist(movimiento(bolsillo, "mov-revisar", new BigDecimal("50"),
                EstadoRevisionMovimiento.POR_REVISAR));
        entityManager.flush();

        BigDecimal saldo = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), null);

        assertEquals(new BigDecimal("-100.00"), saldo);
    }

    @Test
    void saldoCombinaIngresoYGastosDeCualquierOrigenSinDobleResta() {
        MBolsillo bolsillo = prepararBolsillo("AHORRO");
        MMovimientoContable ingreso = movimiento(bolsillo, "ingreso-100", new BigDecimal("100.00"), null);
        ingreso.setTipo("INGRESO");
        MMovimientoContable gastoEfectivo = movimiento(bolsillo, "gasto-efectivo-30", new BigDecimal("30.00"), null);
        gastoEfectivo.setOrigenTipo("EFECTIVO");
        MMovimientoContable gastoMismoBolsillo = movimiento(bolsillo, "gasto-bolsillo-20", new BigDecimal("20.00"), null);
        gastoMismoBolsillo.setOrigenTipo("BOLSILLO");
        gastoMismoBolsillo.setOrigenId(bolsillo.getIdBolsillo());
        entityManager.persist(ingreso);
        entityManager.persist(gastoEfectivo);
        entityManager.persist(gastoMismoBolsillo);
        entityManager.flush();

        BigDecimal saldo = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), null);

        assertEquals(new BigDecimal("50.00"), saldo);
    }

    @Test
    void estadoRevisionNuloCuentaComoConfirmado() {
        MBolsillo bolsillo = prepararBolsillo("AHORRO");
        entityManager.persist(movimiento(bolsillo, "gasto-confirmado-nulo", new BigDecimal("25.00"), null));
        entityManager.flush();

        BigDecimal saldo = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), null);

        assertEquals(new BigDecimal("-25.00"), saldo);
    }

    @Test
    void gastoAtribuidoABConOrigenEnASeDescuentaSoloDeA() {
        MBolsillo origen = prepararBolsillo("AHORRO");
        MBolsillo categoria = nuevoBolsillo("bolsillo-categoria", "Categoría", "AHORRO", origen.getContabilidad());
        MMovimientoContable gasto = movimiento(categoria, "gasto-fondos-a", new BigDecimal("30.00"), null);
        gasto.setOrigenTipo("BOLSILLO");
        gasto.setOrigenId(origen.getIdBolsillo());
        entityManager.persist(gasto);
        entityManager.flush();

        BigDecimal saldoCategoria = movimientos.calcularSaldoDisponible(categoria.getIdBolsillo(), null);
        BigDecimal saldoOrigen = movimientos.calcularSaldoDisponible(origen.getIdBolsillo(), null);

        assertEquals(0, saldoCategoria.compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("-30.00"), saldoOrigen);
    }

    @Test
    void transferenciaEntreBolsillosSumaAlDestinoYRestaAlOrigen() {
        MBolsillo origen = prepararBolsillo("AHORRO");
        MBolsillo destino = nuevoBolsillo("bolsillo-destino", "Destino", "AHORRO", origen.getContabilidad());
        MMovimientoContable transferencia = movimiento(destino, "transferencia-40", new BigDecimal("40.00"), null);
        transferencia.setTipo("TRANSFERENCIA");
        transferencia.setOrigenTipo("BOLSILLO");
        transferencia.setOrigenId(origen.getIdBolsillo());
        entityManager.persist(transferencia);
        entityManager.flush();

        BigDecimal saldoDestino = movimientos.calcularSaldoDisponible(destino.getIdBolsillo(), null);
        BigDecimal saldoOrigen = movimientos.calcularSaldoDisponible(origen.getIdBolsillo(), null);

        assertEquals(new BigDecimal("40.00"), saldoDestino);
        assertEquals(new BigDecimal("-40.00"), saldoOrigen);
        List<String> ids = List.of(origen.getIdBolsillo(), destino.getIdBolsillo());
        assertEquals(List.of(), movimientos.resumirPorMes(ids));
        List<Object[]> resumenMensualInclusivo = movimientos.resumirPorMesIncluyendoTransferencias(ids);
        assertEquals(1, resumenMensualInclusivo.size());
        assertEquals("TRANSFERENCIA", resumenMensualInclusivo.getFirst()[2]);
        assertEquals(List.of(), movimientos.resumirPorBolsilloTipoCategoria(
                ids));
    }

    @Test
    void transferenciaConOrigenIgualADestinoSeRechaza() {
        MBolsillo bolsillo = prepararBolsillo("AHORRO");
        MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(
                bolsillo.getIdBolsillo(), "TRANSFERENCIA", LocalDate.now(), new BigDecimal("10.00"),
                "Transferencias", "Prueba", "BOLSILLO", bolsillo.getIdBolsillo(), null);

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> servicioMovimientos.crear("usuario-regla", solicitud));

        assertEquals("El bolsillo de origen debe ser distinto del bolsillo destino.", error.getMessage());
    }

    @Test
    void resumenGlobalDelMesSeparaIngresosGastosAhorrosTransferenciasYSaldos() {
        MBolsillo origen = prepararBolsillo("AHORRO");
        MBolsillo destino = nuevoBolsillo("bolsillo-destino", "Viajes", "AHORRO", origen.getContabilidad());
        MBolsillo revisar = nuevoBolsillo("bolsillo-revisar", "Por revisar", "OTROS", origen.getContabilidad());

        MMovimientoContable ingreso = conFecha(movimiento(origen, "resumen-ingreso", new BigDecimal("100.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 1);
        ingreso.setTipo("INGRESO");
        MMovimientoContable gasto = conFecha(movimiento(destino, "resumen-gasto", new BigDecimal("30.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 2);
        gasto.setOrigenTipo("BOLSILLO");
        gasto.setOrigenId(origen.getIdBolsillo());
        MMovimientoContable transferencia = conFecha(movimiento(destino, "resumen-transferencia", new BigDecimal("40.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 3);
        transferencia.setTipo("TRANSFERENCIA");
        transferencia.setOrigenTipo("BOLSILLO");
        transferencia.setOrigenId(origen.getIdBolsillo());
        MMovimientoContable ahorro = conFecha(movimiento(origen, "resumen-ahorro", new BigDecimal("15.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 4);
        ahorro.setTipo("AHORRO");
        MMovimientoContable porRevisar = conFecha(movimiento(revisar, "resumen-por-revisar",
                new BigDecimal("13000.00"), EstadoRevisionMovimiento.POR_REVISAR), 2026, 5, 5);
        entityManager.persist(ingreso);
        entityManager.persist(gasto);
        entityManager.persist(transferencia);
        entityManager.persist(ahorro);
        entityManager.persist(porRevisar);
        entityManager.flush();

        ResumenGlobalContabilidadDTO resumen = servicioMovimientos.resumenGlobal("usuario-regla", YearMonth.of(2026, 5));

        assertEquals(new BigDecimal("100.00"), resumen.ingresos());
        assertEquals(new BigDecimal("30.00"), resumen.gastos());
        assertEquals(new BigDecimal("15.00"), resumen.ahorros());
        assertEquals(new BigDecimal("40.00"), resumen.transferencias());
        assertEquals(new BigDecimal("70.00"), resumen.saldoAhorros());
        assertEquals(1L, resumen.porRevisar());
        assertEquals(new BigDecimal("13000.00"), resumen.montoPorRevisar());
    }

    @Test
    void getResumenMantieneTotalesDeIngresoYGastoCuandoElMesTieneTransferencia() throws Exception {
        MBolsillo origen = prepararBolsillo("AHORRO");
        MBolsillo destino = nuevoBolsillo("bolsillo-destino", "Viajes", "AHORRO", origen.getContabilidad());
        MMovimientoContable ingreso = conFecha(movimiento(origen, "endpoint-ingreso", new BigDecimal("100.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 1);
        ingreso.setTipo("INGRESO");
        MMovimientoContable gasto = conFecha(movimiento(destino, "endpoint-gasto", new BigDecimal("30.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 2);
        gasto.setOrigenTipo("EFECTIVO");
        MMovimientoContable transferencia = conFecha(movimiento(destino, "endpoint-transferencia", new BigDecimal("40.00"),
                EstadoRevisionMovimiento.CONFIRMADO), 2026, 5, 3);
        transferencia.setTipo("TRANSFERENCIA");
        transferencia.setOrigenTipo("BOLSILLO");
        transferencia.setOrigenId(origen.getIdBolsillo());
        entityManager.persist(ingreso);
        entityManager.persist(gasto);
        entityManager.persist(transferencia);
        entityManager.flush();

        ReflectionTestUtils.setField(jwtTokens, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        String token = jwtTokens.emitir("usuario-regla");
        MvcResult result = mockMvc.perform(get("/movimiento/resumen")
                        .header("Authorization", "Bearer " + token)
                        .param("mes", "2026-05"))
                .andExpect(status().isOk())
                .andReturn();
        var bolsillosJson = objectMapper.readTree(result.getResponse().getContentAsString());
        var resumenDestino = StreamSupport.stream(bolsillosJson.spliterator(), false)
                .filter(item -> "bolsillo-destino".equals(item.path("idBolsillo").asText()))
                .findFirst().orElseThrow();

        assertEquals(0, resumenDestino.path("ingresos").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, resumenDestino.path("gastos").decimalValue().compareTo(new BigDecimal("30.00")));
        assertEquals(0, resumenDestino.path("otros").decimalValue().compareTo(new BigDecimal("40.00")));
    }

    private MBolsillo prepararBolsillo(String tipo) {
        MUsuario usuario = new MUsuario("usuario-regla", "Usuario de prueba", "hash", LocalDate.now());
        entityManager.persist(usuario);
        MContabilidad contabilidad = new MContabilidad("contabilidad-regla", "Contabilidad", 0, true, LocalDate.now());
        contabilidad.setUsuario(usuario);
        entityManager.persist(contabilidad);
        MBolsillo bolsillo = nuevoBolsillo("bolsillo-regla", "Gastos", tipo, contabilidad);
        entityManager.flush();
        return bolsillo;
    }

    private MBolsillo nuevoBolsillo(String id, String nombre, String tipo, MContabilidad contabilidad) {
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(id);
        bolsillo.setIdUsuario(contabilidad.getUsuario().getIdUsuario());
        bolsillo.setContabilidad(contabilidad);
        bolsillo.setNombre(nombre);
        bolsillo.setTipo(tipo);
        bolsillo.setEstado(true);
        bolsillo.setMontoObjetivo(BigDecimal.ZERO);
        bolsillo.setFechaCreacion(LocalDate.now());
        entityManager.persist(bolsillo);
        return bolsillo;
    }

    private MMovimientoContable movimiento(MBolsillo bolsillo, String id, BigDecimal monto,
                                            EstadoRevisionMovimiento estado) {
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(id);
        movimiento.setBolsillo(bolsillo);
        movimiento.setFecha(LocalDate.now());
        movimiento.setMonto(monto);
        movimiento.setTipo("GASTO");
        movimiento.setCategoria("Compras");
        movimiento.setConcepto("Prueba");
        movimiento.setEstadoRevision(estado);
        return movimiento;
    }

    private static MMovimientoContable conFecha(MMovimientoContable movimiento, int anio, int mes, int dia) {
        movimiento.setFecha(LocalDate.of(anio, mes, dia));
        return movimiento;
    }
}
