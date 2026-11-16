package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pruebas de las operaciones de escritura contra Google Drive (crear, subir, mover, compartir,
 * papelera). Verifican el método, la URL, el cuerpo enviado y el DTO devuelto, sin salir a Internet.
 */
class GoogleDriveArchivosEscrituraTest {

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

    private static String jsonArchivo(String id, String nombre, String mime) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + nombre + "\",\"mimeType\":\"" + mime + "\",\"parents\":[\"root\"]}";
    }

    @Test
    void crearCarpetaEnviaNombreYMimeDeCarpetaEnLaRaiz() throws Exception {
        token();
        servidor.expect(requestTo(containsString("https://www.googleapis.com/drive/v3/files?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-de-prueba"))
                .andExpect(content().string(containsString("\"name\":\"Finanzas 2026\"")))
                .andExpect(content().string(containsString("application/vnd.google-apps.folder")))
                .andExpect(content().string(containsString("\"parents\":[\"root\"]")))
                .andRespond(withSuccess(jsonArchivo("c9", "Finanzas 2026", GoogleDriveArchivos.MIME_CARPETA),
                        MediaType.APPLICATION_JSON));

        AlmacenamientoArchivoDTO carpeta = drive.crearCarpeta(conexion, null, "Finanzas 2026");

        assertEquals("c9", carpeta.id());
        assertEquals("Finanzas 2026", carpeta.nombre());
        assertEquals(AlmacenamientoArchivoDTO.TIPO_CARPETA, carpeta.tipo());
        servidor.verify();
    }

    @Test
    void crearCarpetaEnUnaCarpetaIndicadaUsaEsePadre() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"parents\":[\"carpeta-padre\"]")))
                .andRespond(withSuccess(jsonArchivo("c10", "Subcarpeta", GoogleDriveArchivos.MIME_CARPETA),
                        MediaType.APPLICATION_JSON));

        drive.crearCarpeta(conexion, "carpeta-padre", "Subcarpeta");

        servidor.verify();
    }

    @Test
    void escribirInvalidaLaCacheDelListadoDeLaConexion() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files?")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonArchivo("c9", "Nueva", GoogleDriveArchivos.MIME_CARPETA),
                        MediaType.APPLICATION_JSON));
        // La escritura invalidó la caché: el listado vuelve a consultar al proveedor (tercera petición).
        servidor.expect(requestTo(containsString("/files?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));

        drive.listar(conexion, null, null, null, null, null);
        drive.crearCarpeta(conexion, null, "Nueva");
        drive.listar(conexion, null, null, null, null, null);

        servidor.verify();
    }

    @Test
    void subirEnviaMetadatosYArchivoComoMultipart() throws Exception {
        token();
        servidor.expect(requestTo(containsString("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("multipart/form-data")))
                .andExpect(content().string(containsString("\"name\":\"presupuesto.xlsx\"")))
                .andExpect(content().string(containsString("\"parents\":[\"root\"]")))
                .andExpect(content().string(containsString("CON-TENIDO")))
                .andRespond(withSuccess("{\"id\":\"f2\",\"name\":\"presupuesto.xlsx\","
                        + "\"mimeType\":\"application/vnd.ms-excel\",\"size\":\"9\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));

        AlmacenamientoArchivoDTO archivo = drive.subir(conexion, null, "presupuesto.xlsx",
                "application/vnd.ms-excel", "CON-TENIDO".getBytes(StandardCharsets.UTF_8));

        assertEquals("f2", archivo.id());
        assertEquals(9L, archivo.tamanio());
        assertEquals(AlmacenamientoArchivoDTO.TIPO_ARCHIVO, archivo.tipo());
        servidor.verify();
    }

    @Test
    void renombrarEnviaPatchConElNuevoNombre() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"name\":\"Nuevo nombre\"")))
                .andRespond(withSuccess(jsonArchivo("f1", "Nuevo nombre", "application/pdf"), MediaType.APPLICATION_JSON));

        AlmacenamientoArchivoDTO renombrado = drive.actualizar(conexion, "f1", "Nuevo nombre", null, null);

        assertEquals("Nuevo nombre", renombrado.nombre());
        servidor.verify();
    }

    @Test
    void moverConsultaElPadreActualYEnviaAddParentsYRemoveParents() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"doc.pdf\",\"mimeType\":\"application/pdf\","
                        + "\"parents\":[\"padre-viejo\"]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(requestTo(containsString("addParents=padre-nuevo")))
                .andExpect(requestTo(containsString("removeParents=padre-viejo")))
                .andRespond(withSuccess(jsonArchivo("f1", "doc.pdf", "application/pdf"), MediaType.APPLICATION_JSON));

        drive.actualizar(conexion, "f1", null, "padre-nuevo", null);

        servidor.verify();
    }

    @Test
    void destacarEnviaElCampoStarred() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"starred\":true")))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"doc.pdf\",\"mimeType\":\"application/pdf\","
                        + "\"starred\":true,\"parents\":[\"root\"]}", MediaType.APPLICATION_JSON));

        AlmacenamientoArchivoDTO destacado = drive.actualizar(conexion, "f1", null, null, true);

        assertEquals(true, destacado.destacado());
        servidor.verify();
    }

    @Test
    void renombrarYDestacarEnLaMismaLlamadaEnviaAmbosCampos() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"name\":\"Reporte\"")))
                .andExpect(content().string(containsString("\"starred\":false")))
                .andRespond(withSuccess(jsonArchivo("f1", "Reporte", "application/pdf"), MediaType.APPLICATION_JSON));

        drive.actualizar(conexion, "f1", "Reporte", null, false);

        servidor.verify();
    }

    @Test
    void copiarUsaElEndpointCopyYElDestinoIndicado() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1/copy?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"parents\":[\"destino\"]")))
                .andRespond(withSuccess(jsonArchivo("f1-copia", "doc.pdf", "application/pdf"), MediaType.APPLICATION_JSON));

        AlmacenamientoArchivoDTO copia = drive.copiar(conexion, "f1", "destino");

        assertEquals("f1-copia", copia.id());
        servidor.verify();
    }

    @Test
    void enviarALaPapeleraMarcaTrashedYRestaurarLoDeshace() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"trashed\":true")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"trashed\":false")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        drive.aPapelera(conexion, "f1");
        drive.restaurar(conexion, "f1");

        servidor.verify();
    }

    @Test
    void vaciarPapeleraRecorreTodasLasPaginasYBorraCadaArchivo() throws Exception {
        token();
        servidor.expect(requestTo(containsString("q=trashed%3Dtrue")))
                .andRespond(withSuccess("{\"files\":[{\"id\":\"x1\"},{\"id\":\"x2\"}],\"nextPageToken\":\"p2\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("pageToken=p2")))
                .andRespond(withSuccess("{\"files\":[{\"id\":\"x3\"}]}", MediaType.APPLICATION_JSON));
        for (String id : List.of("x1", "x2", "x3")) {
            servidor.expect(requestTo(containsString("/files/" + id + "?")))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withSuccess());
        }

        drive.vaciarPapelera(conexion);

        servidor.verify();
    }

    @Test
    void listarPermisosMapeaIdTipoRolYTitular() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1/permissions?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"permissions\":[{\"id\":\"perm1\",\"type\":\"user\",\"role\":\"writer\","
                        + "\"emailAddress\":\"ana@ejemplo.com\",\"displayName\":\"Ana\"}]}", MediaType.APPLICATION_JSON));

        List<AlmacenamientoPermisoDTO> permisos = drive.listarPermisos(conexion, "f1");

        assertEquals(1, permisos.size());
        AlmacenamientoPermisoDTO permiso = permisos.get(0);
        assertEquals("perm1", permiso.id());
        assertEquals("user", permiso.tipo());
        assertEquals("writer", permiso.rol());
        assertEquals("ana@ejemplo.com", permiso.email());
        assertEquals("Ana", permiso.nombre());
        servidor.verify();
    }

    @Test
    void compartirTraduceElRolYTipoYEnviaElCorreoDelTitular() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1/permissions?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"role\":\"writer\"")))
                .andExpect(content().string(containsString("\"type\":\"user\"")))
                .andExpect(content().string(containsString("\"emailAddress\":\"ana@ejemplo.com\"")))
                .andRespond(withSuccess("{\"id\":\"perm2\",\"type\":\"user\",\"role\":\"writer\","
                        + "\"emailAddress\":\"ana@ejemplo.com\"}", MediaType.APPLICATION_JSON));

        AlmacenamientoPermisoDTO permiso = drive.compartir(conexion, "f1", "EDITOR", "USUARIO", "ana@ejemplo.com");

        assertEquals("perm2", permiso.id());
        assertEquals("writer", permiso.rol());
        assertEquals("user", permiso.tipo());
        servidor.verify();
    }

    @Test
    void compartirConLinkPublicoNoEnviaCorreo() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1/permissions?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"role\":\"reader\"")))
                .andExpect(content().string(containsString("\"type\":\"anyone\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("emailAddress"))))
                .andRespond(withSuccess("{\"id\":\"perm3\",\"type\":\"anyone\",\"role\":\"reader\"}",
                        MediaType.APPLICATION_JSON));

        drive.compartir(conexion, "f1", "LECTOR", "PUBLICO", null);

        servidor.verify();
    }

    @Test
    void revocarPermisoBorraElPermisoIndicado() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1/permissions/perm1?")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        drive.revocarPermiso(conexion, "f1", "perm1");

        servidor.verify();
    }

    @Test
    void eliminarDefinitivamenteBorraElArchivoEnElProveedor() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        drive.eliminarDefinitivo(conexion, "f1");

        servidor.verify();
    }

    @Test
    void descargarLoteIgnoraIdentificadoresVaciosYGeneraZip() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/l1?")))
                .andRespond(withSuccess(jsonArchivo("l1", "informe.pdf", "application/pdf"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files/l1?")))
                .andExpect(requestTo(containsString("alt=media")))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_PDF));

        byte[] zip = drive.descargarLote(conexion, java.util.Arrays.asList("", "l1", null));

        assertEquals("PK", new String(java.util.Arrays.copyOfRange(zip, 0, 2), StandardCharsets.ISO_8859_1));
        assertFalse(zip.length == 0);
        servidor.verify();
    }
}
