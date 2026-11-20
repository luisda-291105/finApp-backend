package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GrupoMensajeDTO;
import com.luisda.fin_app_back.DTO.GrupoMensajeSolicitudDTO;
import com.luisda.fin_app_back.DTO.GrupoMiembroDTO;
import com.luisda.fin_app_back.DTO.GrupoUsuarioResumenDTO;
import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Model.MGrupoMiembro;
import com.luisda.fin_app_back.Model.MGrupoMensaje;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IGrupoMiembro;
import com.luisda.fin_app_back.Repository.IGrupoMensaje;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chat y membresía de los grupos: mensajes entre miembros existentes y administración de
 * integrantes (añadir por correo, teléfono, identificación o nombre; el dueño gestiona).
 */
@Service
public class SGrupoChat {
    private static final int MAX_CARACTERES_MENSAJE = 2000;
    private static final int MAX_RESULTADOS_BUSQUEDA = 10;
    private static final long MAX_MIEMBROS_POR_GRUPO = 50;

    private final IGrupo grupos;
    private final IGrupoMiembro miembros;
    private final IGrupoMensaje mensajes;
    private final IUsuario usuarios;

    public SGrupoChat(IGrupo grupos, IGrupoMiembro miembros, IGrupoMensaje mensajes, IUsuario usuarios) {
        this.grupos = grupos;
        this.miembros = miembros;
        this.mensajes = mensajes;
        this.usuarios = usuarios;
    }

    // ── Mensajes ─────────────────────────────────────────────────────────────────

    /** Conversación del grupo; cualquier integrante puede leerla. */
    @Transactional(readOnly = true)
    public List<GrupoMensajeDTO> listarMensajes(String idUsuario, String idGrupo) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        exigirMiembro(grupo, idUsuario);
        List<MGrupoMensaje> mensajesGrupo =
                mensajes.findByIdGrupoOrderByFechaAscIdMensajeAsc(idGrupo);
        Map<String, MUsuario> autores = cargarAutores(mensajesGrupo);
        return mensajesGrupo.stream()
                .map(mensaje -> dto(mensaje, autores.get(mensaje.getIdUsuario())))
                .toList();
    }

    /** Persiste el mensaje de un miembro existente. */
    @Transactional
    public GrupoMensajeDTO enviarMensaje(String idUsuario, String idGrupo, GrupoMensajeSolicitudDTO solicitud) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        exigirMiembro(grupo, idUsuario);
        String texto = solicitud == null || solicitud.texto() == null ? "" : solicitud.texto().trim();
        if (texto.isEmpty()) throw new IllegalArgumentException("Escribe un mensaje.");
        if (texto.length() > MAX_CARACTERES_MENSAJE)
            throw new IllegalArgumentException("El mensaje supera los 2000 caracteres.");

        MGrupoMensaje mensaje = new MGrupoMensaje();
        mensaje.setIdMensaje(UUID.randomUUID().toString());
        mensaje.setIdGrupo(idGrupo);
        mensaje.setIdUsuario(idUsuario);
        mensaje.setRol("user");
        mensaje.setTexto(texto);
        mensaje.setFecha(LocalDateTime.now());
        mensajes.save(mensaje);
        return dto(mensaje, usuarios.findById(idUsuario).orElse(null));
    }

    /** Elimina todos los mensajes del grupo; se invoca al borrar el grupo. */
    @Transactional
    public void eliminarMensajesDe(String idGrupo) {
        mensajes.deleteByIdGrupo(idGrupo);
    }

    // ── Membresía ────────────────────────────────────────────────────────────────

    /** Miembros del grupo; incluye al propietario aunque aún no tenga fila (grupos previos). */
    @Transactional(readOnly = true)
    public List<GrupoMiembroDTO> listarMiembros(String idUsuario, String idGrupo) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        exigirMiembro(grupo, idUsuario);
        List<MGrupoMiembro> registrados = miembros.findByIdGrupo(idGrupo);
        boolean duenoRegistrado = registrados.stream().anyMatch(m -> "DUENO".equals(m.getRol()));
        List<String> ids = registrados.stream().map(MGrupoMiembro::getIdUsuario).collect(Collectors.toList());
        Map<String, MGrupoMiembro> porUsuario = registrados.stream()
                .collect(Collectors.toMap(MGrupoMiembro::getIdUsuario, Function.identity(), (a, b) -> a));
        if (!duenoRegistrado && grupo.getUsuario() != null) {
            String idDueno = grupo.getUsuario().getIdUsuario();
            if (!ids.contains(idDueno)) ids.add(idDueno);
        }
        Map<String, MUsuario> usuariosMiembros = usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(MUsuario::getIdUsuario, Function.identity()));
        return ids.stream()
                .map(id -> {
                    String rol = Optional.ofNullable(porUsuario.get(id))
                            .map(MGrupoMiembro::getRol)
                            .orElse("DUENO");
                    MUsuario u = usuariosMiembros.get(id);
                    LocalDateTime fecha = Optional.ofNullable(porUsuario.get(id))
                            .map(MGrupoMiembro::getFecha)
                            .orElse(null);
                    return new GrupoMiembroDTO(id, u != null ? u.getNombre() : "Usuario eliminado",
                            u != null ? u.getCorreo() : null,
                            u != null ? u.getTelefono() : null,
                            rol, fecha);
                })
                .sorted((a, b) -> {
                    if (a.rol().equals(b.rol())) {
                        if (a.fecha() == null && b.fecha() == null) return 0;
                        if (a.fecha() == null) return -1;
                        if (b.fecha() == null) return 1;
                        return a.fecha().compareTo(b.fecha());
                    }
                    return "DUENO".equals(a.rol()) ? -1 : 1;
                })
                .toList();
    }

    /**
     * Añade un usuario registrado como miembro. Solo el propietario puede administrar
     * integrantes; el término puede ser el identificador, el correo, el teléfono o el nombre.
     */
    @Transactional
    public List<GrupoMiembroDTO> agregarMiembro(String idUsuario, String idGrupo, String idUsuarioMiembro) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        exigirPropietario(grupo, idUsuario);
        String objetivo = idUsuarioMiembro == null ? "" : idUsuarioMiembro.trim();
        if (objetivo.isEmpty()) throw new IllegalArgumentException("Indica el usuario.");
        MUsuario usuarioObjetivo = usuarios.findById(objetivo)
                .orElseGet(() -> usuarios.findByCorreoIgnoreCase(objetivo)
                        .orElseGet(() -> usuarios.findByTelefono(objetivo)
                                .orElseGet(() -> {
                                    List<MUsuario> porNombre = usuarios.findByNombreIgnoreCase(objetivo);
                                    return porNombre.size() == 1 ? porNombre.get(0) : null;
                                })));
        if (usuarioObjetivo == null)
            throw new NoSuchElementException("No se encontró un usuario con esos datos. Si buscas por nombre, usa también su correo o identificación.");
        if (miembros.existsByIdGrupoAndIdUsuario(idGrupo, usuarioObjetivo.getIdUsuario()))
            throw new IllegalStateException("Ese usuario ya es miembro del grupo.");
        if (grupo.getUsuario() != null
                && usuarioObjetivo.getIdUsuario().equals(grupo.getUsuario().getIdUsuario()))
            throw new IllegalArgumentException("El propietario ya es parte del grupo.");
        if (miembros.findByIdGrupo(idGrupo).size() >= MAX_MIEMBROS_POR_GRUPO)
            throw new IllegalArgumentException("El grupo supera el máximo de integrantes.");

        MGrupoMiembro nuevo = new MGrupoMiembro();
        nuevo.setIdMiembro(UUID.randomUUID().toString());
        nuevo.setIdGrupo(idGrupo);
        nuevo.setIdUsuario(usuarioObjetivo.getIdUsuario());
        nuevo.setRol("MIEMBRO");
        nuevo.setFecha(LocalDateTime.now());
        miembros.save(nuevo);
        return listarMiembros(idUsuario, idGrupo);
    }

    /** Quita un miembro; los dueños no pueden auto-eliminarse ni ser eliminados. */
    @Transactional
    public List<GrupoMiembroDTO> eliminarMiembro(String idUsuario, String idGrupo, String idUsuarioMiembro) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        exigirPropietario(grupo, idUsuario);
        String objetivo = idUsuarioMiembro == null ? "" : idUsuarioMiembro.trim();
        if (grupo.getUsuario() != null && objetivo.equals(grupo.getUsuario().getIdUsuario()))
            throw new IllegalArgumentException("El propietario no se puede quitar del grupo.");
        long eliminados = miembros.deleteByIdGrupoAndIdUsuario(idGrupo, objetivo);
        if (eliminados == 0) throw new NoSuchElementException("Ese integrante no pertenece al grupo.");
        return listarMiembros(idUsuario, idGrupo);
    }

    // ── Búsqueda de usuarios ─────────────────────────────────────────────────────

    /**
     * Busca usuarios registrados por identificador, correo, teléfono o nombre (con coincidencia
     * parcial; con un nombre común devuelve todas las coincidencias).
     */
    @Transactional(readOnly = true)
    public List<GrupoUsuarioResumenDTO> buscarUsuarios(String consulta) {
        String q = consulta == null ? "" : consulta.trim();
        if (q.length() < 2)
            throw new IllegalArgumentException("Escribe al menos 2 caracteres para buscar.");
        return usuarios.buscarPorTermino(q).stream()
                .limit(MAX_RESULTADOS_BUSQUEDA)
                .map(u -> new GrupoUsuarioResumenDTO(u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getTelefono()))
                .toList();
    }

    // ── Utilitarios ──────────────────────────────────────────────────────────────

    private MGrupo propio(String idUsuario, String idGrupo) {
        if (idGrupo == null || idGrupo.isBlank())
            throw new NoSuchElementException("Selecciona un grupo.");
        return grupos.findById(idGrupo)
                .filter(grupo -> grupo.getUsuario() != null)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el grupo seleccionado."));
    }

    /** Solo miembros (el propietario cuenta como miembro implícito y no necesita fila). */
    private void exigirMiembro(MGrupo grupo, String idUsuario) {
        boolean propio = grupo.getUsuario() != null
                && idUsuario.equals(grupo.getUsuario().getIdUsuario());
        boolean registrado = miembros.findByIdGrupoAndIdUsuario(grupo.getIdGrupo(), idUsuario)
                .isPresent();
        if (!propio && !registrado) {
            throw new AccesoGrupoDenegadoException("No perteneces a este grupo.");
        }
    }

    /** Solo el propietario administra integrantes. */
    private void exigirPropietario(MGrupo grupo, String idUsuario) {
        if (grupo.getUsuario() == null || !idUsuario.equals(grupo.getUsuario().getIdUsuario()))
            throw new AccesoGrupoDenegadoException("Solo el propietario puede administrar los integrantes.");
    }

    private Map<String, MUsuario> cargarAutores(List<MGrupoMensaje> mensajesGrupo) {
        List<String> ids = mensajesGrupo.stream()
                .map(MGrupoMensaje::getIdUsuario)
                .distinct()
                .toList();
        return usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(MUsuario::getIdUsuario, Function.identity()));
    }

    private GrupoMensajeDTO dto(MGrupoMensaje mensaje, MUsuario autor) {
        return new GrupoMensajeDTO(mensaje.getIdMensaje(), mensaje.getIdGrupo(), mensaje.getIdUsuario(),
                autor != null ? autor.getNombre() : "Usuario",
                mensaje.getRol(), mensaje.getTexto(), mensaje.getFecha());
    }
}
