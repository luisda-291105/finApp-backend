package com.luisda.fin_app_back.DTO;

/**
 * @uso        Respuesta del inicio de un flujo OAuth de almacenamiento
 * @funciones  Registro inmutable serializable
 * @datos      URL de autorización del proveedor; nunca secretos
 * @eventos    Ninguno
 * @estados    Ninguno
 * @usadoPor   CAlmacenamiento y SAlmacenamientoConexiones
 */
public record AlmacenamientoInicioDTO(String proveedor, String url) { }
