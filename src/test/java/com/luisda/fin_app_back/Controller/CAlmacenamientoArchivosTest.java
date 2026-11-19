package com.luisda.fin_app_back.Controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.ConexionRequiereReconexionException;
import com.luisda.fin_app_back.Services.SAlmacenamientoArchivos;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CAlmacenamientoArchivosTest {
    private static final String RUTA = "/api/almacenamiento/conexiones/c1/archivos";

    private SUsuarioActivo usuarioActivo;
    private IAlmacenamientoConexiones repositorio;
    private SAlmacenamientoArchivos proveedor;
    private MockMvc mockMvc;
//     private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void preparar() {
        usuarioActivo = mock(SUsuarioActivo.class);
        repositorio = mock(IAlmacenamientoConexiones.class);
        proveedor = mock(SAlmacenamientoArchivos.class);
        CAlmacenamientoArchivos controlador = new CAlmacenamientoArchivos(proveedor, usuarioActivo, repositorio,
                "http://localhost:5173");
        mockMvc = MockMvcBuilders.standaloneSetup(controlador).build();
    }

    private void conSesion() {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        MAlmacenamientoConexion conexion = new MAlmacenamientoConexion("c1", usuario, "GOOGLE_DRIVE");
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "1101383776")).thenReturn(Optional.of(conexion));
    }

//     private static final Object[] ORIGEN = {HttpHeaders.ORIGIN, "http://localhost:5173"};

    @Test
    void sinSesionResponde401() throws Exception {
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.empty());
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("No hay una sesión activa."));
    }

    @Test
    void escrituraConOrigenNoAutorizadoResponde403() throws Exception {
        conSesion();
        mockMvc.perform(post(RUTA + "/carpeta")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Nueva\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conexionAjenaResponde404SinRevelarExistencia() throws Exception {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("otro");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "otro")).thenReturn(Optional.empty());
        mockMvc.perform(get(RUTA + "/f1").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenCaducadoResponde409ConCodigoDeReconexion() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ConexionRequiereReconexionException("No se pudo renovar el acceso al proveedor; reconecta la cuenta."));
        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONEXION_REQUIERE_RECONEXION"));
    }

    @Test
    void vistaInvalidaResponde400ConDetalle() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Vista no soportada: OTRA. Usa unidad, recientes, compartidos, destacados o papelera."));
        mockMvc.perform(get(RUTA).param("vista", "OTRA").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("Vista no soportada")));
    }

    @Test
    void conexionNoActivaResponde409ConMensajeAccionable() throws Exception {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        MAlmacenamientoConexion conexion = new MAlmacenamientoConexion("c1", usuario, "GOOGLE_DRIVE");
        conexion.setEstado("REQUIERE_RECONEXION");
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "1101383776")).thenReturn(Optional.of(conexion));
        mockMvc.perform(get(RUTA + "?vista=unidad").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONEXION_REQUIERE_RECONEXION"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("reconexión")));
    }

    @Test
    void listarConSesionYOrigenValidoDevuelveLaPagina() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any())).thenReturn(
                new SAlmacenamientoArchivos.PaginaArchivos(java.util.List.of(
                        new com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO(
                                "f1", "doc.pdf", "ARCHIVO", "application/pdf", 10L, "2026-11-10", "root",
                                false, true, false, "https://drive.google.com/d/f1")),
                        null, "root"));
        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivos[0].id").value("f1"));
    }
}
