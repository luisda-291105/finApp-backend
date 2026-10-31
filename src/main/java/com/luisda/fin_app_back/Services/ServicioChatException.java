package com.luisda.fin_app_back.Services;

import org.springframework.http.HttpStatus;

/** Error controlado que conserva el estado HTTP sin filtrar errores de proveedores. */
public class ServicioChatException extends RuntimeException {
    private final HttpStatus estado;

    public ServicioChatException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
