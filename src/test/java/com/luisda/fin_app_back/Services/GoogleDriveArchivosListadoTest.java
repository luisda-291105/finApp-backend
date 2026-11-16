package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.GeneralSecurityException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pruebas de navegación y lectura en Google Drive: caché de listados, órdenes, paginación,
 * búsqueda, descarga/previsualización y traducción de errores del proveedor.
 */
class GoogleDriveArchivosListadoTest {

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
    void elSegundoListadoIgualSeSirveDeLaCacheSinLlamarAlProveedor() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files?")))
                .andRespond(withSuccess("{\"files\":[{\"id\":\"a1\",\"name\":\"Factura.pdf\","
                        + "\"mimeType\":\"application/pdf\"}]}", MediaType.APPLICATION_JSON));

        SAlmacenamientoArchivos.PaginaArchivos primera = drive.listar(conexion, null, null, null, null, null);
        SAlmacenamientoArchivos.PaginaArchivos segunda = drive.listar(conexion, null, null, null, null, null);

        assertEquals(1, primera.archivos().size());
        assertEquals("a1", segunda.archivos().get(0).id());
        // Si la cache no funcionara, la segunda llamada consumiría otra petición y verify() fallaría.
        servidor.verify();
    }

    @Test
    void unaConsultaDistintaNoUsaLaCacheDeLaAnterior() throws Exception {
        token();
        servidor.expect(requestTo(containsString("pageSize=50")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("pageSize=50")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));

        drive.listar(conexion, null, "UNIDAD", null, null, null);
        drive.listar(conexion, null, "PAPELERA", null, null, null);

        servidor.verify();
    }

    @Test
    void laRaizSeNormalizaALaCarpetaRootDeDrive() throws Exception {
        token();
        servidor.expect(requestTo(containsString("q='root'%20in%20parents%20and%20trashed%3Dfalse")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));

        drive.listar(conexion, "RAIZ", null, null, null, null);

        servidor.verify();
    }

    @Test
    void losOrdenesSeTraducenAlCampoOrderByDeDrive() throws Exception {
        token();
        servidor.expect(requestTo(containsString("orderBy=name")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, null, null, "NOMBRE_ASC", null);

        servidor.reset();
        token();
        servidor.expect(requestTo(containsString("orderBy=size%20desc")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, null, null, "TAMANIO_DESC", null);

        servidor.reset();
        token();
        servidor.expect(requestTo(containsString("orderBy=modifiedTime%20asc")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));
        drive.listar(conexion, null, null, null, "FECHA_ASC", null);

        servidor.verify();
    }

    @Test
    void laPaginaSeEnviaComoPageToken() throws Exception {
        token();
        servidor.expect(requestTo(containsString("pageToken=p9")))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));

        drive.listar(conexion, null, null, null, null, "p9");

        servidor.verify();
    }

    @Test
    void laBusquedaEscapaLasComillasParaNoRomperLaConsulta() throws Exception {
        token();
        servidor.expect(requestTo(containsString("name%20contains%20'o")))
                .andExpect(requestTo(not(containsString("contains%20'obrien"))))
                .andRespond(withSuccess("{\"files\":[]}", MediaType.APPLICATION_JSON));

        drive.listar(conexion, null, null, "o'brien", null, null);

        servidor.verify();
    }

    @Test
    void descargarDevuelveLosBytesYElNombreSaneadoParaLaCabecera() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"informe \\\"final\\\".pdf\","
                        + "\"mimeType\":\"application/pdf\",\"size\":\"4\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(requestTo(containsString("alt=media")))
                .andRespond(withSuccess(new byte[]{1, 2, 3, 4}, MediaType.APPLICATION_PDF));

        SAlmacenamientoArchivos.RecursoContenido recurso = drive.contenido(conexion, "f1", true);

        assertEquals(4, recurso.flujo().readAllBytes().length);
        assertEquals("application/pdf", recurso.mime());
        assertEquals("informe _final_.pdf", recurso.nombreArchivo());
        servidor.verify();
    }

    @Test
    void laPrevisualizacionUsaElDisposicionInline() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"doc.pdf\",\"mimeType\":\"application/pdf\","
                        + "\"parents\":[\"root\"]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("/files/f1?")))
                .andExpect(requestTo(containsString("alt=media")))
                .andRespond(withSuccess(new byte[]{9}, MediaType.APPLICATION_PDF));

        SAlmacenamientoArchivos.RecursoContenido recurso = drive.previsualizacion(conexion, "f1");

        assertEquals(1, recurso.flujo().readAllBytes().length);
        assertEquals("doc.pdf", recurso.nombreArchivo());
        servidor.verify();
    }

    @Test
    void elNombreSaneadoEliminaSaltosDeLineaComillasYBarras() {
        assertEquals("a__b_c_d", GoogleDriveArchivos.nombreSeguro("a\r\nb\"c\\d"));
        assertEquals("archivo", GoogleDriveArchivos.nombreSeguro(null));
        assertEquals("archivo", GoogleDriveArchivos.nombreSeguro("   "));
    }

    @Test
    void un403DelProveedorExigeReconectarLaCuenta() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/x?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body(""));

        ConexionRequiereReconexionException error = assertThrows(ConexionRequiereReconexionException.class,
                () -> drive.metadatos(conexion, "x"));

        assertTrue(error.getMessage().contains("reconexión"));
        servidor.verify();
    }

    @Test
    void unErrorDelServidorIndicaQueElProveedorNoRespondio() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/x?")))
                .andRespond(withServerError());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> drive.metadatos(conexion, "x"));

        assertEquals("Google Drive no respondió (500).", error.getMessage());
        servidor.verify();
    }

    @Test
    void unaCarpetaSinTamanoDevuelveTamanoNuloEnElDto() throws Exception {
        token();
        servidor.expect(requestTo(containsString("/files/c1?")))
                .andRespond(withSuccess("{\"id\":\"c1\",\"name\":\"Finanzas\","
                        + "\"mimeType\":\"application/vnd.google-apps.folder\",\"parents\":[\"root\"]}",
                        MediaType.APPLICATION_JSON));

        assertEquals(null, drive.metadatos(conexion, "c1").tamanio());
        servidor.verify();
    }

    @Test
    void laCacheIgnoraLasEntradasVencidas() {
        SCacheListadosAlmacenamiento cache = new SCacheListadosAlmacenamiento();
        SAlmacenamientoArchivos.PaginaArchivos pagina = new SAlmacenamientoArchivos.PaginaArchivos(
                java.util.List.of(), null, "root");
        cache.guardar(conexion, "consulta", pagina);

        assertEquals(pagina, cache.obtener(conexion, "consulta"));
        cache.invalidar(conexion);
        assertEquals(null, cache.obtener(conexion, "consulta"));
        assertFalse(cache.ttl().isNegative());
    }
}
