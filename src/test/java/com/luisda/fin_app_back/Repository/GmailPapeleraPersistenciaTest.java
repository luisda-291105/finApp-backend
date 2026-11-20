package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailMensaje;
import com.luisda.fin_app_back.Model.MGmailPapeleraCategoria;
import com.luisda.fin_app_back.Model.MGmailPapeleraConfiguracion;
import com.luisda.fin_app_back.Model.MGmailPapeleraListaBlanca;
import com.luisda.fin_app_back.Model.MUsuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapeo y valores por defecto de los datos de la papelera de correos (spec 040, CD-02/CD-03).
 * Verifica que las entidades nuevas cargan y que las columnas/valores por defecto coinciden con
 * la migración `sql/075_papelera_correos.sql` (categorías desactivadas, antigüedad 7, límite 200,
 * sin permiso ampliado y sin estado de papelera).
 */
@SpringBootTest
@Transactional
class GmailPapeleraPersistenciaTest {

    @Autowired private EntityManager entityManager;
    @Autowired private IGmailPapeleraConfiguracion configuraciones;
    @Autowired private IGmailPapeleraCategoria categorias;
    @Autowired private IGmailPapeleraListaBlanca listaBlanca;

    @Test
    void configuracionNuevaUsaLosValoresPorDefectoDeCd03() {
        MFuente cuenta = prepararCuenta("cuenta-config");

        configuraciones.saveAndFlush(new MGmailPapeleraConfiguracion(cuenta));
        entityManager.clear();

        MGmailPapeleraConfiguracion guardada = configuraciones.findById("cuenta-config").orElseThrow();
        assertEquals(7, guardada.getAntiguedadMinimaDias());
        assertEquals(200, guardada.getLimitePorEjecucion());
        assertNotNull(guardada.getActualizadoEn());
    }

    @Test
    void mensajeNuevoNaceConPapeleraVaciaYFlagsDesactivados() {
        MFuente cuenta = prepararCuenta("cuenta-mensaje");
        entityManager.persist(new MGmailMensaje("mensaje-1", cuenta, "google-1", Instant.now()));
        entityManager.flush();
        entityManager.clear();

        MGmailMensaje mensaje = entityManager.find(MGmailMensaje.class, "mensaje-1");
        assertNull(mensaje.getPapeleraEstado());
        assertNull(mensaje.getPapeleraMotivo());
        assertNull(mensaje.getPapeleraCategoria());
        assertFalse(mensaje.isPapeleraMovidoAutomaticamente());
        assertFalse(mensaje.isAlertaMovimientoDescartada());
        assertFalse(mensaje.isPapeleraExenta());
    }

    @Test
    void categoriaNuevaNaceInactivaYSinAdvertencia() {
        MFuente cuenta = prepararCuenta("cuenta-categoria");

        categorias.saveAndFlush(new MGmailPapeleraCategoria("cat-1", cuenta, "CATEGORY_PROMOTIONS"));
        entityManager.clear();

        MGmailPapeleraCategoria guardada = categorias.findById("cat-1").orElseThrow();
        assertEquals("CATEGORY_PROMOTIONS", guardada.getCategoria());
        assertFalse(guardada.isActiva());
        assertFalse(guardada.isAdvertenciaAceptada());
        assertNull(guardada.getActivadaEn());
    }

    @Test
    void listaBlancaGuardaElRemitentePorCuenta() {
        MFuente cuenta = prepararCuenta("cuenta-lista");

        listaBlanca.saveAndFlush(new MGmailPapeleraListaBlanca("lb-1", cuenta, "banco@banco.com"));
        entityManager.clear();

        MGmailPapeleraListaBlanca guardada = listaBlanca.findById("lb-1").orElseThrow();
        assertEquals("banco@banco.com", guardada.getRemitente());
        assertNotNull(guardada.getCreadoEn());
        assertTrue(listaBlanca.existsByFuente_IdFuenteAndRemitente("cuenta-lista", "banco@banco.com"));
    }

    @Test
    void fuenteNuevaNoTieneScopeConcedido() {
        MFuente cuenta = prepararCuenta("cuenta-scope");

        assertNull(cuenta.getGmailScopeConcedido());
    }

    private MFuente prepararCuenta(String idFuente) {
        MUsuario usuario = new MUsuario("usuario-" + idFuente, "Usuario de prueba", "hash", LocalDate.now());
        entityManager.persist(usuario);
        MFuente cuenta = new MFuente(idFuente, "CORREO", idFuente + "@correo.com", true, LocalDate.now());
        cuenta.setUsuario(usuario);
        entityManager.persist(cuenta);
        return cuenta;
    }
}
