package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Services.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las rutas exactas del contrato (secciones 1, 2 y 3) quedan registradas bajo el prefijo
 * `/api/gmail` y exigen sesión activa.
 */
@SpringBootTest(properties = {
        "finapp.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class CPapeleraGmailRutasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokens;

    @Test
    void laEspecificacionExponeLasRutasExactasDeMoverRestaurarYListar() throws Exception {
        Cookie sesion = new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("1101383776"));

        mockMvc.perform(get("/api-docs").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/gmail/cuentas/{cuentaId}/mensajes/{mensajeId}/papelera'].post").exists())
                .andExpect(jsonPath("$.paths['/api/gmail/cuentas/{cuentaId}/mensajes/{mensajeId}/restaurar'].post").exists())
                .andExpect(jsonPath("$.paths['/api/gmail/cuentas/{cuentaId}/papelera'].get").exists());
    }
}
