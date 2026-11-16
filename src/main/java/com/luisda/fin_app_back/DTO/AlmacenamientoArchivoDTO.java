package com.luisda.fin_app_back.DTO;

/**
 * @uso               Archivo o carpeta de una conexión de almacenamiento en la nube
 * @capa              DTO
 * @responsabilidades Contrato único de archivo entre proveedores (Drive, OneDrive, Nextcloud) y el frontend
 * @datos             Metadatos seguros; nunca tokens ni credenciales
 * @dependencias      Ninguno
 * @usadoPor          SAlmacenamientoArchivos, CAlmacenamientoArchivos
 */
public record AlmacenamientoArchivoDTO(
        String id,
        String nombre,
        String tipo,
        String mime,
        Long tamanio,
        String fechaModificacion,
        String idPadre,
        boolean compartido,
        boolean eliminable,
        boolean destacado,
        String webViewLink) {

    public static final String TIPO_CARPETA = "CARPETA";
    public static final String TIPO_ARCHIVO = "ARCHIVO";
}
