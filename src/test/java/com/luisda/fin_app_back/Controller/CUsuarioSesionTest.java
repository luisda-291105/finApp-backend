package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.luisda.fin_app_back.Services.SVerificadorContrasena;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "finapp.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class CUsuarioSesionTest {
    private static final String ORIGEN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IUsuario usuarios;
    @Autowired
    private SVerificadorContrasena verificador;

    @BeforeEach
    void preparar() {
        if (usuarios.findById("sesion-1").isPresent()) return;
        MUsuario usuario = new MUsuario("sesion-1", "Sesión Test",
                verificador.codificar("clave-segura-123"), LocalDate.now());
        usuario.setCorreo("sesion@test.local");
        usuario.setActivo(true);
        usuario.setCredentialVersion(0L);
        usuarios.save(usuario);
    }

    private String cookieValor(MockHttpServletResponse respuesta, String nombre) {
        return respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(nombre + "="))
                .map(c -> c.substring(nombre.length() + 1, c.indexOf(';')))
                .findFirst().orElse(null);
    }

    private List<String> cookies(MockHttpServletResponse respuesta, String nombre) {
        return respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(nombre + "=")).toList();
    }

    @Test
    void bodyVacioFallaCon400YNorevuelveCookies() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(post("/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse();

        assertNull(cookieValor(respuesta, "FINAPP_AUTH"));
    }

    @Test
    void loginDevuelveAccessYRefreshConFlagsCorrectos() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(post("/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificador\":\"sesion-1\",\"contrasena\":\"clave-segura-123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse();

        String access = cookieValor(respuesta, "FINAPP_AUTH");
        String refresh = cookieValor(respuesta, "FINAPP_REFRESH");
        assertNotNull(access);
        assertNotNull(refresh);
        assertNotEquals(access, refresh);
        String accessCookie = cookies(respuesta, "FINAPP_AUTH").getFirst();
        String refreshCookie = cookies(respuesta, "FINAPP_REFRESH").getFirst();
        assertTrue(accessCookie.contains("HttpOnly"));
        assertTrue(accessCookie.contains("Secure"));
        assertTrue(accessCookie.contains("Path=/"));
        assertTrue(accessCookie.contains("SameSite=Lax"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Secure"));
        assertTrue(refreshCookie.contains("Path=/usuario/refresh"));
        assertTrue(refreshCookie.contains("SameSite=Strict"));
    }

    @Test
    void refreshSinCookieDevuelve401YBorraAmbasCookies() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(post("/usuario/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse();

        List<String> accessCookies = cookies(respuesta, "FINAPP_AUTH");
        List<String> refreshCookies = cookies(respuesta, "FINAPP_REFRESH");
        assertEquals(1, accessCookies.size());
        assertEquals(1, refreshCookies.size());
        assertTrue(accessCookies.getFirst().contains("Max-Age=0"));
        assertTrue(refreshCookies.getFirst().contains("Path=/usuario/refresh"));
        assertTrue(refreshCookies.getFirst().contains("Max-Age=0"));
    }

    @Test
    void refreshValidoRotaAmbasCookies() throws Exception {
        MockHttpServletResponse login = mockMvc.perform(post("/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificador\":\"sesion-1\",\"contrasena\":\"clave-segura-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie refreshCookie = new Cookie("FINAPP_REFRESH", cookieValor(login, "FINAPP_REFRESH"));
        Cookie accessCookie = new Cookie("FINAPP_AUTH", cookieValor(login, "FINAPP_AUTH"));

        MockHttpServletResponse respuesta = mockMvc.perform(post("/usuario/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGEN).cookie(refreshCookie, accessCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse();

        assertNotEquals(refreshCookie.getValue(), cookieValor(respuesta, "FINAPP_REFRESH"));
        assertNotEquals(accessCookie.getValue(), cookieValor(respuesta, "FINAPP_AUTH"));
    }

    @Test
    void refreshConOrigenNoPermitidoEs403() throws Exception {
        mockMvc.perform(post("/usuario/refresh").header(HttpHeaders.ORIGIN, "http://sitio-ajeno.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutExpiraAmbasCookies() throws Exception {
        MockHttpServletResponse login = mockMvc.perform(post("/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificador\":\"sesion-1\",\"contrasena\":\"clave-segura-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie accessCookie = new Cookie("FINAPP_AUTH", cookieValor(login, "FINAPP_AUTH"));

        MockHttpServletResponse respuesta = mockMvc.perform(post("/usuario/logout")
                        .header(HttpHeaders.ORIGIN, ORIGEN).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse();

        assertTrue(cookies(respuesta, "FINAPP_AUTH").getFirst().contains("Max-Age=0"));
        assertTrue(cookies(respuesta, "FINAPP_REFRESH").getFirst().contains("Max-Age=0"));
    }

    @Test
    void loginDeUsuarioInactivoEs401() throws Exception {
        crearUsuarioInactivo();

        mockMvc.perform(post("/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificador\":\"inactivo-1\",\"contrasena\":\"clave-segura-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private void crearUsuarioInactivo() {
        if (usuarios.findById("inactivo-1").isPresent()) return;
        MUsuario usuario = new MUsuario("inactivo-1", "Inactivo",
                verificador.codificar("clave-segura-123"), LocalDate.now());
        usuario.setCorreo("inactivo@test.local");
        usuario.setActivo(false);
        usuarios.save(usuario);
    }
}
