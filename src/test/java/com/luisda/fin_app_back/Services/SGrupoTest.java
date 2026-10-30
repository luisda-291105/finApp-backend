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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SGrupoTest {
    private IGrupo grupos;
    private IFuentes fuentes;
    private SGrupo servicio;
    private MGrupo grupo;
    private MFuente fuente;

    @BeforeEach
    void preparar() {
        grupos = mock(IGrupo.class);
        fuentes = mock(IFuentes.class);
        servicio = new SGrupo(grupos, mock(IUsuario.class), fuentes);
        grupo = new MGrupo();
        grupo.setIdGrupo("grupo-1");
        grupo.setUsuario(usuario("usuario-1"));
        grupo.setFuentes(new ArrayList<>());
        fuente = new MFuente();
        fuente.setIdFuente("fuente-1");
        fuente.setUsuario(usuario("usuario-1"));
    }

    @Test
    void vinculaFuenteDelMismoUsuario() throws Exception {
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
        when(fuentes.findById("fuente-1")).thenReturn(Optional.of(fuente));
        when(grupos.save(grupo)).thenReturn(grupo);

        MGrupo resultado = servicio.vincularFuente("grupo-1", "fuente-1", "usuario-1");

        assertEquals(1, resultado.getFuentes().size());
        assertEquals("fuente-1", resultado.getFuentes().getFirst().getIdFuente());
        verify(grupos).save(grupo);
    }

    @Test
    void rechazaFuenteDeOtroUsuario() {
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
        fuente.setUsuario(usuario("usuario-2"));
        when(fuentes.findById("fuente-1")).thenReturn(Optional.of(fuente));

        Exception error = assertThrows(Exception.class, () -> servicio.vincularFuente("grupo-1", "fuente-1", "usuario-1"));

        assertEquals("La fuente no pertenece al usuario indicado", error.getMessage());
        verify(grupos, never()).save(grupo);
    }

    @Test
    void desvinculaSinEliminarLaFuente() throws Exception {
        grupo.getFuentes().add(fuente);
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
        when(grupos.save(grupo)).thenReturn(grupo);

        MGrupo resultado = servicio.desvincularFuente("grupo-1", "fuente-1", "usuario-1");

        assertEquals(0, resultado.getFuentes().size());
        verify(fuentes, never()).deleteById("fuente-1");
        verify(grupos).save(grupo);
    }

    @Test
    void modificaSoloGrupoDelUsuarioIndicado() throws Exception {
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
        MGrupo cambios = new MGrupo();
        cambios.setNombre("Grupo editado");
        cambios.setDescripcion("Descripción editada");
        when(grupos.save(grupo)).thenReturn(grupo);

        MGrupo resultado = servicio.modificar(cambios, "grupo-1", "usuario-1");

        assertEquals("Grupo editado", resultado.getNombre());
        assertEquals("Descripción editada", resultado.getDescripcion());
        verify(grupos).save(grupo);
    }

    @Test
    void rechazaEliminarGrupoDeOtroUsuario() {
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));

        assertThrows(Exception.class, () -> servicio.eliminar("grupo-1", "usuario-2"));

        verify(grupos, never()).deleteById("grupo-1");
    }

    @Test
    void eliminaGrupoDelUsuarioSinBorrarFuentes() throws Exception {
        grupo.getFuentes().add(fuente);
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));

        servicio.eliminar("grupo-1", "usuario-1");

        verify(grupos).deleteById("grupo-1");
        verify(fuentes, never()).deleteById("fuente-1");
    }

    private MUsuario usuario(String id) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        return usuario;
    }
}
