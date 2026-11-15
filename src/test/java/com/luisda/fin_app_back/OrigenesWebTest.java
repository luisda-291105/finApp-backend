package com.luisda.fin_app_back;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrigenesWebTest {

    @Test
    void normalizaMayusculasBarraFinalYEspacios() {
        List<String> permitidos = OrigenesWeb.normalizarLista(" http://finApp.luisda.org/ , http://localhost:5173 ");

        assertTrue(OrigenesWeb.permitido("http://finapp.luisda.org", permitidos));
        assertTrue(OrigenesWeb.permitido("HTTP://FINAPP.LUISDA.ORG", permitidos));
        assertTrue(OrigenesWeb.permitido("http://localhost:5173/", permitidos));
        assertFalse(OrigenesWeb.permitido("http://sitio-ajeno.com", permitidos));
    }

    @Test
    void validaElOrigenDeLasEscriturasSinDistinguirMayusculas() {
        ValidacionOrigenInterceptor interceptor = new ValidacionOrigenInterceptor(
                List.of("http://finapp.luisda.org", "http://localhost:5173"));

        MockHttpServletRequest escritura = new MockHttpServletRequest("POST", "/suscripcion");
        escritura.addHeader("Origin", "http://finApp.luisda.org");
        assertTrue(interceptor.preHandle(escritura, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest escrituraAjena = new MockHttpServletRequest("POST", "/suscripcion");
        escrituraAjena.addHeader("Origin", "http://sitio-ajeno.com");
        MockHttpServletResponse respuestaAjena = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(escrituraAjena, respuestaAjena, new Object()));
        assertTrue(respuestaAjena.getStatus() == 403);
    }

    @Test
    void lasLecturasNoValidanElOrigen() {
        ValidacionOrigenInterceptor interceptor = new ValidacionOrigenInterceptor(List.of("http://localhost:5173"));

        MockHttpServletRequest lectura = new MockHttpServletRequest("GET", "/suscripcion");

        assertTrue(interceptor.preHandle(lectura, new MockHttpServletResponse(), new Object()));
    }
}
