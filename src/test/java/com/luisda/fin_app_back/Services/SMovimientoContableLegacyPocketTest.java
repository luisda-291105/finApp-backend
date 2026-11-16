package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del tipo de movimiento en el registro manual: la interfaz envía el
 * tipo pedido y el
 * asistente de IA no lo envía, así que en ese caso se toma el del bolsillo.
 */
class SMovimientoContableLegacyPocketTest {
        @Test
        void aceptaGastoEnBolsilloAhorro() {
                String usuarioId = "usuario-1";
                String bolsilloId = "bolsillo-ahorro";
                IMovimientoContable movimientos = mock(IMovimientoContable.class);
                IBolsillo bolsillos = mock(IBolsillo.class);
                IUsuario usuarios = mock(IUsuario.class);
                when(usuarios.existsById(usuarioId)).thenReturn(true);
                MBolsillo ahorro = bolsillo(bolsilloId, usuarioId, "AHORRO");
                when(bolsillos.findByIdBolsilloAndIdUsuario(bolsilloId, usuarioId)).thenReturn(Optional.of(ahorro));
                when(movimientos.save(any(MMovimientoContable.class)))
                                .thenAnswer(invocacion -> invocacion.getArgument(0));
                SMovimientoContable servicio = new SMovimientoContable(movimientos, bolsillos, usuarios,
                                mock(ITarjeta.class), mock(SBolsillo.class), mock(SReglaFiltro.class));
                MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(bolsilloId, " gasto ",
                                LocalDate.of(2026, 11, 10), new BigDecimal("100.00"), "Compras", "Prueba",
                                "EFECTIVO", null, null);

                servicio.crear(usuarioId, solicitud);

                ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
                verify(movimientos).save(captor.capture());
                assertEquals("GASTO", captor.getValue().getTipo());
                assertEquals(bolsilloId, captor.getValue().getBolsillo().getIdBolsillo());
        }

        @Test
        void sinTipoSolicitadoTomaElDelBolsillo() {
                String usuarioId = "usuario-1";
                String bolsilloId = "bolsillo-gasto";
                IMovimientoContable movimientos = mock(IMovimientoContable.class);
                IBolsillo bolsillos = mock(IBolsillo.class);
                IUsuario usuarios = mock(IUsuario.class);
                when(usuarios.existsById(usuarioId)).thenReturn(true);
                when(bolsillos.findByIdBolsilloAndIdUsuario(bolsilloId, usuarioId))
                                .thenReturn(Optional.of(bolsillo(bolsilloId, usuarioId, "GASTO")));
                when(movimientos.save(any(MMovimientoContable.class)))
                                .thenAnswer(invocacion -> invocacion.getArgument(0));
                SMovimientoContable servicio = new SMovimientoContable(movimientos, bolsillos, usuarios,
                                mock(ITarjeta.class), mock(SBolsillo.class), mock(SReglaFiltro.class));
                // El asistente de IA no envía tipo: debe deducirse del bolsillo.
                MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(bolsilloId, null,
                                LocalDate.of(2026, 11, 10), new BigDecimal("100.00"), "Compras", "Prueba",
                                "EFECTIVO", null, null);

                servicio.crear(usuarioId, solicitud);

                ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
                verify(movimientos).save(captor.capture());
                assertEquals("GASTO", captor.getValue().getTipo());
        }

        @Test
        void aceptaMovimientoNuevoEnBolsilloGasto() {
                String usuarioId = "usuario-1";
                String bolsilloId = "bolsillo-legado";
                IMovimientoContable movimientos = mock(IMovimientoContable.class);
                IBolsillo bolsillos = mock(IBolsillo.class);
                IUsuario usuarios = mock(IUsuario.class);
                when(usuarios.existsById(usuarioId)).thenReturn(true);
                when(bolsillos.findByIdBolsilloAndIdUsuario(bolsilloId, usuarioId))
                                .thenReturn(Optional.of(bolsillo(bolsilloId, usuarioId, "GASTO")));
                when(movimientos.save(any(MMovimientoContable.class)))
                                .thenAnswer(invocacion -> invocacion.getArgument(0));
                SMovimientoContable servicio = new SMovimientoContable(movimientos, bolsillos, usuarios,
                                mock(ITarjeta.class), mock(SBolsillo.class), mock(SReglaFiltro.class));
                MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(bolsilloId, "GASTO",
                                LocalDate.of(2026, 11, 10), new BigDecimal("100.00"), "Compras", "Prueba",
                                "EFECTIVO", null, null);

                servicio.crear(usuarioId, solicitud);

                ArgumentCaptor<MMovimientoContable> captor = ArgumentCaptor.forClass(MMovimientoContable.class);
                verify(movimientos).save(captor.capture());
                assertEquals("GASTO", captor.getValue().getTipo());
        }

        @Test
        void rechazaMovimientoEnBolsilloDeTipoNoPermitido() {
                String usuarioId = "usuario-1";
                String bolsilloId = "bolsillo-otros";
                IMovimientoContable movimientos = mock(IMovimientoContable.class);
                IBolsillo bolsillos = mock(IBolsillo.class);
                IUsuario usuarios = mock(IUsuario.class);
                when(usuarios.existsById(usuarioId)).thenReturn(true);
                when(bolsillos.findByIdBolsilloAndIdUsuario(bolsilloId, usuarioId))
                                .thenReturn(Optional.of(bolsillo(bolsilloId, usuarioId, "OTROS")));
                SMovimientoContable servicio = new SMovimientoContable(movimientos, bolsillos, usuarios,
                                mock(ITarjeta.class), mock(SBolsillo.class), mock(SReglaFiltro.class));
                MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(bolsilloId, "GASTO",
                                LocalDate.of(2026, 11, 10), new BigDecimal("100.00"), "Compras", "Prueba",
                                "EFECTIVO", null, null);

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> servicio.crear(usuarioId, solicitud));

                assertTrue(error.getMessage()
                                .contains("solo pueden asignarse a un bolsillo de tipo AHORRO, GASTO o INGRESO"));
                verify(movimientos, never()).save(any());
        }

        @Test
        void rechazaUnTipoDeMovimientoDesconocido() {
                String usuarioId = "usuario-1";
                String bolsilloId = "bolsillo-ahorro";
                IMovimientoContable movimientos = mock(IMovimientoContable.class);
                IBolsillo bolsillos = mock(IBolsillo.class);
                IUsuario usuarios = mock(IUsuario.class);
                when(usuarios.existsById(usuarioId)).thenReturn(true);
                when(bolsillos.findByIdBolsilloAndIdUsuario(bolsilloId, usuarioId))
                                .thenReturn(Optional.of(bolsillo(bolsilloId, usuarioId, "AHORRO")));
                SMovimientoContable servicio = new SMovimientoContable(movimientos, bolsillos, usuarios,
                                mock(ITarjeta.class), mock(SBolsillo.class), mock(SReglaFiltro.class));
                MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(bolsilloId, "PAGAR",
                                LocalDate.of(2026, 11, 10), new BigDecimal("100.00"), "Compras", "Prueba",
                                "EFECTIVO", null, null);

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> servicio.crear(usuarioId, solicitud));

                assertFalse(error.getMessage().contains("solo pueden asignarse a un bolsillo AHORRO"));
                verify(movimientos, never()).save(any());
        }

        private static MBolsillo bolsillo(String id, String usuarioId, String tipo) {
                MBolsillo bolsillo = new MBolsillo();
                bolsillo.setIdBolsillo(id);
                bolsillo.setIdUsuario(usuarioId);
                bolsillo.setNombre("Ahorro");
                bolsillo.setTipo(tipo);
                bolsillo.setEstado(true);
                return bolsillo;
        }
}
