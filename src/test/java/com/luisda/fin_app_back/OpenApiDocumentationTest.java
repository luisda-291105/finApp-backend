package com.luisda.fin_app_back;

import com.luisda.fin_app_back.Services.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "finapp.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokens;

    @Test
    void sinSesionSoloSeExponenLasRutasPublicas() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinApp API"))
                .andExpect(jsonPath("$.paths['/usuario/login'].post").exists())
                .andExpect(jsonPath("$.paths['/usuario/admin-login'].post").exists())
                .andExpect(jsonPath("$.paths['/bolsillo'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/grupo'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/movimiento'].get").doesNotExist());
    }

    @Test
    void conSesionSeExponeLaApiCompleta() throws Exception {
        Cookie sesion = new Cookie(JwtTokenService.COOKIE_NAME, tokens.emitir("1101383776"));

        mockMvc.perform(get("/api-docs").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinApp API"))
                .andExpect(jsonPath("$.paths['/usuario/login'].post").exists())
                .andExpect(jsonPath("$.paths['/bolsillo'].get").exists())
                .andExpect(jsonPath("$.paths['/grupo'].get").exists());
    }

    @Test
    void swaggerUiRedirigeASuPaginaInteractiva() throws Exception {
        mockMvc.perform(get("/docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui")));
    }

    @Test
    void lasRutasProtegidasExigenSesion() throws Exception {
        mockMvc.perform(get("/grupo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(not(containsString("Exception"))));
    }
}
