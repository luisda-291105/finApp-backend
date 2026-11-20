package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.DTO.GmailMensajeDTO;
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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La bandeja cacheada no devuelve correos en TRASH para que no se reimporten como por revisar. */
class CGmailMensajesPapeleraTest {

    @Test
    @SuppressWarnings("unchecked")
    void excluyeCorreosEnPapeleraDeLaBandejaCacheada() throws Exception {
        IGmailMensajes repositorio = mock(IGmailMensajes.class);
        when(repositorio.findResumenesByCuentaAndFecha(anyString(), any(Instant.class)))
                .thenReturn(List.of(
                        resumen("en-bandeja", "[]", null),
                        resumen("movido-desde-finapp", "[]", "EN_PAPELERA"),
                        resumen("movido-en-gmail", "[\"TRASH\"]", null)));
        CGmail gmail = new CGmail(mock(IFuentes.class), repositorio, mock(SSuscripcionGmail.class),
                mock(SGmailEntradas.class), mock(SGmailMensajes.class), mock(SUsuarioActivo.class),
                mock(SMovimientoContable.class), mock(SClasificadorMovimiento.class),
                mock(SImportacionLoteGmail.class));
        MFuente cuenta = new MFuente();
        cuenta.setIdFuente("cuenta-1");
        cuenta.setContenido("usuario@gmail.com");
        Method metodo = CGmail.class.getDeclaredMethod("mensajesCacheados", MFuente.class, List.class);
        metodo.setAccessible(true);

        List<GmailMensajeDTO> correos = (List<GmailMensajeDTO>) metodo.invoke(
                gmail, cuenta, List.of("usuario@gmail.com"));

        assertEquals(List.of("en-bandeja"), correos.stream().map(GmailMensajeDTO::id).toList());
    }

    private IGmailMensajes.Resumen resumen(String id, String etiquetas, String papeleraEstado) {
        return new IGmailMensajes.Resumen() {
            @Override public String getGoogleMessageId() { return id; }
            @Override public String getRemitente() { return "banco@ejemplo.com"; }
            @Override public String getDestinatario() { return "usuario@gmail.com"; }
            @Override public String getAsunto() { return "Movimiento"; }
            @Override public String getFragmento() { return "Pago de prueba"; }
            @Override public String getEtiquetasJson() { return etiquetas; }
            @Override public String getNombresEtiquetasJson() { return "[]"; }
            @Override public String getPapeleraEstado() { return papeleraEstado; }
            @Override public Instant getFechaInterna() { return Instant.parse("2026-11-17T12:00:00Z"); }
        };
    }
}
