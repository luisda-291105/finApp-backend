package com.luisda.fin_app_back.DTO;

/**
 * @uso        Metadatos seguros de una conexión de almacenamiento para el frontend
 * @funciones  Registro inmutable serializable
 * @datos      id, proveedor, etiqueta, url de servidor, estado y fechas; nunca secretos
 * @eventos    Ninguno
 * @estados    Ninguno
 * @usadoPor   CAlmacenamiento y SAlmacenamientoConexiones
 */
public record AlmacenamientoConexionDTO(
        String id,
        String proveedor,
        String etiquetaCuenta,
        String urlServidor,
        String estado,
        String conectadoEn,
        String actualizadoEn
) { }
