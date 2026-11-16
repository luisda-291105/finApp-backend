package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.AlmacenamientoProveedorNoSoportado;
import com.luisda.fin_app_back.Services.SAlmacenamientoArchivos;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del contrato HTTP de escritura y streaming sobre las conexiones de almacenamiento:
 * crear carpeta, subir, renombrar/mover, copiar, papelera, permisos, descarga en lote y
 * traducción de errores del proveedor a estados HTTP.
 */
class CAlmacenamientoArchivosEscrituraTest {

    private static final String RUTA = "/api/almacenamiento/conexiones/c1/archivos";
    private static final String ORIGEN_VALIDO = "http://localhost:5173";

    private SUsuarioActivo usuarioActivo;
    private IAlmacenamientoConexiones repositorio;
    private SAlmacenamientoArchivos proveedor;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        usuarioActivo = mock(SUsuarioActivo.class);
        repositorio = mock(IAlmacenamientoConexiones.class);
        proveedor = mock(SAlmacenamientoArchivos.class);
        CAlmacenamientoArchivos controlador = new CAlmacenamientoArchivos(proveedor, usuarioActivo, repositorio,
                "http://localhost:5173,https://finapp.luisda.org");
        mockMvc = MockMvcBuilders.standaloneSetup(controlador).build();
    }

    private void conSesion() {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        MAlmacenamientoConexion conexion = new MAlmacenamientoConexion("c1", usuario, "GOOGLE_DRIVE");
        conexion.setEstado("ACTIVA");
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "1101383776")).thenReturn(Optional.of(conexion));
    }

    private AlmacenamientoArchivoDTO archivo(String id, String nombre) {
        return new AlmacenamientoArchivoDTO(id, nombre, AlmacenamientoArchivoDTO.TIPO_ARCHIVO, "application/pdf",
                10L, "2026-11-10", "root", false, true, false, "https://drive.google.com/d/" + id);
    }

    @Test
    void crearCarpetaSinNombreResponde400() throws Exception {
        conSesion();
        mockMvc.perform(post(RUTA + "/carpeta")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"carpetaId\":\"root\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Indica el nombre de la carpeta."));
    }

    @Test
    void crearCarpetaDevuelve201ConElArchivoCreado() throws Exception {
        conSesion();
        when(proveedor.crearCarpeta(any(), eq("root"), eq("Finanzas"))).thenReturn(
                new AlmacenamientoArchivoDTO("c9", "Finanzas", AlmacenamientoArchivoDTO.TIPO_CARPETA,
                        "application/vnd.google-apps.folder", null, "2026-11-16", "root", false, true, false, null));

        mockMvc.perform(post(RUTA + "/carpeta")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"carpetaId\":\"root\",\"nombre\":\"Finanzas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("c9"))
                .andExpect(jsonPath("$.tipo").value("CARPETA"));
    }

    @Test
    void elOrigenPermitidoSeComparaSinDistinguirMayusculasNiBarraFinal() throws Exception {
        conSesion();
        when(proveedor.crearCarpeta(any(), any(), any())).thenReturn(archivo("c9", "Finanzas"));

        mockMvc.perform(post(RUTA + "/carpeta")
                        .header(HttpHeaders.ORIGIN, "HTTPS://FinApp.Luisda.Org/")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Finanzas\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void subirSinArchivoResponde400() throws Exception {
        conSesion();
        mockMvc.perform(multipart(RUTA + "/subir")
                        .file(new MockMultipartFile("archivo", "", "text/plain", new byte[0]))
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona un archivo para subir."));
    }

    @Test
    void subirDevuelve201YEnviaElContenidoALProveedor() throws Exception {
        conSesion();
        when(proveedor.subir(any(), any(), any(), any(), any())).thenReturn(archivo("f2", "dato.txt"));

        mockMvc.perform(multipart(RUTA + "/subir")
                        .file(new MockMultipartFile("archivo", "dato.txt", "text/plain", "hola".getBytes()))
                        .param("idPadre", "carpeta-1")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("f2"));

        ArgumentCaptor<byte[]> contenido = ArgumentCaptor.forClass(byte[].class);
        verify(proveedor).subir(any(), eq("carpeta-1"), eq("dato.txt"), eq("text/plain"), contenido.capture());
        assertEquals("hola", new String(contenido.getValue()));
    }

    @Test
    void subirEliminaLasRutasDelNombreOriginalParaEvitarRecorridos() throws Exception {
        conSesion();
        when(proveedor.subir(any(), any(), any(), any(), any())).thenReturn(archivo("f3", "passwd"));

        mockMvc.perform(multipart(RUTA + "/subir")
                        .file(new MockMultipartFile("archivo", "../../etc/passwd", "text/plain", "x".getBytes()))
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isCreated());

        verify(proveedor).subir(any(), any(), eq("passwd"), any(), any());
    }

    @Test
    void actualizarDelegaNombreDestacarYNuevoPadre() throws Exception {
        conSesion();
        when(proveedor.actualizar(any(), eq("f1"), eq("Reporte"), eq("padre-2"), eq(true)))
                .thenReturn(archivo("f1", "Reporte"));

        mockMvc.perform(patch(RUTA + "/f1")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"nombre\":\"Reporte\",\"idNuevoPadre\":\"padre-2\",\"destacar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Reporte"));
    }

    @Test
    void copiarDevuelve201() throws Exception {
        conSesion();
        when(proveedor.copiar(any(), eq("f1"), eq("destino"))).thenReturn(archivo("f1-copia", "doc.pdf"));

        mockMvc.perform(post(RUTA + "/f1/copiar")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"carpetaId\":\"destino\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("f1-copia"));
    }

    @Test
    void moverALaPapeleraYRestaurarDevuelven204() throws Exception {
        conSesion();
        mockMvc.perform(delete(RUTA + "/f1").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(RUTA + "/f1/restaurar").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isNoContent());
        verify(proveedor).aPapelera(any(), eq("f1"));
        verify(proveedor).restaurar(any(), eq("f1"));
    }

    @Test
    void eliminarDefinitivamenteDevuelve204() throws Exception {
        conSesion();
        mockMvc.perform(delete(RUTA + "/f1/permanente").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isNoContent());
        verify(proveedor).eliminarDefinitivo(any(), eq("f1"));
    }

    @Test
    void eliminarDefinitivamenteConOrigenNoAutorizadoResponde403() throws Exception {
        conSesion();
        mockMvc.perform(delete(RUTA + "/f1/permanente").header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example"))
                .andExpect(status().isForbidden());
        verify(proveedor, org.mockito.Mockito.never()).eliminarDefinitivo(any(), any());
    }

    @Test
    void vaciarPapeleraDevuelve204() throws Exception {
        conSesion();
        mockMvc.perform(delete(RUTA + "/papelera").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isNoContent());
        verify(proveedor).vaciarPapelera(any());
    }

    @Test
    void compartirSinRolResponde400() throws Exception {
        conSesion();
        mockMvc.perform(post(RUTA + "/f1/permisos")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"tipo\":\"USUARIO\",\"email\":\"ana@ejemplo.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Indica el rol de compartición."));
    }

    @Test
    void compartirDevuelve201ConElPermisoCreado() throws Exception {
        conSesion();
        when(proveedor.compartir(any(), eq("f1"), eq("EDITOR"), eq("USUARIO"), eq("ana@ejemplo.com")))
                .thenReturn(new com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO("perm1", "user", "writer",
                        "ana@ejemplo.com", "Ana"));

        mockMvc.perform(post(RUTA + "/f1/permisos")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"rol\":\"EDITOR\",\"tipo\":\"USUARIO\",\"email\":\"ana@ejemplo.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("writer"));
    }

    @Test
    void listarPermisosDevuelveLaLista() throws Exception {
        conSesion();
        when(proveedor.listarPermisos(any(), eq("f1"))).thenReturn(List.of(
                new com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO("perm1", "user", "reader", "ana@ejemplo.com", "Ana")));

        mockMvc.perform(get(RUTA + "/f1/permisos").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("perm1"));
    }

    @Test
    void revocarPermisoDevuelve204() throws Exception {
        conSesion();
        mockMvc.perform(delete(RUTA + "/f1/permisos/perm1").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isNoContent());
        verify(proveedor).revocarPermiso(any(), eq("f1"), eq("perm1"));
    }

    @Test
    void descargarEnLoteRespondeUnZip() throws Exception {
        conSesion();
        byte[] zip = new byte[]{80, 75, 3, 4};
        when(proveedor.descargarLote(any(), anyList())).thenReturn(zip);

        mockMvc.perform(post(RUTA + "/lote")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"ids\":[\"f1\",\"f2\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("lote-archivos.zip")))
                .andExpect(content().bytes(zip));
    }

    @Test
    void descargarEnLoteSinArchivosResponde400() throws Exception {
        conSesion();
        when(proveedor.descargarLote(any(), any())).thenReturn(null);

        mockMvc.perform(post(RUTA + "/lote")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO)
                        .contentType("application/json")
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se seleccionaron archivos válidos."));
    }

    @Test
    void elContenidoSeSirveComoAdjuntoConNombreSaneado() throws Exception {
        conSesion();
        when(proveedor.contenido(any(), eq("f1"), anyBoolean())).thenReturn(
                new SAlmacenamientoArchivos.RecursoContenido(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        3L, "application/pdf", "informe \"2026\".pdf"));

        mockMvc.perform(get(RUTA + "/f1/contenido").param("descargar", "true")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("informe _2026_.pdf")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void laPrevisualizacionSeSirveEnLinea() throws Exception {
        conSesion();
        when(proveedor.previsualizacion(any(), eq("f1"))).thenReturn(
                new SAlmacenamientoArchivos.RecursoContenido(new ByteArrayInputStream(new byte[]{7}),
                        1L, "image/png", "captura.png"));

        mockMvc.perform(get(RUTA + "/f1/previsualizacion").header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void elOrigenCampoYDireccionComponenElOrdenDeLaConsulta() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any())).thenReturn(
                new SAlmacenamientoArchivos.PaginaArchivos(List.of(), null, "root"));

        mockMvc.perform(get(RUTA).param("ordenCampo", "nombre").param("ordenDireccion", "desc")
                        .header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isOk());

        ArgumentCaptor<String> orden = ArgumentCaptor.forClass(String.class);
        verify(proveedor).listar(any(), any(), any(), any(), orden.capture(), any());
        assertEquals("NOMBRE_DESC", orden.getValue());
    }

    @Test
    void nextcloudConCredencialEsOperableAunqueNoEsteActiva() throws Exception {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        when(usuarioActivo.obtenerUsuario(any(HttpServletRequest.class))).thenReturn(Optional.of(usuario));
        MAlmacenamientoConexion conexion = new MAlmacenamientoConexion("c1", usuario, "NEXTCLOUD");
        conexion.setEstado("REQUIERE_RECONEXION");
        conexion.setCredencialCifrada("material-cifrado");
        when(repositorio.findByIdConexionAndUsuario_IdUsuario("c1", "1101383776")).thenReturn(Optional.of(conexion));
        when(proveedor.listar(any(), any(), any(), any(), any(), any())).thenReturn(
                new SAlmacenamientoArchivos.PaginaArchivos(List.of(), null, "root"));

        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isOk());
    }

    @Test
    void unErrorDelProveedorSeTraduceA502() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Google Drive no respondió (503)."));

        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value("Google Drive no respondió (503)."));
    }

    @Test
    void unProveedorNoSoportadoSeTraduceA400() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new AlmacenamientoProveedorNoSoportado("El proveedor no está soportado."));

        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El proveedor no está soportado."));
    }

    @Test
    void unErrorDesconocidoNoExponeDetallesInternos() throws Exception {
        conSesion();
        when(proveedor.listar(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("detalle interno de SQL"));

        mockMvc.perform(get(RUTA).header(HttpHeaders.ORIGIN, ORIGEN_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("No se pudo completar la operación. Inténtalo de nuevo más tarde."));
    }
}
