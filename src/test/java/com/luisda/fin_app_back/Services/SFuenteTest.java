package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SFuenteTest {
    private IFuentes fuentes;
    private IGrupo grupos;
    private SFuente servicio;
    private MFuente fuente;

    @BeforeEach
    void preparar() {
        fuentes = mock(IFuentes.class);
        grupos = mock(IGrupo.class);
        servicio = new SFuente(fuentes, mock(IUsuario.class), grupos);
        fuente = new MFuente();
        fuente.setIdFuente("fuente-1");
        fuente.setTipo("CORREO");
        fuente.setContenido("uno@ejemplo.test");
        fuente.setEstado(true);
        fuente.setUsuario(usuario("usuario-1"));
    }

    @Test
    void eliminaFuenteDelUsuarioYDesvinculaGruposSinDejarRelacionesRotas() throws Exception {
        MGrupo grupo = new MGrupo();
        grupo.setFuentes(new ArrayList<>(List.of(fuente)));
        when(fuentes.findById("fuente-1")).thenReturn(Optional.of(fuente));
        when(grupos.findByFuentes_IdFuente("fuente-1")).thenReturn(List.of(grupo));

        servicio.eliminar("fuente-1", "usuario-1");

        assertEquals(0, grupo.getFuentes().size());
        verify(grupos).save(grupo);
        verify(fuentes).deleteById("fuente-1");
    }

    @Test
    void impideEliminarFuenteDeOtroUsuario() {
        when(fuentes.findById("fuente-1")).thenReturn(Optional.of(fuente));

        Exception error = assertThrows(Exception.class, () -> servicio.eliminar("fuente-1", "usuario-2"));

        assertEquals("La fuente no pertenece al usuario indicado", error.getMessage());
        verify(fuentes, never()).deleteById("fuente-1");
    }

    @Test
    void actualizaUnaFuenteSoloSiPerteneceAlUsuario() throws Exception {
        MFuente cambios = new MFuente();
        cambios.setTipo(" telefono ");
        cambios.setContenido(" +573001234567 ");
        cambios.setEstado(false);
        when(fuentes.findById("fuente-1")).thenReturn(Optional.of(fuente));
        when(fuentes.contarDuplicadasExceptoId("usuario-1", "fuente-1", "TELEFONO", "+573001234567")).thenReturn(0L);
        when(fuentes.save(fuente)).thenReturn(fuente);

        MFuente actualizada = servicio.modificar(cambios, "fuente-1", "usuario-1");

        assertEquals("TELEFONO", actualizada.getTipo());
        assertEquals("+573001234567", actualizada.getContenido());
        assertEquals(false, actualizada.isEstado());
    }

    private MUsuario usuario(String id) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        return usuario;
    }
}
