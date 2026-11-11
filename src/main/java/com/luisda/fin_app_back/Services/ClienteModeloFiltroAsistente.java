package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Filtrado.ClienteModeloFiltro;
import org.springframework.stereotype.Component;

/** Adapta el cliente NVIDIA ya usado por el asistente al puerto del clasificador. */
@Component
public class ClienteModeloFiltroAsistente implements ClienteModeloFiltro {
    private final SAsistenteChat asistente;

    public ClienteModeloFiltroAsistente(SAsistenteChat asistente) {
        this.asistente = asistente;
    }

    @Override
    public String clasificar(String prompt) {
        return asistente.clasificarFiltro(prompt);
    }
}
