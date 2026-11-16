package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.GeneralSecurityException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SAlmacenamientoTokensTest {
    private static final String SECRETO = "secreto-cifrado-prueba-almacenamiento";

    private IAlmacenamientoConexiones repositorio;
    private SAlmacenamientoTokens tokens;
    private MockRestServiceServer servidor;
    private MAlmacenamientoConexion conexion;

    @BeforeEach
    void preparar() throws GeneralSecurityException {
        repositorio = mock(IAlmacenamientoConexiones.class);
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        tokens = new SAlmacenamientoTokens(repositorio, new SAlmacenamientoCifrado(SECRETO), constructor);
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario("1101383776");
        conexion = new MAlmacenamientoConexion("conexion-1", usuario, SAlmacenamientoConexiones.PROVEEDOR_GOOGLE_DRIVE);
        conexion.setAccessTokenCifrado(new SAlmacenamientoCifrado(SECRETO).cifrar("token-acceso"));
        conexion.setRefreshTokenCifrado(new SAlmacenamientoCifrado(SECRETO).cifrar("token-refresco"));
    }

    @Test
    void tokenVigenteNoLlamaAlProveedor() throws Exception {
        conexion.setExpiraEn(Instant.now().plusSeconds(1800));
        assertEquals("token-acceso", tokens.tokenVigente(conexion));
        servidor.verify();
        verify(repositorio, never()).save(any());
    }

    @Test
    void tokenPorCaducarSeRenuevaYCifraElNuevo() throws Exception {
        conexion.setExpiraEn(Instant.now().minusSeconds(10));
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"nuevo-acceso\",\"refresh_token\":\"nuevo-refresco\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        assertEquals("nuevo-acceso", tokens.tokenVigente(conexion));
        verify(repositorio).save(any());
        assertTrue(conexion.getExpiraEn().isAfter(Instant.now()));
        assertEquals("nuevo-acceso", new SAlmacenamientoCifrado(SECRETO).descifrar(conexion.getAccessTokenCifrado()));
        servidor.verify();
    }

    @Test
    void renovacionFallidaMarcaReconexion() {
        conexion.setExpiraEn(Instant.now().minusSeconds(10));
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        assertThrows(ConexionRequiereReconexionException.class, () -> tokens.tokenVigente(conexion));
        assertEquals("REQUIERE_RECONEXION", conexion.getEstado());
        verify(repositorio).save(any());
        servidor.verify();
    }

    @Test
    void sinRefreshTokenExigeReconexion() {
        conexion.setRefreshTokenCifrado(null);
        conexion.setExpiraEn(Instant.now().minusSeconds(10));
        ConexionRequiereReconexionException error = assertThrows(ConexionRequiereReconexionException.class,
                () -> tokens.tokenVigente(conexion));
        assertEquals("REQUIERE_RECONEXION", conexion.getEstado());
        verify(repositorio).save(any());
        assertTrue(error.getMessage().contains("reconexión"));
    }

    @Test
    void conexionSinMaterialCifradoMarcaReconexion() throws GeneralSecurityException {
        conexion.setAccessTokenCifrado(null);
        conexion.setRefreshTokenCifrado(null);
        conexion.setExpiraEn(Instant.now().plusSeconds(1800));
        assertThrows(ConexionRequiereReconexionException.class, () -> tokens.tokenVigente(conexion));
    }
}
