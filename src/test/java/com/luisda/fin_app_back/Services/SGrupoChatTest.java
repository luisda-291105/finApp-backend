package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.GrupoMensajeDTO;
import com.luisda.fin_app_back.DTO.GrupoMensajeSolicitudDTO;
import com.luisda.fin_app_back.DTO.PeticionChat;
import com.luisda.fin_app_back.DTO.RespuestaChat;
import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Model.MGrupoMensaje;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.IGrupoMensaje;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SGrupoChatTest {
    private IGrupo grupos;
    private IGrupoMensaje mensajes;
    private SAsistenteChat asistente;
    private SGrupoChat chat;
    private MGrupo grupo;

    @BeforeEach
    void preparar() {
        grupos = mock(IGrupo.class);
        mensajes = mock(IGrupoMensaje.class);
        asistente = mock(SAsistenteChat.class);
        chat = new SGrupoChat(grupos, mensajes, asistente);
        grupo = new MGrupo();
        grupo.setIdGrupo("grupo-1");
        grupo.setNombre("Viaje");
        grupo.setUsuario(usuario("usuario-1"));
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
    }

    @Test
    void listaLosMensajesDelGrupo() {
        MGrupoMensaje previo = mensaje("m-1", "user", "hola");
        MGrupoMensaje respuesta = mensaje("m-2", "assistant", "hola, ¿en qué te ayudo?");
        when(mensajes.findByIdGrupoOrderByFechaAscIdMensajeAsc("grupo-1"))
                .thenReturn(List.of(previo, respuesta));

        List<GrupoMensajeDTO> resultado = chat.listar("usuario-1", "grupo-1");

        assertEquals(2, resultado.size());
        assertEquals("user", resultado.get(0).rol());
        assertEquals("assistant", resultado.get(1).rol());
    }

    @Test
    void noListaMensajesDeUnGrupoAjeno() {
        grupo.setUsuario(usuario("usuario-2"));

        assertThrows(NoSuchElementException.class,
                () -> chat.listar("usuario-1", "grupo-1"));
    }

    @Test
    void rechazaMensajesVaciosYExtensos() {
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviar("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("   ")));
        String extenso = "a".repeat(2001);
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviar("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO(extenso)));
    }

    @Test
    void enviaYRespondeConElAsistente() {
        when(mensajes.save(any(MGrupoMensaje.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(mensajes.findByIdGrupoOrderByFechaDescIdMensajeDesc(
                eq("grupo-1"), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(mensaje("m-2", "user", "¿qué gastos tengo?"),
                        mensaje("m-1", "user", "hola")));
        when(asistente.responder(any(PeticionChat.class), eq("usuario-1")))
                .thenReturn(new RespuestaChat("Tus gastos este mes son $1.000.000.", List.of(), false, ""));

        List<GrupoMensajeDTO> resultado =
                chat.enviar("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("¿qué gastos tengo?"));

        assertEquals(2, resultado.size());
        assertEquals("user", resultado.get(0).rol());
        assertEquals("assistant", resultado.get(1).rol());
        ArgumentCaptor<PeticionChat> captor = ArgumentCaptor.forClass(PeticionChat.class);
        verify(asistente).responder(captor.capture(), eq("usuario-1"));
        // El asistente recibe el turno propio reciente como historial, sin la pregunta actual duplicada.
        assertEquals("¿qué gastos tengo?", captor.getValue().mensaje());
        assertEquals(1, captor.getValue().historial().size());
        assertEquals("hola", captor.getValue().historial().get(0).contenido());
    }

    @Test
    void respondeInclusoSiElAsistenteFalla() {
        when(mensajes.save(any(MGrupoMensaje.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(mensajes.findByIdGrupoOrderByFechaDescIdMensajeDesc(any(), any()))
                .thenReturn(List.of());
        when(asistente.responder(any(PeticionChat.class), eq("usuario-1")))
                .thenReturn(null);

        List<GrupoMensajeDTO> resultado =
                chat.enviar("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("hola"));

        assertEquals(2, resultado.size());
        assertEquals("assistant", resultado.get(1).rol());
    }

    private MGrupoMensaje mensaje(String id, String rol, String texto) {
        MGrupoMensaje mensaje = new MGrupoMensaje();
        mensaje.setIdMensaje(id);
        mensaje.setIdGrupo("grupo-1");
        mensaje.setIdUsuario("usuario-1");
        mensaje.setRol(rol);
        mensaje.setTexto(texto);
        mensaje.setFecha(LocalDateTime.now());
        return mensaje;
    }

    private MUsuario usuario(String id) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        return usuario;
    }
}
