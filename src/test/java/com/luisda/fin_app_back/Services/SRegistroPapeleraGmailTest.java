package com.luisda.fin_app_back.Services;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-38, RF-39, CD-04 — el registro en memoria conserva las vistas previas y los lotes de limpieza
 * por cuenta/usuario, con el bloqueo «una limpieza por cuenta». Se pierde al reiniciar.
 */
class SRegistroPapeleraGmailTest {

    private final SRegistroPapeleraGmail registro = new SRegistroPapeleraGmail();

    @Test
    void registraYRecuperaUnaVistaPreviaDeLaCuentaYUsuario() {
        SRegistroPapeleraGmail.VistaPrevia vista = registro.registrarVistaPrevia(
                "usuario-1", "cuenta-1", List.of("CATEGORY_PROMOTIONS"), 7, 2, List.of(
                        new SRegistroPapeleraGmail.CorreoElegible("m1", "CATEGORY_PROMOTIONS",
                                "promo@tienda.com", "Asunto", "fragmento", Instant.now())));

        assertNotNull(vista.id());
        assertEquals(2, vista.totalEstimado());
        assertEquals(List.of("CATEGORY_PROMOTIONS"), vista.categorias());
        assertTrue(registro.obtenerVistaPrevia(vista.id(), "usuario-1", "cuenta-1").isPresent());
    }

    @Test
    void noDevuelveVistasPreviasDeOtraCuentaUOtroUsuario() {
        SRegistroPapeleraGmail.VistaPrevia vista = registro.registrarVistaPrevia(
                "usuario-1", "cuenta-1", List.of(), 7, 0, List.of());

        assertTrue(registro.obtenerVistaPrevia(vista.id(), "usuario-2", "cuenta-1").isEmpty());
        assertTrue(registro.obtenerVistaPrevia(vista.id(), "usuario-1", "cuenta-2").isEmpty());
        assertTrue(registro.obtenerVistaPrevia("inexistente", "usuario-1", "cuenta-1").isEmpty());
        assertTrue(registro.obtenerVistaPrevia(null, "usuario-1", "cuenta-1").isEmpty());
    }

    @Test
    void soloUnaLimpiezaPorCuenta() {
        assertTrue(registro.intentarBloquearLimpieza("cuenta-1"));
        assertFalse(registro.intentarBloquearLimpieza("cuenta-1"));
        assertTrue(registro.intentarBloquearLimpieza("cuenta-2"));
        registro.liberarLimpieza("cuenta-1");
        assertTrue(registro.intentarBloquearLimpieza("cuenta-1"));
    }

    @Test
    void creaYConsultaUnLoteDeLimpiezaDeLaCuenta() {
        SRegistroPapeleraGmail.LoteLimpieza lote = registro.crearLote("usuario-1", "cuenta-1", 5);
        lote.setMovidos(3);
        lote.setFallidos(1);
        lote.setEstado("COMPLETADO");

        assertTrue(registro.consultarLote("usuario-1", "cuenta-1", lote.getId()).isPresent());
        assertEquals(5, registro.consultarLote("usuario-1", "cuenta-1", lote.getId()).orElseThrow().getTotal());
        assertTrue(registro.consultarLote("usuario-2", "cuenta-1", lote.getId()).isEmpty());
        assertTrue(registro.consultarLote("usuario-1", "cuenta-2", lote.getId()).isEmpty());
        assertTrue(registro.consultarLote("usuario-1", "cuenta-1", "inexistente").isEmpty());
    }
}
