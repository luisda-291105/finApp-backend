package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.luisda.fin_app_back.Repository.IContacto;
import com.luisda.fin_app_back.Repository.IFuentes;
import com.luisda.fin_app_back.Repository.IGrupo;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IMuestraRepository;
import com.luisda.fin_app_back.Model.MContacto;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MGrupo;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Model.MMuestra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * @uso               Gestiona las operaciones disponibles de usuarios
 * @capa              Servicio
 * @responsabilidades CRUD, búsquedas por contacto y nombre, y unicidad del contacto
 * @datos             Recibe y devuelve entidades MUsuario
 * @dependencias      UsuarioRepository, SValidadorOperaciones
 * @usadoPor          Capas consumidoras de servicios
 */
@Service
public  class SUsuario {
    private static final Pattern CORREO_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO_VALIDO = Pattern.compile("^\\+?\\d{7,15}$");
    private final IUsuario IUsuario;
    private final SVerificadorContrasena verificador;
    private final IContacto contactos;
    private final IFuentes fuentes;
    private final IGrupo grupos;
    private final ITarjeta tarjetas;
    private final IMuestraRepository muestras;

    // Constructor
    public SUsuario(IUsuario iUsuario, SVerificadorContrasena verificador, IContacto contactos,
                    IFuentes fuentes, IGrupo grupos, ITarjeta tarjetas,
                    IMuestraRepository muestras) {
        IUsuario = iUsuario;
        this.verificador = verificador;
        this.contactos = contactos;
        this.fuentes = fuentes;
        this.grupos = grupos;
        this.tarjetas = tarjetas;
        this.muestras = muestras;
    }

    /** Agrega un usuario  */
    public MUsuario crear(MUsuario MUsuario) throws Exception{
        if (MUsuario == null || MUsuario.getIdUsuario() == null || MUsuario.getIdUsuario().isBlank()) {
            throw new IllegalArgumentException("La identificación es obligatoria.");
        }
        String identificacion = MUsuario.getIdUsuario().trim();
        if (IUsuario.existsById(identificacion)) {
            throw new IllegalStateException("Ya existe un usuario con esta identificación.");
        }
        if (MUsuario.getContrasena() == null || MUsuario.getContrasena().length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        String correo = normalizarCorreo(MUsuario.getCorreo());
        if (correo != null && !CORREO_VALIDO.matcher(correo).matches()) {
            throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        }
        if (correo != null && (IUsuario.findByCorreoIgnoreCase(correo).isPresent()
                || IUsuario.findByCorreoGoogleIgnoreCase(correo).isPresent())) {
            throw new IllegalStateException("Ya existe una cuenta con este correo.");
        }
        String telefono = normalizarTelefono(MUsuario.getTelefono());
        if (telefono != null && !TELEFONO_VALIDO.matcher(telefono).matches()) {
            throw new IllegalArgumentException("Ingresa un teléfono válido con al menos 7 dígitos.");
        }
        if (telefono != null && IUsuario.findByTelefono(telefono).isPresent()) {
            throw new IllegalStateException("Ya existe una cuenta con este teléfono.");
        }
        MUsuario.setIdUsuario(identificacion);
        MUsuario.setCorreo(correo);
        MUsuario.setTelefono(telefono);
        MUsuario.setContrasena(verificador.codificar(MUsuario.getContrasena()));
        return IUsuario.save(MUsuario);
    }

    /** Valida por nombre, identificación, correo o teléfono y migra contraseñas heredadas. */
    public MUsuario autenticar(String identificador, String contrasena, SVerificadorContrasena codificador) {
        if (identificador == null || contrasena == null) return null;
        String valor = identificador.trim();
        Map<String, MUsuario> candidatos = new LinkedHashMap<>();
        IUsuario.findById(valor).ifPresent(usuario -> candidatos.put(usuario.getIdUsuario(), usuario));
        if (valor.contains("@")) {
            IUsuario.findByCorreoIgnoreCase(valor).ifPresent(usuario -> candidatos.put(usuario.getIdUsuario(), usuario));
            IUsuario.findByCorreoGoogleIgnoreCase(valor).ifPresent(usuario -> candidatos.put(usuario.getIdUsuario(), usuario));
        }
        String telefono = normalizarTelefono(valor);
        if (telefono != null && TELEFONO_VALIDO.matcher(telefono).matches()) {
            IUsuario.findByTelefono(telefono).ifPresent(usuario -> candidatos.put(usuario.getIdUsuario(), usuario));
        }
        IUsuario.findByNombreIgnoreCase(valor).forEach(usuario -> candidatos.put(usuario.getIdUsuario(), usuario));

        List<MUsuario> validos = new ArrayList<>();
        for (MUsuario usuario : candidatos.values()) {
            String guardada = usuario.getContrasena();
            if (codificador.verificar(contrasena, guardada)) {
                validos.add(usuario);
                continue;
            }
            if (guardada != null && !guardada.startsWith(SVerificadorContrasena.PREFIJO)
                    && java.security.MessageDigest.isEqual(guardada.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                usuario.setContrasena(codificador.codificar(contrasena));
                IUsuario.save(usuario);
                validos.add(usuario);
            }
        }
        return validos.size() == 1 ? validos.get(0) : null;
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String limpio = telefono.trim();
        if (!limpio.matches("^[+\\d\\s().-]+$")) return limpio;
        return limpio.replaceAll("[\\s().-]", "");
    }

    private String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) return null;
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    /** Consulta todos los usuarios. */
    public List<MUsuario> buscarTodos() throws Exception{
        try {
            return this.IUsuario.findAll();
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por nombre */
    public List<MUsuario> buscarPorNombre(String nombreUsuario) throws Exception{
        try {
            return this.IUsuario.buscarPorNombre(nombreUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Consulta un usuario por identificador. */
    public Optional<MUsuario> buscarPorId(String idUsuario) throws Exception{
        try {
            return this.IUsuario.findById(idUsuario);
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Elimina el usuario identificado */
    public Boolean eliminar(String idUsuario) throws Exception{
        try {
            Optional<MUsuario> usuarioEncontrado = this.IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()){
                this.IUsuario.deleteById(idUsuario);
                return true;
            }else {
                throw new Exception("No se puede eliminar porque usuario no registrado");
            }
        }catch (Exception error) {
            throw new Exception(error.getMessage());
        }
    }

    /** Actualiza un usuario existente y comprueba que exista */

    @Transactional
    public MUsuario modificar(MUsuario cambios , String idUsuario ) throws Exception{
            Optional<MUsuario> usuarioEncontrado = IUsuario.findById(idUsuario);
            if (usuarioEncontrado.isPresent()) {
                MUsuario anterior = usuarioEncontrado.get();
                String nuevoId = cambios.getIdUsuario() == null ? idUsuario : cambios.getIdUsuario().trim();
                if (nuevoId.isBlank()) throw new IllegalArgumentException("La identificación es obligatoria.");
                if (!nuevoId.equals(idUsuario) && IUsuario.existsById(nuevoId)) {
                    throw new IllegalStateException("Ya existe un usuario con esta identificación.");
                }
                if (cambios.getNombre() == null || cambios.getNombre().isBlank()) {
                    throw new IllegalArgumentException("El nombre es obligatorio.");
                }
                if (cambios.getContrasena() != null && !cambios.getContrasena().isBlank()
                        && cambios.getContrasena().length() < 8) {
                    throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
                }

                String correoNuevo = cambios.getCorreo() == null ? anterior.getCorreo() : normalizarCorreo(cambios.getCorreo());
                if (correoNuevo != null && !CORREO_VALIDO.matcher(correoNuevo).matches()) {
                    throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
                }
                if (correoNuevo != null && !correoNuevo.equalsIgnoreCase(anterior.getCorreo())
                        && (IUsuario.findByCorreoIgnoreCase(correoNuevo).isPresent()
                        || IUsuario.findByCorreoGoogleIgnoreCase(correoNuevo).isPresent())) {
                    throw new IllegalStateException("Ya existe una cuenta con este correo.");
                }

                String telefonoNuevo = cambios.getTelefono() == null ? anterior.getTelefono() : normalizarTelefono(cambios.getTelefono());
                if (telefonoNuevo != null && !TELEFONO_VALIDO.matcher(telefonoNuevo).matches()) {
                    throw new IllegalArgumentException("Ingresa un teléfono válido con al menos 7 dígitos.");
                }
                if (telefonoNuevo != null && !telefonoNuevo.equals(anterior.getTelefono())
                        && IUsuario.findByTelefono(telefonoNuevo).isPresent()) {
                    throw new IllegalStateException("Ya existe una cuenta con este teléfono.");
                }

                anterior.setNombre(cambios.getNombre().trim());
                if (cambios.getContrasena() != null && !cambios.getContrasena().isBlank()) {
                    anterior.setContrasena(verificador.codificar(cambios.getContrasena()));
                }
                if (nuevoId.equals(idUsuario)) {
                    anterior.setCorreo(correoNuevo);
                    anterior.setTelefono(telefonoNuevo);
                    return IUsuario.save(anterior);
                }

                // Cambiar una PK requiere trasladar primero la cuenta y luego sus relaciones.
                MUsuario actualizado = new MUsuario();
                actualizado.setIdUsuario(nuevoId);
                actualizado.setNombre(anterior.getNombre());
                actualizado.setContrasena(anterior.getContrasena());
                actualizado.setFechaCreacion(anterior.getFechaCreacion());
                MUsuario nuevoUsuario = IUsuario.saveAndFlush(actualizado);

                List<MContacto> listaContactos = contactos.findByUsuario_IdUsuario(idUsuario);
                listaContactos.forEach(registro -> registro.setUsuario(nuevoUsuario));
                contactos.saveAll(listaContactos);
                List<MFuente> listaFuentes = fuentes.findByUsuario_IdUsuario(idUsuario);
                listaFuentes.forEach(registro -> registro.setUsuario(nuevoUsuario));
                fuentes.saveAll(listaFuentes);
                List<MGrupo> listaGrupos = grupos.findByUsuario_IdUsuario(idUsuario);
                listaGrupos.forEach(registro -> registro.setUsuario(nuevoUsuario));
                grupos.saveAll(listaGrupos);
                List<MTarjeta> listaTarjetas = tarjetas.findByUsuario_IdUsuario(idUsuario);
                listaTarjetas.forEach(registro -> registro.setUsuario(nuevoUsuario));
                tarjetas.saveAll(listaTarjetas);

                List<MMuestra> listaMuestras = muestras.findByUsuarioId(idUsuario);
                listaMuestras.forEach(registro -> registro.setUsuarioId(nuevoId));
                muestras.saveAll(listaMuestras);
                IUsuario.delete(anterior);
                IUsuario.flush();
                nuevoUsuario.setCorreo(correoNuevo);
                nuevoUsuario.setTelefono(telefonoNuevo);
                nuevoUsuario.setGoogleSubject(anterior.getGoogleSubject());
                nuevoUsuario.setCorreoGoogle(anterior.getCorreoGoogle());
                IUsuario.save(nuevoUsuario);
                return nuevoUsuario;
            }
            throw new IllegalStateException("No se puede modificar porque el usuario no está registrado.");
    }
}
