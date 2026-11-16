package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @uso               Router de operaciones de archivos según el proveedor de la conexión
 * @capa              Servicio
 * @responsabilidades Delegar cada operación en la implementación del proveedor (GOOGLE_DRIVE, NEXTCLOUD)
 * @datos             Ninguno adicional; delegación directa
 * @dependencias      Implementaciones de SAlmacenamientoArchivos
 * @usadoPor          CAlmacenamientoArchivos
 */
@Service("sArchivosPorConexion")
public class SArchivosPorConexion implements SAlmacenamientoArchivos {

    private final Map<String, SAlmacenamientoArchivos> porProveedor = new LinkedHashMap<>();

    public SArchivosPorConexion(List<SAlmacenamientoArchivos> implementaciones) {
        for (SAlmacenamientoArchivos implementacion : implementaciones) {
            porProveedor.put(implementacion.proveedor().toUpperCase(java.util.Locale.ROOT), implementacion);
        }
    }

    /** Implementación correspondiente al proveedor de la conexión. */
    public SAlmacenamientoArchivos de(MAlmacenamientoConexion conexion) {
        SAlmacenamientoArchivos implementacion = porProveedor.get(
                conexion.getProveedor() == null ? "" : conexion.getProveedor().toUpperCase(java.util.Locale.ROOT));
        if (implementacion == null) {
            throw new AlmacenamientoProveedorNoSoportado(
                    "El proveedor " + conexion.getProveedor() + " aún no soporta operaciones de archivos.");
        }
        return implementacion;
    }

    @Override public String proveedor() { return "DINAMICO"; }

    @Override
    public PaginaArchivos listar(MAlmacenamientoConexion conexion, String carpetaId, String vista, String busqueda,
                                 String orden, String pagina) throws GeneralSecurityException {
        return de(conexion).listar(conexion, carpetaId, vista, busqueda, orden, pagina);
    }

    @Override
    public AlmacenamientoArchivoDTO metadatos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return de(conexion).metadatos(conexion, fileId);
    }

    @Override
    public RecursoContenido contenido(MAlmacenamientoConexion conexion, String fileId, boolean descargar)
            throws GeneralSecurityException {
        return de(conexion).contenido(conexion, fileId, descargar);
    }

    @Override
    public RecursoContenido previsualizacion(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return de(conexion).previsualizacion(conexion, fileId);
    }

    @Override
    public AlmacenamientoArchivoDTO crearCarpeta(MAlmacenamientoConexion conexion, String carpetaId, String nombre)
            throws GeneralSecurityException {
        return de(conexion).crearCarpeta(conexion, carpetaId, nombre);
    }

    @Override
    public AlmacenamientoArchivoDTO subir(MAlmacenamientoConexion conexion, String carpetaId, String nombreArchivo,
                                          String mime, byte[] contenido) throws GeneralSecurityException {
        return de(conexion).subir(conexion, carpetaId, nombreArchivo, mime, contenido);
    }

    @Override
    public AlmacenamientoArchivoDTO actualizar(MAlmacenamientoConexion conexion, String fileId, String nombre,
                                               String idNuevoPadre, Boolean destacar) throws GeneralSecurityException {
        return de(conexion).actualizar(conexion, fileId, nombre, idNuevoPadre, destacar);
    }

    @Override
    public AlmacenamientoArchivoDTO copiar(MAlmacenamientoConexion conexion, String fileId, String idNuevoPadre)
            throws GeneralSecurityException {
        return de(conexion).copiar(conexion, fileId, idNuevoPadre);
    }

    @Override
    public void aPapelera(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        de(conexion).aPapelera(conexion, fileId);
    }

    @Override
    public void restaurar(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        de(conexion).restaurar(conexion, fileId);
    }

    @Override
    public void eliminarDefinitivo(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        de(conexion).eliminarDefinitivo(conexion, fileId);
    }

    @Override
    public void vaciarPapelera(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        de(conexion).vaciarPapelera(conexion);
    }

    @Override
    public List<AlmacenamientoPermisoDTO> listarPermisos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return de(conexion).listarPermisos(conexion, fileId);
    }

    @Override
    public AlmacenamientoPermisoDTO compartir(MAlmacenamientoConexion conexion, String fileId, String rol, String tipo,
                                              String email) throws GeneralSecurityException {
        return de(conexion).compartir(conexion, fileId, rol, tipo, email);
    }

    @Override
    public void revocarPermiso(MAlmacenamientoConexion conexion, String fileId, String permisoId)
            throws GeneralSecurityException {
        de(conexion).revocarPermiso(conexion, fileId, permisoId);
    }

    @Override
    public byte[] descargarLote(MAlmacenamientoConexion conexion, List<String> fileIds) throws GeneralSecurityException {
        return de(conexion).descargarLote(conexion, fileIds);
    }
}
