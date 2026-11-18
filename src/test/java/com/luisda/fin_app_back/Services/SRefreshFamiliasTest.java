package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Config.SesionConfig;
import com.luisda.fin_app_back.Model.MRefreshFamilia;
import com.luisda.fin_app_back.Repository.IRefreshFamilia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SRefreshFamiliasTest {
    private static final String FAMILIA = "familia-1";
    private static final String USUARIO = "usuario-1";
    private static final String IP = "203.0.113.9";

    private IRefreshFamilia repositorio;
    private SesionConfig config;
    private JwtTokenService tokens;
    private SRefreshFamilias servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(IRefreshFamilia.class);
        config = new SesionConfig();
        ReflectionTestUtils.setField(config, "reuseGraceSeconds", 15L);
        ReflectionTestUtils.setField(config, "absoluteSeconds", 28800L);
        tokens = new JwtTokenService(config);
        ReflectionTestUtils.setField(tokens, "secret",
                Base64.getEncoder().encodeToString(new byte[32]));
        servicio = new SRefreshFamilias(repositorio, config, tokens);
    }

    private MRefreshFamilia familia(Instant ahora, String hashVigente, String hashAnterior) {
        MRefreshFamilia familia = new MRefreshFamilia(FAMILIA, USUARIO, hashVigente, ahora,
                ahora.minusSeconds(60), ahora.plusSeconds(28800), 0L);
        familia.setHashAnterior(hashAnterior);
        return familia;
    }

    @Test
    void crearGuardaSoloElHashDelRefresh() {
        Instant ahora = Instant.now();
        String hash = tokens.hash("refresh-en-claro");
        when(repositorio.save(any(MRefreshFamilia.class))).thenAnswer(inv -> inv.getArgument(0));

        MRefreshFamilia creada = servicio.crear(FAMILIA, USUARIO, hash, ahora,
                ahora.plusSeconds(28800), 3L);

        assertEquals(hash, creada.getHashActual());
        assertNull(creada.getHashAnterior());
        assertEquals(3L, creada.getCredentialVersion());
        assertEquals(ahora, creada.getUsedAt());
        assertFalse(creada.estaRevocada());
    }

    @Test
    void rotarConservaElHashAnteriorParaDetectarReuso() {
        Instant ahora = Instant.now();
        MRefreshFamilia familia = familia(ahora, tokens.hash("r1"), null);
        String hashNuevo = tokens.hash("r2");

        servicio.rotar(familia, hashNuevo, ahora.plusSeconds(5));

        assertEquals(hashNuevo, familia.getHashActual());
        assertEquals(tokens.hash("r1"), familia.getHashAnterior());
        assertEquals(ahora.plusSeconds(5), familia.getUsedAt());
        assertEquals(SRefreshFamilias.ResultadoReuso.VIGENTE,
                servicio.evaluarReuso(familia, hashNuevo, IP, USUARIO, ahora.plusSeconds(6)));
    }

    @Test
    void reusoDelVigenteEsValidoYDentroDeGraciaSeToleraElAnterior() {
        Instant ahora = Instant.now();
        String vigente = tokens.hash("r2");
        String anterior = tokens.hash("r1");
        MRefreshFamilia familia = familia(ahora, vigente, anterior);

        assertEquals(SRefreshFamilias.ResultadoReuso.VIGENTE,
                servicio.evaluarReuso(familia, vigente, IP, USUARIO, ahora.plusSeconds(10)));
        assertEquals(SRefreshFamilias.ResultadoReuso.ANTERIOR_DENTRO_GRACIA,
                servicio.evaluarReuso(familia, anterior, IP, USUARIO, ahora.plusSeconds(15)));
        assertFalse(familia.estaRevocada());
        verify(repositorio, never()).save(any());
    }

    @Test
    void reusoDelAnteriorFueraDeGraciaRevocaLaFamilia() {
        Instant ahora = Instant.now();
        MRefreshFamilia familia = familia(ahora, tokens.hash("r2"), tokens.hash("r1"));
        when(repositorio.findById(FAMILIA)).thenReturn(Optional.of(familia));

        SRefreshFamilias.ResultadoReuso resultado = servicio.evaluarReuso(
                familia, tokens.hash("r1"), IP, USUARIO, ahora.plusSeconds(16));

        assertEquals(SRefreshFamilias.ResultadoReuso.REUSO_DETECTADO, resultado);
        assertTrue(familia.estaRevocada());
        verify(repositorio).save(familia);
    }

    @Test
    void hashDesconocidoNoRevocaPeroSeRechaza() {
        Instant ahora = Instant.now();
        MRefreshFamilia familia = familia(ahora, tokens.hash("r2"), tokens.hash("r1"));

        assertEquals(SRefreshFamilias.ResultadoReuso.DESCONOCIDO,
                servicio.evaluarReuso(familia, tokens.hash("r-ajeno"), IP, USUARIO, ahora));
        assertFalse(familia.estaRevocada());
    }

    @Test
    void revocarTodasDelUsuarioMarcaSoloLasActivas() {
        Instant ahora = Instant.now();
        MRefreshFamilia activa = familia(ahora, tokens.hash("a"), null);
        MRefreshFamilia yaRevocada = familia(ahora, tokens.hash("b"), null);
        yaRevocada.setRevokedAt(ahora.minusSeconds(10));
        when(repositorio.findByIdUsuario(USUARIO)).thenReturn(List.of(activa, yaRevocada));

        int revocadas = servicio.revocarTodasDelUsuario(USUARIO, ahora);

        assertEquals(1, revocadas);
        assertTrue(activa.estaRevocada());
        verify(repositorio, times(1)).save(activa);
        verify(repositorio, never()).save(yaRevocada);
    }

    @Test
    void limpiarVencidasUsaElTopeAbsolutoMasLaGracia() {
        servicio.limpiarVencidas();
        verify(repositorio).deleteByAbsoluteExpiresAtBefore(any(Instant.class));
    }
}
