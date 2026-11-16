package com.luisda.fin_app_back.DTO;

/**
 * @uso               Permiso compartido de un archivo de una conexión de almacenamiento
 * @capa              DTO
 * @responsabilidades Contrato único de permisos entre proveedores y el frontend
 * @datos             Id, tipo (user/group/domain/anyone), rol y titular; sin material sensible
 * @dependencias      Ninguno
 * @usadoPor          SAlmacenamientoArchivos, CAlmacenamientoArchivos
 */
public record AlmacenamientoPermisoDTO(String id, String tipo, String rol, String email, String nombre) { }
