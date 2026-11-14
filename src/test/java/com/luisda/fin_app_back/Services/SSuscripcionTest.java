package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.SuscripcionDTO;
import com.luisda.fin_app_back.DTO.SuscripcionSolicitudDTO;
import com.luisda.fin_app_back.Model.MSuscripcion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import com.luisda.fin_app_back.Repository.ISuscripcion;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SSuscripcionTest {

    private ISuscripcion repositorio;
    private SSuscripcion servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(ISuscripcion.class);
        IUsuario usuarios = mock(IUsuario.class);
        when(usuarios.findById("usuario-1")).thenReturn(Optional.of(usuario("usuario-1")));
        servicio = new SSuscripcion(repositorio, usuarios);
    }

    @Test
    void creaSuscripcionManualDelUsuarioDeLaSesion() {
        when(repositorio.save(any(MSuscripcion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        SuscripcionDTO resultado = servicio.crear("usuario-1", solicitud("Netflix", "29900", "Alta"));

        assertNotNull(resultado.id());
        assertEquals("MANUAL", resultado.origen());
        assertEquals("Netflix", resultado.nombre());
        assertEquals("Servicios", resultado.categoria());
        assertEquals(new BigDecimal("29900.00"), resultado.monto());
        assertEquals(LocalDate.of(2026, 11, 20), resultado.fechaCobro());
        assertEquals("ALTA", resultado.importancia());
        assertTrue(resultado.activa());
    }

    @Test
    void normalizaLaImportanciaYRechazaValoresInvalidos() {
        when(repositorio.save(any(MSuscripcion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        SuscripcionDTO baja = servicio.crear("usuario-1", solicitud("Internet", "80000", "Baja"));

        assertEquals("BAJA", baja.importancia());
        SSuscripcion.DatosInvalidosException error = assertThrows(SSuscripcion.DatosInvalidosException.class,
                () -> servicio.crear("usuario-1", solicitud("Internet", "80000", "Media")));
        assertEquals("importancia", error.getCampo());
    }

    @Test
    void exigeNombreMontoFechaCategoria() {
        SSuscripcion.DatosInvalidosException error = assertThrows(SSuscripcion.DatosInvalidosException.class,
                () -> servicio.crear("usuario-1", new SuscripcionSolicitudDTO(
                        "  ", new BigDecimal("1000"), LocalDate.of(2026, 11, 20), "Servicios", "Alta", null)));
        assertEquals("nombre", error.getCampo());
        assertEquals("El nombre es obligatorio.", error.getMessage());
    }

    @Test
    void listaLasSuscripcionesDelUsuarioSeparadasPorOrigen() {
        MSuscripcion manual = manual("manual-1", "Netflix", LocalDate.of(2026, 11, 20));
        MSuscripcion deGmail = gmail("gmail-1", "correo@promo.com");
        when(repositorio.findDelUsuario("usuario-1")).thenReturn(List.of(manual, deGmail));

        List<SuscripcionDTO> resultado = servicio.listar("usuario-1", null);

        assertEquals(2, resultado.size());
        assertEquals("MANUAL", resultado.get(0).origen());
        assertEquals("GMAIL", resultado.get(1).origen());
        assertEquals("correo@promo.com", resultado.get(1).correoRemitente());
    }

    @Test
    void noEntregaSuscripcionesDeOtroUsuario() {
        when(repositorio.findPropia("gmail-1", "usuario-2")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> servicio.obtener("usuario-2", "gmail-1"));
    }

    @Test
    void noPermiteEditarSuscripcionesDetectadasEnGmail() {
        MSuscripcion deGmail = gmail("gmail-1", "correo@promo.com");
        when(repositorio.findPropia("gmail-1", "usuario-1")).thenReturn(Optional.of(deGmail));

        SSuscripcion.NoEditableException error = assertThrows(SSuscripcion.NoEditableException.class,
                () -> servicio.actualizar("usuario-1", "gmail-1", solicitud("Netflix", "29900", "Alta")));

        assertTrue(error.getMessage().contains("Gmail"));
        verify(repositorio, never()).save(any(MSuscripcion.class));
    }

    @Test
    void actualizaYEliminaSoloSuscripcionesManuales() {
        MSuscripcion manual = manual("manual-1", "Netflix", LocalDate.of(2026, 11, 20));
        when(repositorio.findPropia("manual-1", "usuario-1")).thenReturn(Optional.of(manual));
        when(repositorio.save(manual)).thenReturn(manual);

        SuscripcionDTO actualizada = servicio.actualizar("usuario-1", "manual-1",
                solicitud("Netflix Premium", "34900", "Baja"));

        assertEquals("Netflix Premium", actualizada.nombre());
        assertEquals("BAJA", actualizada.importancia());

        servicio.eliminar("usuario-1", "manual-1");

        verify(repositorio).delete(manual);
    }

    @Test
    void pausaYSigueUnaSuscripcionManual() {
        MSuscripcion manual = manual("manual-1", "Netflix", LocalDate.of(2026, 11, 20));
        when(repositorio.findPropia("manual-1", "usuario-1")).thenReturn(Optional.of(manual));
        when(repositorio.save(manual)).thenReturn(manual);

        SuscripcionDTO pausada = servicio.cambiarEstado("usuario-1", "manual-1", false);
        assertTrue(!pausada.activa());

        SuscripcionDTO activa = servicio.cambiarEstado("usuario-1", "manual-1", true);
        assertTrue(activa.activa());
    }

    private SuscripcionSolicitudDTO solicitud(String nombre, String monto, String importancia) {
        return new SuscripcionSolicitudDTO(nombre, new BigDecimal(monto), LocalDate.of(2026, 11, 20),
                "Servicios", importancia, null);
    }

    private MUsuario usuario(String id) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        return usuario;
    }

    private MSuscripcion manual(String id, String nombre, LocalDate fechaCobro) {
        MSuscripcion suscripcion = new MSuscripcion();
        suscripcion.setId(id);
        suscripcion.setUsuario(usuario("usuario-1"));
        suscripcion.setOrigen(OrigenSuscripcion.MANUAL);
        suscripcion.setNombre(nombre);
        suscripcion.setCategoria("Servicios");
        suscripcion.setMonto(new BigDecimal("29900.00"));
        suscripcion.setFechaCobro(fechaCobro);
        suscripcion.setImportancia(SSuscripcion.IMPORTANCIA_ALTA);
        suscripcion.setActiva(true);
        suscripcion.setFechaCreacion(java.time.Instant.now());
        suscripcion.setFechaActualizacion(java.time.Instant.now());
        return suscripcion;
    }

    private MSuscripcion gmail(String id, String remitente) {
        MSuscripcion suscripcion = new MSuscripcion();
        suscripcion.setId(id);
        suscripcion.setUsuario(usuario("usuario-1"));
        suscripcion.setOrigen(OrigenSuscripcion.GMAIL);
        suscripcion.setNombre("Promociones");
        suscripcion.setCorreoRemitente(remitente);
        suscripcion.setCategoriasJson("[\"CATEGORY_PROMOTIONS\"]");
        suscripcion.setListUnsubscribe("<https://baja.example.com/x>");
        suscripcion.setActiva(true);
        suscripcion.setUltimaActividad(java.time.Instant.now());
        suscripcion.setFechaCreacion(java.time.Instant.now());
        suscripcion.setFechaActualizacion(java.time.Instant.now());
        return suscripcion;
    }
}
