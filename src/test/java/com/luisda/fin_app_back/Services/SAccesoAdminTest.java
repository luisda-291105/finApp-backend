package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SAccesoAdminTest {
    private IUsuario repositorio;
    private SAccesoAdmin acceso;

    @BeforeEach
    void preparar() {
        repositorio = mock(IUsuario.class);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        usuario.setNombre("luis daniel perez pinedo");
        usuario.setCorreo("estudiosluisperez@gmail.com");
        when(repositorio.findById(anyString())).thenReturn(Optional.empty());
        when(repositorio.findById("1101383776")).thenReturn(Optional.of(usuario));
        when(repositorio.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repositorio.findByNombreIgnoreCase(anyString())).thenReturn(List.of(usuario));
    }

    private SAccesoAdmin crear(Map<String, String> configuracion) {
        SConfiguracionEntorno entorno = new SConfiguracionEntorno(configuracion);
        acceso = new SAccesoAdmin(entorno, repositorio);
        return acceso;
    }

    @Test
    void rechazaCredencialesIncorrectas() {
        crear(Map.of("LOGIN_USER", "admin", "LOGIN_PASSWORD", "secreto-admin",
                "FINAPP_ADMIN_USER_ID", "1101383776"));

        assertFalse(acceso.autenticar("admin", "incorrecta").isPresent());
        assertFalse(acceso.autenticar("otro", "secreto-admin").isPresent());
    }

    @Test
    void rechazaPeticionSinCredenciales() {
        crear(Map.of("LOGIN_USER", "admin", "LOGIN_PASSWORD", "secreto-admin"));

        assertFalse(acceso.autenticar(null, null).isPresent());
        assertFalse(acceso.autenticar("admin", "").isPresent());
    }

    @Test
    void deshabilitadoSinConfiguracion() {
        crear(Map.of());

        assertFalse(acceso.disponible());
        assertFalse(acceso.autenticar("admin", "secreto-admin").isPresent());
    }

    @Test
    void autenticaYResuelveCuentaAdministrada() {
        crear(Map.of("LOGIN_USER", "admin", "LOGIN_PASSWORD", "secreto-admin",
                "FINAPP_ADMIN_USER_ID", "1101383776"));

        assertTrue(acceso.disponible());
        Optional<MUsuario> usuario = acceso.autenticar("admin", "secreto-admin");
        assertTrue(usuario.isPresent());
        assertTrue(usuario.orElseThrow().getIdUsuario().equals("1101383776"));
    }
}
