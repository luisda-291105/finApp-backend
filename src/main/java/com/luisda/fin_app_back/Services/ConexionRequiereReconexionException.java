package com.luisda.fin_app_back.Services;

/**
 * @uso               Señala que la conexión necesita que el usuario vuelva a autorizar el proveedor
 * @capa              Servicio
 * @responsabilidades Permitir al controlador responder 409 CONEXION_REQUIERE_RECONEXION
 * @datos             Mensaje seguro para el cliente
 * @dependencias      Ninguno
 * @usadoPor          SAlmacenamientoTokens, GoogleDriveArchivos, CAlmacenamientoArchivos
 */
public class ConexionRequiereReconexionException extends RuntimeException {
    public ConexionRequiereReconexionException(String mensaje) { super(mensaje); }
    public ConexionRequiereReconexionException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
