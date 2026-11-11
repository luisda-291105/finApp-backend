package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.ContactoSolicitudDTO;
import com.luisda.fin_app_back.Model.MContacto;
import com.luisda.fin_app_back.Repository.IContacto;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

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
        if (solicitud.origen() != null && solicitud.origen().startsWith("GOOGLE_CONTACTS:")) {
            Optional<MContacto> existente = contactos.findByOrigenAndUsuario_IdUsuario(solicitud.origen(), idUsuario);
            if (existente.isEmpty()) existente = buscarOrigenGoogleAnterior(solicitud, idUsuario);
            if (existente.isPresent()) {
                actualizarDesdeGoogle(existente.get(), solicitud);
                return contactos.save(existente.get());
            }
        }
        var usuario = usuarios.findById(idUsuario).orElseThrow(() -> new IllegalStateException("La sesión no corresponde a un usuario activo."));
        MContacto contacto = new MContacto();
        contacto.setIdContacto(UUID.randomUUID().toString());
        contacto.setUsuario(usuario);
        asignarCampos(contacto, solicitud);
        return contactos.save(contacto);
    }

    @Transactional
    public List<MContacto> importarGoogle(String idUsuario, List<ContactoSolicitudDTO> solicitudes) {
        if (solicitudes == null || solicitudes.size() > 1000) throw new IllegalArgumentException("La importación debe contener hasta 1000 contactos.");
        return solicitudes.stream().map(solicitud -> crear(idUsuario, solicitud)).toList();
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
        if (solicitud.telefono() != null && solicitud.telefono().trim().length() > 64) throw new IllegalArgumentException("El teléfono no puede superar 64 caracteres.");
        if (solicitud.notas() != null && solicitud.notas().length() > 500) throw new IllegalArgumentException("Las notas no pueden superar 500 caracteres.");
        if (solicitud.correo() != null && solicitud.correo().trim().length() > 254) throw new IllegalArgumentException("El correo no puede superar 254 caracteres.");
        if (solicitud.origen() != null && solicitud.origen().length() > 255) throw new IllegalArgumentException("El origen del contacto no es válido.");
        if (solicitud.cargo() != null && solicitud.cargo().trim().length() > 150) throw new IllegalArgumentException("El cargo no puede superar 150 caracteres.");
        if (solicitud.empresa() != null && solicitud.empresa().trim().length() > 150) throw new IllegalArgumentException("La empresa no puede superar 150 caracteres.");
        if (solicitud.correoOrigen() != null && solicitud.correoOrigen().trim().length() > 254) throw new IllegalArgumentException("El correo de origen no puede superar 254 caracteres.");
    }

    private void asignarCampos(MContacto contacto, ContactoSolicitudDTO solicitud) {
        contacto.setNombre(solicitud.nombre().trim());
        contacto.setTelefono(solicitud.telefono() == null || solicitud.telefono().isBlank() ? null : solicitud.telefono().trim());
        contacto.setNotas(solicitud.notas() == null || solicitud.notas().isBlank() ? null : solicitud.notas().trim());
        if (solicitud.correo() != null) contacto.setCorreo(solicitud.correo().isBlank() ? null : solicitud.correo().trim());
        if (solicitud.origen() != null) contacto.setOrigen(solicitud.origen().isBlank() ? null : solicitud.origen().trim());
        if (solicitud.cargo() != null) contacto.setCargo(solicitud.cargo().isBlank() ? null : solicitud.cargo().trim());
        if (solicitud.empresa() != null) contacto.setEmpresa(solicitud.empresa().isBlank() ? null : solicitud.empresa().trim());
        if (solicitud.correoOrigen() != null) contacto.setCorreoOrigen(solicitud.correoOrigen().isBlank() ? null : solicitud.correoOrigen().trim());
    }

    private Optional<MContacto> buscarOrigenGoogleAnterior(ContactoSolicitudDTO solicitud, String idUsuario) {
        String prefijo = "GOOGLE_CONTACTS:";
        String origen = solicitud.origen();
        if (origen == null || !origen.startsWith(prefijo)) return Optional.empty();
        String datos = origen.substring(prefijo.length());
        int separador = datos.indexOf(':');
        if (separador < 0) return Optional.empty();
        String idCuenta = datos.substring(0, separador);
        String recursoGoogle = datos.substring(separador + 1);
        try { UUID.fromString(idCuenta); } catch (IllegalArgumentException error) { return Optional.empty(); }
        if (!recursoGoogle.startsWith("people/")) return Optional.empty();
        Optional<MContacto> legado = contactos.findByOrigenAndUsuario_IdUsuario(prefijo + recursoGoogle, idUsuario);
        return legado.filter(contacto -> coincideIdentidadGoogle(contacto, solicitud));
    }

    private boolean coincideIdentidadGoogle(MContacto contacto, ContactoSolicitudDTO solicitud) {
        boolean correoCoincide = solicitud.correo() != null && !solicitud.correo().isBlank()
                && contacto.getCorreo() != null && solicitud.correo().trim().equalsIgnoreCase(contacto.getCorreo().trim());
        String telefonoSolicitud = normalizarTelefono(solicitud.telefono());
        String telefonoExistente = normalizarTelefono(contacto.getTelefono());
        boolean telefonoCoincide = !telefonoSolicitud.isBlank() && telefonoSolicitud.equals(telefonoExistente);
        if (correoCoincide || telefonoCoincide) return true;
        boolean solicitudSinDatosContacto = (solicitud.correo() == null || solicitud.correo().isBlank()) && telefonoSolicitud.isBlank();
        return solicitudSinDatosContacto && contacto.getNombre() != null
                && contacto.getNombre().trim().equalsIgnoreCase(solicitud.nombre().trim());
    }

    /** Sincroniza Google sin borrar los datos manuales cuando la respuesta omite campos. */
    private void actualizarDesdeGoogle(MContacto contacto, ContactoSolicitudDTO solicitud) {
        contacto.setNombre(solicitud.nombre().trim());
        if (solicitud.telefono() != null && !solicitud.telefono().isBlank()) contacto.setTelefono(solicitud.telefono().trim());
        if (solicitud.notas() != null && !solicitud.notas().isBlank()) contacto.setNotas(solicitud.notas().trim());
        if (solicitud.correo() != null && !solicitud.correo().isBlank()) contacto.setCorreo(solicitud.correo().trim());
        if (solicitud.origen() != null && !solicitud.origen().isBlank()) contacto.setOrigen(solicitud.origen().trim());
        if (solicitud.cargo() != null && !solicitud.cargo().isBlank()) contacto.setCargo(solicitud.cargo().trim());
        if (solicitud.empresa() != null && !solicitud.empresa().isBlank()) contacto.setEmpresa(solicitud.empresa().trim());
        if (solicitud.correoOrigen() != null && !solicitud.correoOrigen().isBlank()) contacto.setCorreoOrigen(solicitud.correoOrigen().trim());
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null) return "";
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.startsWith("00")) digitos = digitos.substring(2);
        if (digitos.startsWith("57") && digitos.length() == 12) digitos = digitos.substring(2);
        return digitos;
    }
}
