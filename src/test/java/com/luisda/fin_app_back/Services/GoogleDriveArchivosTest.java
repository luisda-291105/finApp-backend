package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleDriveArchivosTest {
    private static final String BASE = "https://www.googleapis.com/drive/v3/files";

    private SAlmacenamientoTokens tokens;
    private GoogleDriveArchivos drive;
    private MockRestServiceServer servidor;
    private MAlmacenamientoConexion conexion;

    @BeforeEach
    void preparar() {
        tokens = mock(SAlmacenamientoTokens.class);
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        drive = new GoogleDriveArchivos(tokens, new SCacheListadosAlmacenamiento(), constructor);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        conexion = new MAlmacenamientoConexion("conexion-1", usuario, SAlmacenamientoConexiones.PROVEEDOR_GOOGLE_DRIVE);
    }

    private void token() throws GeneralSecurityException {
        when(tokens.tokenVigente(conexion)).thenReturn("token-de-prueba");
        doAnswer(invocacion -> {
            conexion.setEstado("REQUIERE_RECONEXION");
            return null;
        }).when(tokens).marcarReconexion(any(MAlmacenamientoConexion.class), any());
    }

    @Test
    void mapeaArchivosDeUnaCarpetaYConstruyeLaConsulta() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-de-prueba"))
                .andExpect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("q='root'%20in%20parents%20and%20trashed%3Dfalse"),
                        org.hamcrest.Matchers.containsString("supportsAllDrives=true"))))
                .andRespond(withSuccess(
                        "{\"files\":[{\"id\":\"a1\",\"name\":\"Presupuesto.xlsx\",\"mimeType\":\"application/vnd.ms-excel\","
                                + "\"size\":\"2048\",\"modifiedTime\":\"2026-11-10T12:00:00.000Z\",\"parents\":[\"root\"],"
                                + "\"shared\":true,\"starred\":true,\"webViewLink\":\"https://drive.google.com/d/a1\","
                                + "\"capabilities\":{\"canDelete\":true}}],\"nextPageToken\":\"p2\"}",
                        MediaType.APPLICATION_JSON));

        SAlmacenamientoArchivos.PaginaArchivos pagina = drive.listar(conexion, null, null, null, null, null);

        assertEquals(1, pagina.archivos().size());
        AlmacenamientoArchivoDTO archivo = pagina.archivos().get(0);
        assertEquals("a1", archivo.id());
        assertEquals("Presupuesto.xlsx", archivo.nombre());
        assertEquals(AlmacenamientoArchivoDTO.TIPO_ARCHIVO, archivo.tipo());
        assertEquals(2048L, archivo.tamanio());
        assertEquals("root", archivo.idPadre());
        assertEquals(true, archivo.compartido());
        assertEquals(true, archivo.destacado());
        assertEquals(true, archivo.eliminable());
        assertEquals("p2", pagina.siguientePagina());
        servidor.verify();
    }

    @Test
    void papeleraConstruyeQDeTrashedYLosArchivosDeBusquedaFiltranPorNombre() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("q=trashed%3Dtrue")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, "PAPELERA", null, null, null);

        servidor.reset();
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("name%20contains%20'factura'")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, null, "factura", null, null);
        servidor.verify();
    }

    @Test
    void metadatosMapeaCarpetaSinTamano() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/f1?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"f1\",\"name\":\"Finanzas\",\"mimeType\":\"application/vnd.google-apps.folder\","
                                + "\"modifiedTime\":\"2026-11-09T08:00:00.000Z\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));
        AlmacenamientoArchivoDTO carpeta = drive.metadatos(conexion, "f1");
        assertEquals(AlmacenamientoArchivoDTO.TIPO_CARPETA, carpeta.tipo());
        assertNotNull(carpeta.fechaModificacion());
        servidor.verify();
    }

    @Test
    void archivoInexistenteResponde404DelProveedor() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/no-existe?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND).body(""));
        assertThrows(AlmacenamientoRecursoNoEncontrado.class, () -> drive.metadatos(conexion, "no-existe"));
        servidor.verify();
    }

    @Test
    void cuotaAgotadaDaMensajeClaro() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/x?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body(""));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> drive.metadatos(conexion, "x"));
        assertEquals("Se agotó la cuota del proveedor; inténtalo más tarde.", error.getMessage());
        servidor.verify();
    }

    @Test
    void rechazo401FuerzaRenovacionYAlSegundoRechazoExigeReconexion() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/x?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body(""));
        // Tras forzar la renovación se reintenta una vez; vuelve a fallar → REQUIERE_RECONEXION.
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/x?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body(""));
        assertThrows(ConexionRequiereReconexionException.class, () -> drive.metadatos(conexion, "x"));
        assertEquals("REQUIERE_RECONEXION", conexion.getEstado());
        servidor.verify();
    }

    @Test
    void rechazo401ConRenovacionExitosaRecuperaLaOperacion() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/x?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body(""));
        when(tokens.tokenVigente(conexion, true)).thenReturn("token-nuevo");
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/x?")))
                .andExpect(header("Authorization", "Bearer token-nuevo"))
                .andRespond(withSuccess(
                        "{\"id\":\"x\",\"name\":\"ok.pdf\",\"mimeType\":\"application/pdf\","
                                + "\"modifiedTime\":\"2026-11-10T12:00:00.000Z\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));
        assertEquals("x", drive.metadatos(conexion, "x").id());
        servidor.verify();
    }

    @Test
    void vistasSoportadasConstruyenQPorVista() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("q=sharedWithMe%3Dtrue")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, "compartidos", null, null, null);

        servidor.reset();
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("q=starred%3Dtrue%20and%20trashed%3Dfalse")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, "destacados", null, null, null);

        servidor.reset();
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("q=trashed%3Dfalse")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("orderBy=modifiedTime%20desc")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, "recientes", null, null, null);
    }

    @Test
    void vistaInvalidaSeRechazaCon400() {
        assertThrows(IllegalArgumentException.class, () -> drive.listar(conexion, null, "otra", null, null, null));
    }

    @Test
    void loteDevuelveZipConLosArchivosIndicados() throws Exception {
        token();
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/l1?")))
                .andRespond(withSuccess(
                        "{\"id\":\"l1\",\"name\":\"informe.pdf\",\"mimeType\":\"application/pdf\","
                                + "\"modifiedTime\":\"2026-11-10T12:00:00.000Z\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/files/l1?")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam("alt", "media"))
                .andRespond(withSuccess(new byte[]{1, 2, 3, 4}, MediaType.APPLICATION_PDF));

        byte[] zip = drive.descargarLote(conexion, List.of("l1"));
        assertEquals("PK", new String(java.util.Arrays.copyOfRange(zip, 0, 2), java.nio.charset.StandardCharsets.ISO_8859_1));
        servidor.verify();
    }
}
