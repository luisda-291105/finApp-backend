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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NextcloudArchivosTest {

    private static final String XML_LISTA = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/luisd/</d:href>
                <d:propstat><d:prop>
                  <d:resourcetype><d:collection/></d:resourcetype>
                  <d:getlastmodified>Tue, 10 Nov 2026 12:00:00 GMT</d:getlastmodified>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/luisd/presupuesto.xlsx</d:href>
                <d:propstat><d:prop>
                  <d:getcontentlength>2048</d:getcontentlength>
                  <d:getcontenttype>application/vnd.ms-excel</d:getcontenttype>
                  <d:getlastmodified>Tue, 10 Nov 2026 11:00:00 GMT</d:getlastmodified>
                  <d:resourcetype/>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/luisd/Finanzas/</d:href>
                <d:propstat><d:prop>
                  <d:resourcetype><d:collection/></d:resourcetype>
                  <d:getlastmodified>Tue, 10 Nov 2026 10:00:00 GMT</d:getlastmodified>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
            """;

    private NextcloudArchivos nextcloud;
    private MockRestServiceServer servidor;
    private MAlmacenamientoConexion conexion;

    @BeforeEach
    void preparar() throws GeneralSecurityException {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        nextcloud = new NextcloudArchivos(new SAlmacenamientoCifrado("secreto-cifrado-prueba"), constructor);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        conexion = new MAlmacenamientoConexion("nc-1", usuario, "NEXTCLOUD");
        conexion.setUrlServidor("https://nextcloud.example.org/");
        conexion.setIdentificadorExterno("luisd");
        conexion.setCredencialCifrada(new SAlmacenamientoCifrado("secreto-cifrado-prueba").cifrar("clave-app"));
    }

    @Test
    void listarRaizMapeaCarpetasYArchivosDePropfind() throws Exception {
        servidor.expect(requestTo("https://nextcloud.example.org/remote.php/dav/files/luisd/"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header("Depth", "1"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.MULTI_STATUS).body(XML_LISTA));

        SAlmacenamientoArchivos.PaginaArchivos pagina = nextcloud.listar(conexion, "root", "unidad", null, null, null);

        assertEquals(2, pagina.archivos().size());
        AlmacenamientoArchivoDTO carpeta = pagina.archivos().get(0);
        assertEquals("Finanzas", carpeta.nombre());
        assertEquals(AlmacenamientoArchivoDTO.TIPO_CARPETA, carpeta.tipo());
        AlmacenamientoArchivoDTO archivo = pagina.archivos().get(1);
        assertEquals("presupuesto.xlsx", archivo.nombre());
        assertEquals(AlmacenamientoArchivoDTO.TIPO_ARCHIVO, archivo.tipo());
        assertEquals(2048L, archivo.tamanio());
        assertEquals("root", archivo.idPadre());
        servidor.verify();
    }

    @Test
    void credencialRechazadaExigeReconexion() {
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/remote.php/dav/files/luisd/")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body(""));
        assertThrows(ConexionRequiereReconexionException.class,
                () -> nextcloud.listar(conexion, "root", "unidad", null, null, null));
        servidor.verify();
    }

    @Test
    void recursoInexistenteRespondeRecursoNoEncontrado() {
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/remote.php/dav/files/luisd/")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND).body(""));
        assertThrows(AlmacenamientoRecursoNoEncontrado.class,
                () -> nextcloud.listar(conexion, "no-existe", null, null, null, null));
    }

    @Test
    void vistaInvalidaSeRechazaCon400() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> nextcloud.listar(conexion, null, "recientes", null, null, null));
        assertTrue(error.getMessage().contains("Vista no soportada"));
    }

    @Test
    void eliminarDefinitivamenteBorraLaEntradaConDeleteSobreLaRuta() throws Exception {
        String id = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("/remote.php/dav/trashbin/luisd/borrado.pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/remote.php/dav/trashbin/luisd/borrado.pdf")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        nextcloud.eliminarDefinitivo(conexion, id);

        servidor.verify();
    }

    @Test
    void contenidoDescargaElArchivoConBasicAuth() throws Exception {
        servidor.expect(requestTo("https://nextcloud.example.org/remote.php/dav/files/luisd/presupuesto.xlsx"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.MULTI_STATUS).body(XML_LISTA));
        servidor.expect(requestTo("https://nextcloud.example.org/remote.php/dav/files/luisd/presupuesto.xlsx"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        SAlmacenamientoArchivos.RecursoContenido recurso = nextcloud.contenido(conexion, "presupuesto.xlsx", true);
        assertEquals(3, recurso.flujo().readAllBytes().length);
        assertEquals("presupuesto.xlsx", recurso.nombreArchivo());
        servidor.verify();
    }

    @Test
    void renombrarResuelveLaRutaDeMetadatosTrasMover() throws Exception {
        String idOriginal = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("/remote.php/dav/files/luisd/Finanzas/presupuesto.xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String xmlDestino = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/remote.php/dav/files/luisd/Finanzas/presupuesto-renombrado.xlsx</d:href>
                    <d:propstat><d:prop>
                      <d:getcontentlength>2048</d:getcontentlength>
                      <d:getcontenttype>application/vnd.ms-excel</d:getcontenttype>
                      <d:getlastmodified>Tue, 10 Nov 2026 12:00:00 GMT</d:getlastmodified>
                      <d:resourcetype/>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                  </d:response>
                </d:multistatus>
                """;
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/remote.php/dav/files/luisd/Finanzas/presupuesto.xlsx")))
                .andExpect(method(HttpMethod.valueOf("MOVE")))
                .andExpect(header("Destination", org.hamcrest.Matchers.containsString("/remote.php/dav/files/luisd/Finanzas/presupuesto-renombrado.xlsx")))
                .andRespond(withSuccess());
        servidor.expect(requestTo(org.hamcrest.Matchers.containsString("/remote.php/dav/files/luisd/Finanzas/presupuesto-renombrado.xlsx")))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.MULTI_STATUS).body(xmlDestino));

        AlmacenamientoArchivoDTO dto = nextcloud.actualizar(conexion, idOriginal, "presupuesto-renombrado.xlsx", null, null);

        assertEquals("presupuesto-renombrado.xlsx", dto.nombre());
        servidor.verify();
    }
}
