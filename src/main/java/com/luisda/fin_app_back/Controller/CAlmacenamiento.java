package com.luisda.fin_app_back.Controller;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.SAlmacenamientoConexiones;
import com.luisda.fin_app_back.Services.SUsuarioActivo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @uso               Endpoints autenticados de conexiones de almacenamiento en la nube
 * @funciones         listar, iniciarOAuth, completarOAuth, conectarNextcloud, desconectar
 * @datos             Determina al usuario desde la sesión JWT; nunca acepta un userId del cliente
 * @eventos           Ninguno
 * @estados           Ninguno
 * @usadoPor          Frontend (almacenamientoServicio)
 */
@RestController
@Tag(name = "Almacenamiento", description = "Conexiones de almacenamiento en la nube (Google Drive, OneDrive, Nextcloud).")
@RequestMapping("/api/almacenamiento")
public class CAlmacenamiento {

    private final SAlmacenamientoConexiones conexiones;
    private final SUsuarioActivo usuarioActivo;
    private final IAlmacenamientoConexiones repositorioConexiones;
    private final List<String> origenesPermitidos;

    public CAlmacenamiento(SAlmacenamientoConexiones conexiones, SUsuarioActivo usuarioActivo,
                           IAlmacenamientoConexiones repositorioConexiones,
                           @Value("${finapp.cors.allowed-origins:http://localhost:5173}") String origenesPermitidos) {
        this.conexiones = conexiones;
        this.usuarioActivo = usuarioActivo;
        this.repositorioConexiones = repositorioConexiones;
        this.origenesPermitidos = Arrays.stream(origenesPermitidos.split(",")).map(String::trim).toList();
    }

    /** Lista las conexiones de almacenamiento del usuario activo. */
    @GetMapping("/conexiones")
    public ResponseEntity<?> listarConexiones(HttpServletRequest request) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        return ResponseEntity.ok(conexiones.listar(usuario));
    }

    /** Devuelve la URL de autorización del proveedor con un state de un solo uso. */
    @PostMapping("/conexiones/iniciar")
    public ResponseEntity<?> iniciarConexion(@RequestBody SolicitudIniciar solicitud, HttpServletRequest request) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        if (!origenValido(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Origen no autorizado para conectar almacenamiento."));
        try {
            return ResponseEntity.ok(conexiones.iniciar(usuario, solicitud == null ? "" : solicitud.proveedor()));
        } catch (SAlmacenamientoConexiones.ProveedorNoSoportadoException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    /** Completa el OAuth validando el state de un solo uso asociado a la sesión iniciadora. */
    @PostMapping("/conexiones/callback")
    public ResponseEntity<?> completarConexion(@RequestBody SolicitudCallback solicitud, HttpServletRequest request) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Inicia sesión en FinApp antes de conectar almacenamiento."));
        if (!origenValido(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Origen no autorizado para conectar almacenamiento."));
        String origen = request.getHeader(HttpHeaders.ORIGIN);
        try {
            return ResponseEntity.ok(conexiones.completarOAuth(usuario,
                    solicitud == null ? "" : solicitud.proveedor(),
                    solicitud == null ? null : solicitud.code(),
                    solicitud == null ? null : solicitud.state(),
                    origen));
        } catch (SAlmacenamientoConexiones.EstadoInvalidoException | SAlmacenamientoConexiones.ProveedorNoSoportadoException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", error.getMessage()));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", error.getMessage()));
        } catch (GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo guardar la conexión de forma segura."));
        }
    }

    /** Conecta una cuenta Nextcloud con URL, usuario y contraseña de aplicación. */
    @PostMapping("/conexiones/nextcloud")
    public ResponseEntity<?> conectarNextcloud(@RequestBody SAlmacenamientoConexiones.ConexionNextcloud solicitud,
                                               HttpServletRequest request) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        if (!origenValido(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Origen no autorizado para conectar almacenamiento."));
        try {
            return ResponseEntity.ok(conexiones.conectarNextcloud(usuario, solicitud));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", error.getMessage()));
        } catch (GeneralSecurityException error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo guardar la conexión de forma segura."));
        }
    }

    /** Elimina la conexión propia, borra la credencial local e intenta revocar la autorización remota. */
    @DeleteMapping("/conexiones/{id}")
    public ResponseEntity<?> desconectar(@PathVariable String id, HttpServletRequest request) {
        MUsuario usuario = usuarioActivo.obtenerUsuario(request).orElse(null);
        if (usuario == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "No hay una sesión activa."));
        boolean revocacionFallida = conexiones.desconectar(id, usuario);
        if (revocacionFallida) {
            return ResponseEntity.accepted().body(Map.of(
                    "message", "La cuenta se eliminó de FinApp, pero no se pudo revocar el permiso en el proveedor."));
        }
        return ResponseEntity.noContent().build();
    }

    private boolean origenValido(HttpServletRequest request) {
        String origen = request.getHeader(HttpHeaders.ORIGIN);
        String cabeceraSolicitud = request.getHeader("X-Requested-With");
        boolean origenAutorizado = origen != null && origenesPermitidos.contains(origen);
        boolean cabeceraAutorizada = "XmlHttpRequest".equals(cabeceraSolicitud);
        return origenAutorizado && cabeceraAutorizada;
    }

    public record SolicitudIniciar(String proveedor) { }
    public record SolicitudCallback(String proveedor, String code, String state) { }
}
