package com.luisda.fin_app_back.Services;

/**
 * @uso               Señala que el archivo o carpeta no existe en el proveedor
 * @capa              Servicio
 * @responsabilidades Permitir al controlador responder 404 sin filtrar detalles internos
 * @datos             Mensaje seguro para el cliente
 * @dependencias      Ninguno
 * @usadoPor          GoogleDriveArchivos, CAlmacenamientoArchivos
 */
public class AlmacenamientoRecursoNoEncontrado extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AlmacenamientoRecursoNoEncontrado() { super("El recurso solicitado no existe."); }
    public AlmacenamientoRecursoNoEncontrado(String mensaje) { super(mensaje); }
}
