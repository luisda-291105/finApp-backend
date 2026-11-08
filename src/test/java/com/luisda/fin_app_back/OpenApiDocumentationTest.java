package com.luisda.fin_app_back;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsExposeMetadataAndControllerRoutes() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinApp API"))
                .andExpect(jsonPath("$.paths['/usuario/login'].post").exists())
                .andExpect(jsonPath("$.paths['/bolsillo'].get").exists())
                .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItem("Usuarios y sesión")));
    }

    @Test
    void swaggerUiRedirectsToItsInteractivePage() throws Exception {
        mockMvc.perform(get("/docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui")));
    }
}
