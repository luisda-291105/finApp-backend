package com.luisda.fin_app_back.DTO;

/** Representación pública del contacto sin exponer datos de su cuenta propietaria. */
public record ContactoDTO(String idContacto, String nombre, String telefono, String notas, String correo, String origen, String cargo, String empresa, String correoOrigen) {
    public ContactoDTO(String idContacto, String nombre, String telefono, String notas) {
        this(idContacto, nombre, telefono, notas, null, null, null, null, null);
    }

    public ContactoDTO(String idContacto, String nombre, String telefono, String notas, String correo, String origen) {
        this(idContacto, nombre, telefono, notas, correo, origen, null, null, null);
    }

    public ContactoDTO(String idContacto, String nombre, String telefono, String notas, String correo, String origen, String cargo, String empresa) {
        this(idContacto, nombre, telefono, notas, correo, origen, cargo, empresa, null);
    }
}
