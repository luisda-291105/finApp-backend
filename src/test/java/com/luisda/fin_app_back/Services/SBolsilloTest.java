package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SBolsilloTest {
    private static final String USUARIO = "usuario-1";
    private IBolsillo repositorio;
    private IUsuario usuarios;
    private IContabilidad contabilidades;
    private IMovimientoContable movimientos;
    private SBolsillo servicio;
    private MContabilidad contabilidad;

    @BeforeEach
    void preparar() {
        repositorio = mock(IBolsillo.class);
        contabilidades = mock(IContabilidad.class);
        usuarios = mock(IUsuario.class);
        movimientos = mock(IMovimientoContable.class);
        servicio = new SBolsillo(repositorio, contabilidades, usuarios, movimientos);
        MUsuario propietario = mock(MUsuario.class);
        contabilidad = new MContabilidad("contabilidad-1", "Personal", 0, true, LocalDate.now());
        contabilidad.setUsuario(propietario);
        when(usuarios.existsById(USUARIO)).thenReturn(true);
        when(contabilidades.findByUsuario_IdUsuario(USUARIO)).thenReturn(Optional.of(contabilidad));
        when(repositorio.save(any(MBolsillo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(repositorio.saveAll(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void crearAhorroEsAceptado() {
        MBolsillo creado = servicio.crear(USUARIO, solicitud("Viajes", "AHORRO"));

        assertEquals("AHORRO", creado.getTipo());
        verify(repositorio).save(any(MBolsillo.class));
    }

    @Test
    void crearRechazaGastoIngresoYOtros() {
        for (String tipo : List.of("GASTO", "INGRESO", "OTROS")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(USUARIO, solicitud("No permitido", tipo)));
            assertTrue(error.getMessage().contains("solo pueden ser de tipo AHORRO"));
        }

        verify(repositorio, never()).save(any(MBolsillo.class));
    }

    @Test
    void editarBolsilloLegadoPermiteConvertirloAAhorro() {
        MBolsillo legado = bolsillo("bolsillo-legado", "Compras", "GASTO");
        when(repositorio.findByIdBolsilloAndIdUsuario("bolsillo-legado", USUARIO)).thenReturn(Optional.of(legado));

        MBolsillo actualizado = servicio.actualizar(USUARIO, "bolsillo-legado", solicitud("Fondo", "AHORRO"));

        assertEquals("AHORRO", actualizado.getTipo());
        assertEquals("Fondo", actualizado.getNombre());
        verify(repositorio).save(legado);
    }

    @Test
    void bolsilloPorRevisarSeExcluyeDelListadoNormalYSeConsultaAparte() {
        MBolsillo ahorro = bolsillo("ahorro-1", "Emergencias", "AHORRO");
        MBolsillo porRevisar = bolsillo("revisar-1", "Por revisar", "OTROS");
        when(repositorio.findByContabilidad_IdContabilidadOrderByFechaCreacionDesc("contabilidad-1"))
                .thenReturn(List.of(porRevisar, ahorro));

        List<MBolsillo> listado = servicio.listar(USUARIO);
        Optional<MBolsillo> interno = servicio.obtenerPorRevisar(USUARIO);

        assertEquals(List.of(ahorro), listado);
        assertTrue(interno.isPresent());
        assertEquals("revisar-1", interno.orElseThrow().getIdBolsillo());
        assertEquals("POR_REVISAR", interno.orElseThrow().getTipo());
    }

    @Test
    void obtenerOCrearPorRevisarReutilizaElInternoAunqueNoAparezcaEnElListado() {
        MBolsillo porRevisar = bolsillo("revisar-1", "Por revisar", "OTROS");
        when(repositorio.findByContabilidad_IdContabilidadOrderByFechaCreacionDesc("contabilidad-1"))
                .thenReturn(List.of(porRevisar));

        MBolsillo resultado = servicio.obtenerOCrearPorRevisar(USUARIO);

        assertEquals(porRevisar, resultado);
        assertEquals("POR_REVISAR", resultado.getTipo());
        verify(repositorio).saveAll(any());
        verify(repositorio, never()).save(any(MBolsillo.class));
    }

    @Test
    void eliminarRechazaBolsilloInternoPorRevisarAunqueEsteVacio() {
        MBolsillo porRevisar = bolsillo("revisar-1", "Por revisar", "OTROS");
        when(repositorio.findByIdBolsilloAndIdUsuario("revisar-1", USUARIO)).thenReturn(Optional.of(porRevisar));
        when(movimientos.existsByBolsillo_IdBolsillo("revisar-1")).thenReturn(false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> servicio.eliminar(USUARIO, "revisar-1"));

        assertTrue(error.getMessage().contains("bolsillo interno «Por revisar»"));
        verify(repositorio, never()).delete(any(MBolsillo.class));
        verify(movimientos, never()).existsByBolsillo_IdBolsillo("revisar-1");
    }

    private static BolsilloSolicitudDTO solicitud(String nombre, String tipo) {
        return new BolsilloSolicitudDTO(nombre, true, BigDecimal.ZERO, tipo);
    }

    private static MBolsillo bolsillo(String id, String nombre, String tipo) {
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(id);
        bolsillo.setIdUsuario(USUARIO);
        bolsillo.setNombre(nombre);
        bolsillo.setTipo(tipo);
        bolsillo.setEstado(true);
        bolsillo.setMontoObjetivo(BigDecimal.ZERO);
        bolsillo.setFechaCreacion(LocalDate.now());
        return bolsillo;
    }
}
