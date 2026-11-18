package com.luisda.fin_app_back.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGmailMensajes;
import com.luisda.fin_app_back.Services.SClasificadorMovimiento;
import com.luisda.fin_app_back.Services.SGmailEntradas;
import com.luisda.fin_app_back.Services.SGmailMensajes;
import com.luisda.fin_app_back.Services.SImportacionLoteGmail;
import com.luisda.fin_app_back.Services.SMovimientoContable;
import com.luisda.fin_app_back.Services.SSuscripcionGmail;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CGmailFuenteInexistenteTest {

    @Test
    void omiteFuenteInexistenteYNoEncolaMensajes() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        IGmailMensajes mensajes = mock(IGmailMensajes.class);
        SGmailEntradas entradas = mock(SGmailEntradas.class);
        CGmail gmail = new CGmail(fuentes, mensajes, mock(SSuscripcionGmail.class), entradas,
                mock(SGmailMensajes.class), mock(SUsuarioActivo.class), mock(SMovimientoContable.class),
                mock(SClasificadorMovimiento.class), mock(SImportacionLoteGmail.class));
        MFuente fuente = new MFuente();
        fuente.setIdFuente("fuente-ausente");
        when(fuentes.existsById("fuente-ausente")).thenReturn(false);
        Method encolarMensajeListado = CGmail.class.getDeclaredMethod("encolarMensajeListado",
                MFuente.class, com.fasterxml.jackson.databind.JsonNode.class);
        encolarMensajeListado.setAccessible(true);
        ObjectMapper mapper = new ObjectMapper();

        assertDoesNotThrow(() -> {
            encolarMensajeListado.invoke(gmail, fuente, mapper.readTree("{\"id\":\"mensaje-1\"}"));
            encolarMensajeListado.invoke(gmail, fuente, mapper.readTree("{\"id\":\"mensaje-2\"}"));
        });

        verify(fuentes, times(2)).existsById("fuente-ausente");
        verify(entradas, never()).encolarMensaje(any(), any());
        verify(mensajes, never()).save(any());
    }
}
