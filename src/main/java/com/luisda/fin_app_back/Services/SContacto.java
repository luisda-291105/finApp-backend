package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.ContactoSolicitudDTO;
import com.luisda.fin_app_back.Model.MContacto;
import com.luisda.fin_app_back.Repository.IContacto;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Operaciones de contactos limitadas por propietario y validadas antes de persistir. */
@Service
public class SContacto {
    private final IContacto contactos;
    private final IUsuario usuarios;

    public SContacto(IContacto contactos, IUsuario usuarios) {
        this.contactos = contactos;
        this.usuarios = usuarios;
    }

    public List<MContacto> buscarPorUsuario(String idUsuario) {
        return contactos.findByUsuario_IdUsuario(idUsuario);
    }

    public Optional<MContacto> buscarPorIdPropio(String idContacto, String idUsuario) {
        return contactos.findByIdContactoAndUsuario_IdUsuario(idContacto, idUsuario);
    }

    public MContacto crear(String idUsuario, ContactoSolicitudDTO solicitud) {
        validar(solicitud);
        var usuario = usuarios.findById(idUsuario).orElseThrow(() -> new IllegalStateException("La sesión no corresponde a un usuario activo."));
        MContacto contacto = new MContacto();
        contacto.setIdContacto(UUID.randomUUID().toString());
        contacto.setUsuario(usuario);
        asignarCampos(contacto, solicitud);
        return contactos.save(contacto);
    }

    public MContacto actualizar(String idContacto, String idUsuario, ContactoSolicitudDTO solicitud) {
        validar(solicitud);
        MContacto existente = buscarPorIdPropio(idContacto, idUsuario)
                .orElseThrow(() -> new IllegalStateException("No se encontró el contacto."));
        asignarCampos(existente, solicitud);
        return contactos.save(existente);
    }

    public boolean eliminarPropio(String idContacto, String idUsuario) {
        Optional<MContacto> existente = buscarPorIdPropio(idContacto, idUsuario);
        if (existente.isEmpty()) return false;
        contactos.delete(existente.get());
        return true;
    }

    private void validar(ContactoSolicitudDTO solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (solicitud.nombre().trim().length() > 100) throw new IllegalArgumentException("El nombre no puede superar 100 caracteres.");
        if (solicitud.telefono() == null || solicitud.telefono().isBlank()) throw new IllegalArgumentException("El teléfono es obligatorio.");
        if (solicitud.telefono().trim().length() > 20) throw new IllegalArgumentException("El teléfono no puede superar 20 caracteres.");
        if (solicitud.notas() != null && solicitud.notas().length() > 500) throw new IllegalArgumentException("Las notas no pueden superar 500 caracteres.");
    }

    private void asignarCampos(MContacto contacto, ContactoSolicitudDTO solicitud) {
        contacto.setNombre(solicitud.nombre().trim());
        contacto.setTelefono(solicitud.telefono().trim());
        contacto.setNotas(solicitud.notas() == null || solicitud.notas().isBlank() ? null : solicitud.notas().trim());
    }
}
