package com.luisda.fin_app_back.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Repository.IGmailSuscripciones;
import com.luisda.fin_app_back.Services.SClasificadorMovimiento;
import com.luisda.fin_app_back.Services.SImportacionLoteGmail;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CGmailFuenteInexistenteTest {

    @Test
    void omiteFuenteInexistenteYContinuaConLosSiguientesMensajes() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        IGmailMensajes mensajes = mock(IGmailMensajes.class);
        CGmail gmail = new CGmail(fuentes, mensajes, mock(IGmailSuscripciones.class),
                mock(SUsuarioActivo.class), mock(SMovimientoContable.class),
                mock(SClasificadorMovimiento.class), mock(SImportacionLoteGmail.class));
        MFuente fuente = new MFuente();
        fuente.setIdFuente("fuente-ausente");
        when(fuentes.existsById("fuente-ausente")).thenReturn(false);
        Method guardarMensajeListado = CGmail.class.getDeclaredMethod("guardarMensajeListado",
                MFuente.class, String.class, com.fasterxml.jackson.databind.JsonNode.class, java.util.Set.class);
        guardarMensajeListado.setAccessible(true);
        ObjectMapper mapper = new ObjectMapper();

        assertDoesNotThrow(() -> {
            guardarMensajeListado.invoke(gmail, fuente, "token-no-real", mapper.readTree("{\"id\":\"mensaje-1\"}"), new HashSet<String>());
            guardarMensajeListado.invoke(gmail, fuente, "token-no-real", mapper.readTree("{\"id\":\"mensaje-2\"}"), new HashSet<String>());
        });

        verify(fuentes, times(2)).existsById("fuente-ausente");
        verify(mensajes, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
