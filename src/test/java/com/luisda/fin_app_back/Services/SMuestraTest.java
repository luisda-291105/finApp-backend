package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MMuestra;
import com.luisda.fin_app_back.Repository.IMuestraRepository;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SMuestraTest {
    private IMuestraRepository muestras;
    private IUsuario usuarios;
    private SMuestra servicio;
    private MMuestra primera;
    private MMuestra segunda;
    @BeforeEach
    void preparar() {
        muestras = mock(IMuestraRepository.class);
        usuarios = mock(IUsuario.class);
        servicio = new SMuestra(muestras, usuarios);
        primera = muestra(1L);
        segunda = muestra(2L);
        when(usuarios.existsById("usuario-1")).thenReturn(true);
        when(muestras.findByUsuarioId("usuario-1")).thenReturn(List.of(primera, segunda));
    }

    @Test
    void guardaElNuevoOrdenSinPerderLaConfiguracionDeCadaMuestra() throws Exception {
        servicio.reordenar("usuario-1", List.of(2L, 1L));

        verify(segunda).setDatos("{\"fuente\":\"correo\",\"ordenDashboard\":0}");
        verify(primera).setDatos("{\"fuente\":\"contactos\",\"ordenDashboard\":1}");
        verify(muestras).saveAll(List.of(primera, segunda));
    }

    @Test
    void rechazaOrdenIncompletoSinGuardarCambios() {
        assertThrows(IllegalArgumentException.class, () -> servicio.reordenar("usuario-1", List.of(1L)));

        verify(muestras, never()).saveAll(List.of(primera, segunda));
    }

    private MMuestra muestra(Long id) {
        MMuestra muestra = mock(MMuestra.class);
        when(muestra.getId()).thenReturn(id);
        when(muestra.getDatos()).thenReturn(id.equals(1L) ? "{\"fuente\":\"contactos\"}" : "{\"fuente\":\"correo\"}");
        return muestra;
    }
}
