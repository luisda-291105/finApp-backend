package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGmailEntradaPendiente;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CGmailEntradasWorkerTest {

    private CGmail construido(IFuentes fuentes, SGmailEntradas entradas, SGmailMensajes servicioMensajes) {
        return new CGmail(fuentes, mock(IGmailMensajes.class), mock(SSuscripcionGmail.class), entradas,
                servicioMensajes, mock(SUsuarioActivo.class), mock(SMovimientoContable.class),
                mock(SClasificadorMovimiento.class), mock(SImportacionLoteGmail.class));
    }

    private MGmailEntradaPendiente entradaDeCuenta(String idFuente, String mensajeId) {
        MFuente cuenta = new MFuente();
        cuenta.setIdFuente(idFuente);
        MGmailEntradaPendiente entrada = new MGmailEntradaPendiente();
        entrada.setId("entrada-" + idFuente);
        entrada.setCuenta(cuenta);
        entrada.setGoogleMessageId(mensajeId);
        entrada.setEstado(MGmailEntradaPendiente.ESTADO_EN_ESPERA);
        entrada.setIntentos(0);
        return entrada;
    }

    @Test
    void descartaEntradasDeCuentasInexistentesSinReclamarlas() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        SGmailEntradas entradas = mock(SGmailEntradas.class);
        CGmail gmail = construido(fuentes, entradas, mock(SGmailMensajes.class));
        MGmailEntradaPendiente entrada = entradaDeCuenta("cuenta-borrada", "msg-1");
        when(fuentes.findById("cuenta-borrada")).thenReturn(Optional.empty());

        Method procesar = CGmail.class.getDeclaredMethod("procesarEntradaPendiente", MGmailEntradaPendiente.class);
        procesar.setAccessible(true);
        procesar.invoke(gmail, entrada);

        verify(entradas, never()).reclamar(any());
        verify(entradas).marcarProcesado(entrada);
        verify(entradas, never()).marcarError(any(), any());
    }

    @Test
    void elWorkerDrenaTodoElLoteEnEspera() throws Exception {
        IFuentes fuentes = mock(IFuentes.class);
        SGmailEntradas entradas = mock(SGmailEntradas.class);
        CGmail gmail = construido(fuentes, entradas, mock(SGmailMensajes.class));
        MGmailEntradaPendiente primera = entradaDeCuenta("cuenta-1", "msg-1");
        MGmailEntradaPendiente segunda = entradaDeCuenta("cuenta-2", "msg-2");
        when(entradas.pendientes(50)).thenReturn(List.of(primera, segunda));
        when(fuentes.findById("cuenta-1")).thenReturn(Optional.empty());
        when(fuentes.findById("cuenta-2")).thenReturn(Optional.empty());

        gmail.procesarEntradasPendientes();

        verify(entradas).marcarProcesado(primera);
        verify(entradas).marcarProcesado(segunda);
        verify(entradas, times(2)).marcarProcesado(any());
    }
}
