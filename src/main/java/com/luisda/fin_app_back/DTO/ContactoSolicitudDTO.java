package com.luisda.fin_app_back.DTO;

/** Campos editables del contacto; la identidad propietaria siempre se obtiene de la sesión. */
public record ContactoSolicitudDTO(String nombre, String telefono, String notas, String correo, String origen, String cargo, String empresa, String correoOrigen) {
    public ContactoSolicitudDTO(String nombre, String telefono, String notas) {
        this(nombre, telefono, notas, null, null, null, null, null);
    }

    public ContactoSolicitudDTO(String nombre, String telefono, String notas, String correo, String origen) {
        this(nombre, telefono, notas, correo, origen, null, null, null);
    }

    public ContactoSolicitudDTO(String nombre, String telefono, String notas, String correo, String origen, String cargo, String empresa) {
        this(nombre, telefono, notas, correo, origen, cargo, empresa, null);
    }
}
