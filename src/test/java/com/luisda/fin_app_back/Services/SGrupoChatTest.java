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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SGrupoChatTest {
    private IGrupo grupos;
    private IGrupoMiembro miembros;
    private IGrupoMensaje mensajes;
    private IUsuario usuarios;
    private SGrupoChat chat;
    private MGrupo grupo;
    private MUsuario dueno;
    private MUsuario otraUsuario;

    @BeforeEach
    void preparar() {
        grupos = mock(IGrupo.class);
        miembros = mock(IGrupoMiembro.class);
        mensajes = mock(IGrupoMensaje.class);
        usuarios = mock(IUsuario.class);
        chat = new SGrupoChat(grupos, miembros, mensajes, usuarios);
        dueno = usuario("usuario-1", "Luis Daniel", "luis@finapp.co", "3001112233");
        otraUsuario = usuario("usuario-2", "María", "maria@finapp.co", "3004445566");
        grupo = new MGrupo();
        grupo.setIdGrupo("grupo-1");
        grupo.setNombre("Viaje");
        grupo.setUsuario(dueno);
        when(grupos.findById("grupo-1")).thenReturn(Optional.of(grupo));
    }

    @Test
    void listaLosMensajesConElNombreDelAutor() {
        MGrupoMensaje mensaje = mensaje("m-1", "user", "hola grupo", "usuario-1");
        mensaje.setIdUsuario("usuario-2");
        when(mensajes.findByIdGrupoOrderByFechaAscIdMensajeAsc("grupo-1"))
                .thenReturn(List.of(mensaje));
        when(usuarios.findAllById(List.of("usuario-2"))).thenReturn(List.of(otraUsuario));

        List<GrupoMensajeDTO> resultado = chat.listarMensajes("usuario-1", "grupo-1");

        assertEquals(1, resultado.size());
        assertEquals("María", resultado.get(0).nombreAutor());
    }

    @Test
    void noListaNiEnviaSiElGrupoNoEsPropioNiEsMiembro() {
        cuandoDuenoEsOtra();

        assertThrows(AccesoGrupoDenegadoException.class,
                () -> chat.listarMensajes("usuario-1", "grupo-1"));
        assertThrows(AccesoGrupoDenegadoException.class,
                () -> chat.enviarMensaje("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("hola")));
    }

    private void cuandoDuenoEsOtra() {
        grupo.setUsuario(otraUsuario);
        when(miembros.findByIdGrupoAndIdUsuario("grupo-1", "usuario-1"))
                .thenReturn(Optional.empty());
    }

    @Test
    void persisteElMensajeConLaIdentidadDelAutor() {
        when(mensajes.save(any(MGrupoMensaje.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(usuarios.findById("usuario-1")).thenReturn(Optional.of(dueno));

        GrupoMensajeDTO resultado = chat.enviarMensaje(
                "usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("hola equipo"));

        assertEquals("hola equipo", resultado.texto());
        assertEquals("Luis Daniel", resultado.nombreAutor());
        assertEquals("usuario-1", resultado.idUsuario());
    }

    @Test
    void rechazaMensajesVaciosYExtensos() {
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviarMensaje("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("   ")));
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviarMensaje("usuario-1", "grupo-1", new GrupoMensajeSolicitudDTO("a".repeat(2001))));
        verify(mensajes, never()).save(any());
    }

    @Test
    void buscaUsuariosPorIdentificacionCorreoTelefonoYNombre() {
        when(usuarios.buscarPorTermino("maria")).thenReturn(List.of(otraUsuario,
                usuario("usuario-3", "María", "otra@finapp.co", "3007778899")));

        List<GrupoUsuarioResumenDTO> resultado = chat.buscarUsuarios("maria");

        assertEquals(2, resultado.size());
        assertEquals("María", resultado.get(0).nombre());
        verify(usuarios).buscarPorTermino("maria");

        assertThrows(IllegalArgumentException.class, () -> chat.buscarUsuarios("a"));
    }

    @Test
    void elPropietarioPuedeAdministrarMiembros() {
        MGrupoMiembro[] guardo = new MGrupoMiembro[1];
        when(miembros.save(any(MGrupoMiembro.class)))
                .thenAnswer(invocacion -> { guardo[0] = invocacion.getArgument(0); return guardo[0]; });
        when(miembros.findByIdGrupo("grupo-1"))
                .thenAnswer(invocacion -> guardo[0] == null ? List.of() : List.of(guardo[0]));
        Map<String, MUsuario> conocidos = Map.of("usuario-1", dueno, "usuario-2", otraUsuario);
        when(usuarios.findAllById(any()))
                .thenAnswer(invocacion -> {
                    List<MUsuario> resultado = new java.util.ArrayList<>();
                    for (String id : (Iterable<String>) invocacion.getArgument(0)) {
                        if (conocidos.containsKey(id)) resultado.add(conocidos.get(id));
                    }
                    return resultado;
                });
        when(usuarios.findById("usuario-2")).thenReturn(Optional.of(otraUsuario));
        when(usuarios.findByCorreoIgnoreCase("maria@finapp.co")).thenReturn(Optional.empty());
        when(usuarios.findByTelefono("3004445566")).thenReturn(Optional.empty());

        List<GrupoMiembroDTO> resultado = chat.agregarMiembro("usuario-1", "grupo-1", "usuario-2");

        assertEquals(2, resultado.size());
        assertEquals("DUENO", resultado.get(0).rol());
        assertEquals("MIEMBRO", resultado.get(1).rol());

        when(miembros.deleteByIdGrupoAndIdUsuario("grupo-1", "usuario-2")).thenReturn(1L);
        guardo[0] = null; // el integrante quedó fuera
        resultado = chat.eliminarMiembro("usuario-1", "grupo-1", "usuario-2");
        assertEquals(1, resultado.size());
        assertEquals("DUENO", resultado.get(0).rol());
    }

    @Test
    void unMiembroNoPuedeAdministrarMiembrosNiEliminarseASiMismoElDueno() {
        // Un miembro (no propietario) no puede añadir ni quitar integrantes.
        grupo.setUsuario(otraUsuario);
        when(miembros.findByIdGrupoAndIdUsuario("grupo-1", "usuario-1"))
                .thenReturn(Optional.of(miembro("u-m", "grupo-1", "usuario-1", "MIEMBRO")));

        assertThrows(AccesoGrupoDenegadoException.class,
                () -> chat.agregarMiembro("usuario-1", "grupo-1", "usuario-3"));
        assertThrows(AccesoGrupoDenegadoException.class,
                () -> chat.eliminarMiembro("usuario-1", "grupo-1", "usuario-1"));

        // El propietario no se puede quitar a sí mismo.
        grupo.setUsuario(dueno);
        assertThrows(IllegalArgumentException.class,
                () -> chat.eliminarMiembro("usuario-1", "grupo-1", "usuario-1"));
    }

    @Test
    void agregarMiembroRechazaDuplicadosYUsuariosInexistentes() {
        when(miembros.findByIdGrupo("grupo-1")).thenReturn(List.of());
        when(usuarios.findById("usuario-2")).thenReturn(Optional.of(otraUsuario));
        when(usuarios.findByCorreoIgnoreCase("maria@finapp.co")).thenReturn(Optional.empty());
        when(usuarios.findByTelefono("3004445566")).thenReturn(Optional.empty());
        when(miembros.existsByIdGrupoAndIdUsuario("grupo-1", "usuario-2")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> chat.agregarMiembro("usuario-1", "grupo-1", "usuario-2"));
        verify(miembros, never()).save(any());
    }

    @Test
    void listarMiembrosReconstruyeElDuenoSiElGrupoNoTieneFilas() {
        when(miembros.findByIdGrupo("grupo-1")).thenReturn(List.of());
        when(usuarios.findAllById(any())).thenReturn(List.of(dueno));

        List<GrupoMiembroDTO> resultado = chat.listarMiembros("usuario-1", "grupo-1");

        assertEquals(1, resultado.size());
        assertEquals("DUENO", resultado.get(0).rol());
        assertEquals("Luis Daniel", resultado.get(0).nombre());
    }

    private MUsuario usuario(String id, String nombre, String correo, String telefono) {
        MUsuario usuario = new MUsuario();
        usuario.setIdUsuario(id);
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setTelefono(telefono);
        usuario.setFechaCreacion(LocalDateTime.now().toLocalDate());
        return usuario;
    }

    private MGrupoMensaje mensaje(String id, String rol, String texto, String usuarioId) {
        MGrupoMensaje mensaje = new MGrupoMensaje();
        mensaje.setIdMensaje(id);
        mensaje.setIdGrupo("grupo-1");
        mensaje.setIdUsuario(usuarioId);
        mensaje.setRol(rol);
        mensaje.setTexto(texto);
        mensaje.setFecha(LocalDateTime.now());
        return mensaje;
    }

    private MGrupoMiembro miembro(String id, String idGrupo, String idUsuario, String rol) {
        MGrupoMiembro miembro = new MGrupoMiembro();
        miembro.setIdMiembro(id);
        miembro.setIdGrupo(idGrupo);
        miembro.setIdUsuario(idUsuario);
        miembro.setRol(rol);
        miembro.setFecha(LocalDateTime.now());
        return miembro;
    }
}
