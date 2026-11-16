package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @uso               Caché corta de listados de archivos por conexión y carpeta
 * @capa              Servicio
 * @responsabilidades Guardar la última página por clave conexión+consulta con TTL de 60 s
 *                    e invalidar todas las entradas de una conexión tras una escritura
 * @datos             Solo metadatos de listados; nada sensible
 * @dependencias      Ninguna
 * @usadoPor          GoogleDriveArchivos
 */
@Component
public class SCacheListadosAlmacenamiento {

    private static final long TTL_SEGUNDOS = 60;

    private record Entrada(SAlmacenamientoArchivos.PaginaArchivos pagina, Instant expiracion) { }

    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();

    /** Devuelve la página cacheada si sigue vigente. */
    public SAlmacenamientoArchivos.PaginaArchivos obtener(MAlmacenamientoConexion conexion, String claveConsulta) {
        Entrada entrada = entradas.get(clave(conexion, claveConsulta));
        if (entrada == null) return null;
        if (entrada.expiracion().isBefore(Instant.now())) {
            entradas.remove(clave(conexion, claveConsulta));
            return null;
        }
        return entrada.pagina();
    }

    /** Guarda una página con TTL corto. */
    public void guardar(MAlmacenamientoConexion conexion, String claveConsulta, SAlmacenamientoArchivos.PaginaArchivos pagina) {
        if (pagina == null) return;
        entradas.put(clave(conexion, claveConsulta), new Entrada(pagina, Instant.now().plusSeconds(TTL_SEGUNDOS)));
    }

    /** Invalida la caché de la conexión (tras escrituras, subidas, papelera o permisos que afecten listados). */
    public void invalidar(MAlmacenamientoConexion conexion) {
        String prefijo = clave(conexion, "");
        entradas.keySet().removeIf(clave -> clave.startsWith(prefijo));
    }

    /** Limpia todas las entradas vencidas (reutilizable por un job si se requiere). */
    public void limpiarVencidas() {
        Instant ahora = Instant.now();
        entradas.entrySet().removeIf(entrada -> entrada.getValue().expiracion().isBefore(ahora));
    }

    private String clave(MAlmacenamientoConexion conexion, String consulta) {
        return conexion.getIdConexion() + "|" + (consulta == null ? "" : consulta);
    }

    /** TTL actual, expuesto para pruebas. */
    public static Duration ttl() { return Duration.ofSeconds(TTL_SEGUNDOS); }
}
