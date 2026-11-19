package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GrupoMensajeDTO;
import com.luisda.fin_app_back.DTO.GrupoMensajeSolicitudDTO;
import com.luisda.fin_app_back.DTO.PeticionChat;
import com.luisda.fin_app_back.DTO.RespuestaChat;
import com.luisda.fin_app_back.DTO.TurnoChat;
import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Model.MGrupoMensaje;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IGrupoMensaje;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Chat del asistente de IA acotado a un grupo; cada grupo tiene su propia conversación persistente. */
@Service
public class SGrupoChat {
    private static final int MAX_CARACTERES_MENSAJE = 2000;
    private static final int MAX_TURNOS_HISTORIAL = 20;

    private final IGrupo grupos;
    private final IGrupoMensaje mensajes;
    private final SAsistenteChat asistente;

    public SGrupoChat(IGrupo grupos, IGrupoMensaje mensajes, SAsistenteChat asistente) {
        this.grupos = grupos;
        this.mensajes = mensajes;
        this.asistente = asistente;
    }

    /** Devuelve la conversación del grupo; solo el propietario del grupo puede leerla. */
    @Transactional(readOnly = true)
    public List<GrupoMensajeDTO> listar(String idUsuario, String idGrupo) {
        propio(idUsuario, idGrupo);
        return mensajes.findByIdGrupoOrderByFechaAscIdMensajeAsc(idGrupo).stream()
                .map(this::dto)
                .toList();
    }

    /**
     * Persiste el mensaje del usuario, responde con el asistente (con contexto de los últimos
     * turnos del grupo) y persiste la respuesta. Devuelve [mensajeUsuario, mensajeAsistente].
     */
    @Transactional
    public List<GrupoMensajeDTO> enviar(String idUsuario, String idGrupo, GrupoMensajeSolicitudDTO solicitud) {
        MGrupo grupo = propio(idUsuario, idGrupo);
        String texto = solicitud == null || solicitud.texto() == null ? "" : solicitud.texto().trim();
        if (texto.isEmpty()) throw new IllegalArgumentException("Escribe un mensaje.");
        if (texto.length() > MAX_CARACTERES_MENSAJE)
            throw new IllegalArgumentException("El mensaje supera los 2000 caracteres.");

        MGrupoMensaje user = new MGrupoMensaje();
        user.setIdMensaje(UUID.randomUUID().toString());
        user.setIdGrupo(idGrupo);
        user.setIdUsuario(idUsuario);
        user.setRol("user");
        user.setTexto(texto);
        user.setFecha(LocalDateTime.now());
        mensajes.save(user);

        List<TurnoChat> historial = mensajes
                .findByIdGrupoOrderByFechaDescIdMensajeDesc(idGrupo, PageRequest.of(0, MAX_TURNOS_HISTORIAL))
                .stream()
                .map(mensaje -> new TurnoChat(mensaje.getRol(), mensaje.getTexto()))
                .toList()
                .reversed();
        // Historial al modelo: excluye el mensaje actual (ya es la pregunta de la petición).
        List<TurnoChat> contexto = historial.isEmpty() ? historial : historial.subList(0, historial.size() - 1);
        PeticionChat peticion = new PeticionChat(texto, contexto);
        RespuestaChat respuesta = asistente.responder(peticion, idUsuario);
        String textoRespuesta = respuesta == null || respuesta.respuesta() == null || respuesta.respuesta().isBlank()
                ? "No pude generar una respuesta. Inténtalo otra vez."
                : respuesta.respuesta();

        MGrupoMensaje asistenteMensaje = new MGrupoMensaje();
        asistenteMensaje.setIdMensaje(UUID.randomUUID().toString());
        asistenteMensaje.setIdGrupo(idGrupo);
        asistenteMensaje.setIdUsuario(idUsuario);
        asistenteMensaje.setRol("assistant");
        asistenteMensaje.setTexto(textoRespuesta);
        asistenteMensaje.setFecha(LocalDateTime.now());
        mensajes.save(asistenteMensaje);
        return List.of(dto(user), dto(asistenteMensaje));
    }

    /** Elimina todos los mensajes del grupo; se invoca al borrar el grupo. */
    @Transactional
    public void eliminarMensajesDe(String idGrupo) {
        mensajes.deleteByIdGrupo(idGrupo);
    }

    private MGrupo propio(String idUsuario, String idGrupo) {
        if (idGrupo == null || idGrupo.isBlank()) throw new NoSuchElementException("Selecciona un grupo.");
        return grupos.findById(idGrupo)
                .filter(grupo -> grupo.getUsuario() != null && idUsuario.equals(grupo.getUsuario().getIdUsuario()))
                .orElseThrow(() -> new NoSuchElementException("No se encontró el grupo seleccionado."));
    }

    private GrupoMensajeDTO dto(MGrupoMensaje mensaje) {
        return new GrupoMensajeDTO(mensaje.getIdMensaje(), mensaje.getIdGrupo(), mensaje.getIdUsuario(),
                mensaje.getRol(), mensaje.getTexto(), mensaje.getFecha());
    }
}
