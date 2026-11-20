package com.luisda.fin_app_back.Services;

/**
 * @uso               Señala que el proveedor no implementa aún las operaciones de archivos
 * @capa              Servicio
 * @responsabilidades Permitir al controlador responder 400 con un mensaje claro para OneDrive/Nextcloud
 * @datos             Mensaje seguro para el cliente
 * @dependencias      Ninguno
 * @usadoPor          CAlmacenamientoArchivos
 */
public class AlmacenamientoProveedorNoSoportado extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AlmacenamientoProveedorNoSoportado(String mensaje) { super(mensaje); }
}
