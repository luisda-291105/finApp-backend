package com.luisda.fin_app_back.Services;

/** Rechaza una operación sobre un grupo cuando el usuario no es miembro o no es propietario. */
public class AccesoGrupoDenegadoException extends RuntimeException {
    public AccesoGrupoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
