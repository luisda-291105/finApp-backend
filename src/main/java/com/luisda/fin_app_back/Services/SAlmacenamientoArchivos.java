package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * @uso               Operaciones de archivos comunes para los proveedores de almacenamiento
 * @capa              Servicio (contrato)
 * @responsabilidades Listar, metadatos, contenido/previsualización, crear carpeta, subir,
 *                    actualizar, copiar, papelera, restaurar, vaciar papelera, permisos y lote
 * @datos             Solo metadatos y contenido solicitado; los tokens los gestiona SAlmacenamientoTokens
 * @dependencias      Ninguna directa
 * @usadoPor          CAlmacenamientoArchivos; implementado por GoogleDriveArchivos
 */
public interface SAlmacenamientoArchivos {

    /** Proveedor que implementa esta capa. */
    String proveedor();

    /** Página de archivos de una carpeta, búsqueda o vista (PAPELERA). */
    PaginaArchivos listar(MAlmacenamientoConexion conexion, String carpetaId, String vista, String busqueda,
                          String orden, String pagina) throws GeneralSecurityException;

    /** Metadatos de un archivo o carpeta. */
    AlmacenamientoArchivoDTO metadatos(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException;

    /** Contenido de un archivo como flujo proxificado desde el proveedor. */
    RecursoContenido contenido(MAlmacenamientoConexion conexion, String fileId, boolean descargar)
            throws GeneralSecurityException;

    /** Flujo para previsualizar un archivo en línea. */
    RecursoContenido previsualizacion(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException;

    /** Crea una carpeta dentro de carpetaId (o raíz). */
    AlmacenamientoArchivoDTO crearCarpeta(MAlmacenamientoConexion conexion, String carpetaId, String nombre)
            throws GeneralSecurityException;

    /** Sube un archivo (bytes ya recibidos) a la carpeta indicada. */
    AlmacenamientoArchivoDTO subir(MAlmacenamientoConexion conexion, String carpetaId, String nombreArchivo,
                                   String mime, byte[] contenido) throws GeneralSecurityException;

    /** Renombra (nombre), mueve (idNuevoPadre) y/o destaca (destacar) según los valores no nulos. */
    AlmacenamientoArchivoDTO actualizar(MAlmacenamientoConexion conexion, String fileId, String nombre,
                                        String idNuevoPadre, Boolean destacar) throws GeneralSecurityException;

    /** Copia un archivo en la misma carpeta o en la indicada. */
    AlmacenamientoArchivoDTO copiar(MAlmacenamientoConexion conexion, String fileId, String idNuevoPadre)
            throws GeneralSecurityException;

    /** Mueve el archivo a la papelera del proveedor. */
    void aPapelera(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException;

    /** Saca el archivo de la papelera del proveedor. */
    void restaurar(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException;

    /** Elimina el archivo de forma permanente (no se puede deshacer). */
    void eliminarDefinitivo(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException;

    /** Vacía la papelera del proveedor para esta cuenta. */
    void vaciarPapelera(MAlmacenamientoConexion conexion) throws GeneralSecurityException;

    /** Lista los permisos de compartición de un archivo. */
    List<AlmacenamientoPermisoDTO> listarPermisos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException;

    /** Comparte un archivo con rol y titular (usuario, grupo, dominio o anyone). */
    AlmacenamientoPermisoDTO compartir(MAlmacenamientoConexion conexion, String fileId, String rol, String tipo,
                                       String email) throws GeneralSecurityException;

    /** Revoca un permiso de compartición. */
    void revocarPermiso(MAlmacenamientoConexion conexion, String fileId, String permisoId)
            throws GeneralSecurityException;

    /** Genera un ZIP con los archivos indicados para descarga múltiple. */
    byte[] descargarLote(MAlmacenamientoConexion conexion, List<String> fileIds) throws GeneralSecurityException;

    /** Resultado de listar con token de siguiente página. */
    record PaginaArchivos(List<AlmacenamientoArchivoDTO> archivos, String siguientePagina, String carpetaId) { }

    /** Contenido proxificado del proveedor con su tipo y nombre seguro para Content-Disposition. */
    record RecursoContenido(InputStream flujo, long tamano, String mime, String nombreArchivo) {
    }
}
