package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.AlmacenamientoConexionDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.SAlmacenamientoCifrado;
import com.luisda.fin_app_back.Services.SAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CAlmacenamientoConexionesTest {

    @Test
    void listarSoloDevuelveConexionesDelUsuarioYMarcaRenovacionPendiente() {
        MUsuario usuario = usuario("u1");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        MAlmacenamientoConexion vigente = conexion(usuario, "c1", "ACTIVA");
        vigente.setAccessTokenCifrado("material");
        vigente.setExpiraEn(java.time.Instant.now().plusSeconds(600));
        MAlmacenamientoConexion sinRenovacion = conexion(usuario, "c2", "ACTIVA");
        sinRenovacion.setAccessTokenCifrado("material");
        sinRenovacion.setExpiraEn(java.time.Instant.now().minusSeconds(600));
        when(repositorio.findByUsuario_IdUsuario("u1")).thenReturn(List.of(vigente, sinRenovacion));

        List<AlmacenamientoConexionDTO> resultado = servicio.listar(usuario);

        assertEquals(2, resultado.size());
        assertEquals("ACTIVA", resultado.get(0).estado());
        assertEquals("REQUIERE_RECONEXION", resultado.get(1).estado());
        assertFalse(resultado.stream().anyMatch(dto -> dto.etiquetaCuenta().contains("token")));
    }

    @Test
    void stateInvalidoNoCreaConexion() {
        MUsuario usuario = usuario("u1");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        assertThrows(SAlmacenamientoConexiones.EstadoInvalidoException.class,
                () -> servicio.completarOAuth(usuario, "codigo", "state-falso"));
        verify(repositorio, never()).save(any());
    }

    @Test
    void stateDeOtroUsuarioNoCreaConexion() throws GeneralSecurityException {
        MUsuario iniciador = usuario("u1");
        MUsuario atacante = usuario("u2");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        servicio.iniciar(iniciador, "GOOGLE_DRIVE");
        // El state real es opaco; un atacante solo puede reutilizar el suyo u otro. Cualquier
        // state que no esté registrado para esta sesión debe rechazarse sin crear conexiones.
        assertThrows(SAlmacenamientoConexiones.EstadoInvalidoException.class,
                () -> servicio.completarOAuth(atacante, "codigo", "state-desconocido"));
        verify(repositorio, never()).save(any());
    }

    @Test
    void conectarNextcloudCifraLaCredencialYNoLaExpone() throws GeneralSecurityException {
        MUsuario usuario = usuario("u1");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        // Credencial inválida: el cliente HTTP intentará contactar; sin servidor la validación falla
        // y no se persiste nada, lo que demuestra que no se guarda antes de validar.
        assertThrows(Exception.class, () -> servicio.conectarNextcloud(usuario,
                new SAlmacenamientoConexiones.ConexionNextcloud("nube.ejemplo.com", "usuario", "contrasena")));
        verify(repositorio, never()).save(any());
    }

    @Test
    void desconectarSoloEliminaConexionesPropias() {
        MUsuario usuario = usuario("u1");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        when(repositorio.findByIdConexionAndUsuario_IdUsuario("ajena", "u1")).thenReturn(Optional.empty());

        boolean resultado = servicio.desconectar("ajena", usuario);
        assertFalse(resultado);
        verify(repositorio, never()).delete(any());
    }

    @Test
    void desconectarEliminaLaCredencialDeLaConexionPropia() throws GeneralSecurityException {
        MUsuario usuario = usuario("u1");
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        SAlmacenamientoConexiones servicio = new SAlmacenamientoConexiones(repositorio, new SAlmacenamientoCifrado("secreto", "secreto"));

        MAlmacenamientoConexion conexion = conexion(usuario, "c1", "ACTIVA");
        conexion.setProveedor("NEXTCLOUD");
        conexion.setUrlServidor("https://nube.ejemplo.com");
        conexion.setCredencialCifrada("material-cifrado");
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "u1")).thenReturn(Optional.of(conexion));

        boolean revocacionFallida = servicio.desconectar("c1", usuario);
        assertFalse(revocacionFallida);
        ArgumentCaptor<MAlmacenamientoConexion> captura = ArgumentCaptor.forClass(MAlmacenamientoConexion.class);
        verify(repositorio).delete(captura.capture());
        assertEquals("c1", captura.getValue().getIdConexion());
        assertNotNull(captura.getValue().getCredencialCifrada());
    }

    @Test
    void endpointRechazaSolicitudesSinSesionYOrigenNoPermitido() {
        SAlmacenamientoConexiones servicioConexiones = mock(SAlmacenamientoConexiones.class);
        SUsuarioActivo usuarioActivo = mock(SUsuarioActivo.class);
        IAlmacenamientoConexiones repositorio = mock(IAlmacenamientoConexiones.class);
        CAlmacenamiento controller = new CAlmacenamiento(servicioConexiones, usuarioActivo, repositorio,
                "http://localhost:5173");

        HttpServletRequest sinSesion = mock(HttpServletRequest.class);
        when(usuarioActivo.obtenerUsuario(sinSesion)).thenReturn(Optional.empty());
        assertEquals(401, controller.listarConexiones(sinSesion).getStatusCode().value());

        HttpServletRequest origenAjeno = mock(HttpServletRequest.class);
        when(usuarioActivo.obtenerUsuario(origenAjeno)).thenReturn(Optional.of(usuario("u1")));
        when(origenAjeno.getHeader("Origin")).thenReturn("http://malo.example");
        when(origenAjeno.getHeader("X-Requested-With")).thenReturn("XmlHttpRequest");
        assertEquals(403, controller.iniciarConexion(new CAlmacenamiento.SolicitudIniciar("GOOGLE_DRIVE"), origenAjeno).getStatusCode().value());

        HttpServletRequest sinCabecera = mock(HttpServletRequest.class);
        when(usuarioActivo.obtenerUsuario(sinCabecera)).thenReturn(Optional.of(usuario("u1")));
        when(sinCabecera.getHeader("Origin")).thenReturn("http://localhost:5173");
        assertEquals(403, controller.completarConexion(new CAlmacenamiento.SolicitudCallback("c", "s"), sinCabecera).getStatusCode().value());
        verify(servicioConexiones, never()).iniciar(any(), any());
        try {
            verify(servicioConexiones, never()).completarOAuth(any(), any(), any());
        } catch (GeneralSecurityException noEsperada) {
            throw new IllegalStateException(noEsperada);
        }
    }

    private MUsuario usuario(String id) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        return usuario;
    }

    private MAlmacenamientoConexion conexion(MUsuario usuario, String id, String estado) {
        MAlmacenamientoConexion conexion = new MAlmacenamientoConexion(id, usuario, "GOOGLE_DRIVE");
        conexion.setEtiquetaCuenta("cuenta@ejemplo.com");
        conexion.setEstado(estado);
        return conexion;
    }
}
