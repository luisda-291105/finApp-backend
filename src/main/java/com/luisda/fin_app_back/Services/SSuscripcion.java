package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.DTO.SuscripcionDTO;
import com.luisda.fin_app_back.DTO.SuscripcionSolicitudDTO;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MSuscripcion;
import com.luisda.fin_app_back.Model.MUsuario;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import com.luisda.fin_app_back.Repository.ISuscripcion;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @uso               Gestiona las suscripciones manuales y la consulta unificada de suscripciones
 * @capa              Servicio
 * @responsabilidades CRUD de suscripciones manuales y listado unificado (manuales y de Gmail)
 * @datos             Recibe y devuelve DTOs de Suscripcion
 * @dependencias      ISuscripcion, IUsuario
 * @usadoPor          CSuscripcion
 */
@Service
public class SSuscripcion {

    public static final String IMPORTANCIA_ALTA = "ALTA";
    public static final String IMPORTANCIA_BAJA = "BAJA";

    private static final int LONGITUD_NOMBRE = 100;
    private static final int LONGITUD_CATEGORIA = 60;
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999.99");
    private static final Pattern DESTINO_BAJA = Pattern.compile("(?i)<(" + "(?:https?|mailto)" + ":[^>]+)>");

    private final ISuscripcion suscripciones;
    private final IUsuario usuarios;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SSuscripcion(ISuscripcion suscripciones, IUsuario usuarios) {
        this.suscripciones = suscripciones;
        this.usuarios = usuarios;
    }

    /** Registra una suscripción manual del usuario de la sesión. */
    public SuscripcionDTO crear(String idUsuario, SuscripcionSolicitudDTO solicitud) {
        MUsuario propietario = propietario(idUsuario);
        Datos datos = validar(solicitud);
        Instant ahora = Instant.now();

        MSuscripcion suscripcion = new MSuscripcion();
        suscripcion.setId(UUID.randomUUID().toString());
        suscripcion.setUsuario(propietario);
        suscripcion.setOrigen(OrigenSuscripcion.MANUAL);
        suscripcion.setNombre(datos.nombre());
        suscripcion.setCategoria(datos.categoria());
        suscripcion.setMonto(datos.monto());
        suscripcion.setFechaCobro(datos.fechaCobro());
        suscripcion.setImportancia(datos.importancia());
        suscripcion.setActiva(datos.activa() == null || datos.activa());
        suscripcion.setFechaCreacion(ahora);
        suscripcion.setFechaActualizacion(ahora);
        return aDto(this.suscripciones.save(suscripcion));
    }

    /**
     * Lista las suscripciones del usuario: primero las manuales por fecha de cobro y después
     * las detectadas en Gmail por actividad reciente.
     */
    @Transactional(readOnly = true)
    public List<SuscripcionDTO> listar(String idUsuario, OrigenSuscripcion origen) {
        List<MSuscripcion> encontradas = origen == null
                ? this.suscripciones.findDelUsuario(idUsuario)
                : this.suscripciones.findDelUsuarioPorOrigen(idUsuario, origen);
        List<MSuscripcion> manuales = new ArrayList<>();
        List<MSuscripcion> deGmail = new ArrayList<>();
        for (MSuscripcion suscripcion : encontradas) {
            if (suscripcion.getOrigen() == OrigenSuscripcion.GMAIL) deGmail.add(suscripcion);
            else manuales.add(suscripcion);
        }
        manuales.sort(Comparator
                .comparing(MSuscripcion::getFechaCobro, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.getNombre(), Comparator.nullsLast(String::compareToIgnoreCase)));
        deGmail.sort(Comparator
                .comparing(MSuscripcion::getUltimaActividad, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(s -> s.getNombre(), Comparator.nullsLast(String::compareToIgnoreCase)));
        List<MSuscripcion> ordenadas = new ArrayList<>(manuales);
        ordenadas.addAll(deGmail);
        return ordenadas.stream().map(this::aDto).toList();
    }

    /** Obtiene una suscripción del usuario, sea manual o de Gmail. */
    @Transactional(readOnly = true)
    public SuscripcionDTO obtener(String idUsuario, String id) {
        return aDto(buscarPropia(idUsuario, id));
    }

    /** Actualiza una suscripción manual. Las detectadas en Gmail no se editan desde aquí. */
    @Transactional
    public SuscripcionDTO actualizar(String idUsuario, String id, SuscripcionSolicitudDTO solicitud) {
        MSuscripcion suscripcion = buscarPropia(idUsuario, id);
        exigirManual(suscripcion);
        Datos datos = validar(solicitud);
        suscripcion.setNombre(datos.nombre());
        suscripcion.setCategoria(datos.categoria());
        suscripcion.setMonto(datos.monto());
        suscripcion.setFechaCobro(datos.fechaCobro());
        suscripcion.setImportancia(datos.importancia());
        if (datos.activa() != null) suscripcion.setActiva(datos.activa());
        suscripcion.setFechaActualizacion(Instant.now());
        return aDto(this.suscripciones.save(suscripcion));
    }

    /** Activa o pausa una suscripción manual. */
    @Transactional
    public SuscripcionDTO cambiarEstado(String idUsuario, String id, Boolean activa) {
        if (activa == null) {
            throw new DatosInvalidosException("Debes indicar si la suscripción queda activa o pausada.", "activa");
        }
        MSuscripcion suscripcion = buscarPropia(idUsuario, id);
        exigirManual(suscripcion);
        suscripcion.setActiva(activa);
        suscripcion.setFechaActualizacion(Instant.now());
        return aDto(this.suscripciones.save(suscripcion));
    }

    /** Elimina una suscripción manual. Las detectadas en Gmail se limpian al desconectar la cuenta. */
    @Transactional
    public void eliminar(String idUsuario, String id) {
        MSuscripcion suscripcion = buscarPropia(idUsuario, id);
        exigirManual(suscripcion);
        this.suscripciones.delete(suscripcion);
    }

    /** Convierte la entidad en el DTO que consume el cliente. */
    public SuscripcionDTO aDto(MSuscripcion suscripcion) {
        String unsubscribe = valorSeguro(suscripcion.getListUnsubscribe());
        MFuente cuenta = suscripcion.getCuenta();
        return new SuscripcionDTO(
                suscripcion.getId(),
                suscripcion.getOrigen() == null ? null : suscripcion.getOrigen().name(),
                suscripcion.getNombre(),
                suscripcion.getCategoria(),
                leerLista(suscripcion.getCategoriasJson()),
                suscripcion.getMonto(),
                suscripcion.getFechaCobro(),
                suscripcion.getImportancia(),
                suscripcion.isActiva(),
                suscripcion.getCorreoRemitente(),
                cuenta == null ? null : cuenta.getIdFuente(),
                cuenta == null ? null : cuenta.getContenido(),
                extraerDestinoBaja(unsubscribe, "https?"),
                extraerDestinoBaja(unsubscribe, "mailto"),
                valorSeguro(suscripcion.getListUnsubscribePost()).toLowerCase(Locale.ROOT)
                        .contains("list-unsubscribe=one-click"),
                suscripcion.getUltimaActividad(),
                suscripcion.getFechaCreacion(),
                suscripcion.getFechaActualizacion()
        );
    }

    /** Busca una suscripción del usuario; si no existe o es de otro usuario, no se revela. */
    private MSuscripcion buscarPropia(String idUsuario, String id) {
        if (id == null || id.isBlank()) {
            throw new NoSuchElementException("No se encontró la suscripción.");
        }
        return this.suscripciones.findPropia(id, idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró la suscripción."));
    }

    /** Las suscripciones de Gmail se administran desde la cuenta conectada, no desde este CRUD. */
    private void exigirManual(MSuscripcion suscripcion) {
        if (suscripcion.getOrigen() == OrigenSuscripcion.GMAIL) {
            throw new NoEditableException(
                    "Las suscripciones detectadas en Gmail se administran desde la cuenta de correo conectada.");
        }
    }

    private MUsuario propietario(String idUsuario) {
        if (idUsuario == null || idUsuario.isBlank()) {
            throw new IllegalStateException("No hay una sesión activa.");
        }
        return this.usuarios.findById(idUsuario)
                .orElseThrow(() -> new IllegalStateException("La cuenta de la sesión no está disponible."));
    }

    private Datos validar(SuscripcionSolicitudDTO solicitud) {
        if (solicitud == null) {
            throw new DatosInvalidosException("Los datos de la suscripción son obligatorios.", null);
        }
        String nombre = normalizar(solicitud.nombre());
        if (nombre == null) {
            throw new DatosInvalidosException("El nombre es obligatorio.", "nombre");
        }
        if (nombre.length() > LONGITUD_NOMBRE) {
            throw new DatosInvalidosException(
                    "El nombre no puede superar " + LONGITUD_NOMBRE + " caracteres.", "nombre");
        }
        String categoria = normalizar(solicitud.categoria());
        if (categoria == null) {
            throw new DatosInvalidosException("La categoría es obligatoria.", "categoria");
        }
        if (categoria.length() > LONGITUD_CATEGORIA) {
            throw new DatosInvalidosException(
                    "La categoría no puede superar " + LONGITUD_CATEGORIA + " caracteres.", "categoria");
        }
        if (solicitud.monto() == null) {
            throw new DatosInvalidosException("El monto es obligatorio.", "monto");
        }
        BigDecimal monto = solicitud.monto().setScale(2, RoundingMode.HALF_UP);
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DatosInvalidosException("El monto debe ser mayor que cero.", "monto");
        }
        if (monto.compareTo(MONTO_MAXIMO) > 0) {
            throw new DatosInvalidosException("El monto no puede superar 9999999999.99.", "monto");
        }
        LocalDate fechaCobro = solicitud.fechaCobro();
        if (fechaCobro == null) {
            throw new DatosInvalidosException("La fecha de cobro es obligatoria.", "fechaCobro");
        }
        return new Datos(nombre, categoria, monto, fechaCobro,
                normalizarImportancia(solicitud.importancia()), solicitud.activa());
    }

    private String normalizarImportancia(String valor) {
        if (valor == null || valor.isBlank()) return IMPORTANCIA_ALTA;
        return switch (valor.trim().toUpperCase(Locale.ROOT)) {
            case "ALTA", "ALTO", "IMPORTANTE" -> IMPORTANCIA_ALTA;
            case "BAJA", "BAJO" -> IMPORTANCIA_BAJA;
            default -> throw new DatosInvalidosException("La importancia debe ser Alta o Baja.", "importancia");
        };
    }

    private String normalizar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String valorSeguro(String valor) {
        return valor == null ? "" : valor;
    }

    private List<String> leerLista(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return this.objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            return List.of();
        }
    }

    private String extraerDestinoBaja(String valor, String protocolo) {
        if (valor == null || valor.isBlank()) return "";
        Matcher matcher = Pattern.compile("(?i)<(" + protocolo + ":[^>]+)>").matcher(valor);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /** Datos ya validados y normalizados de la solicitud. */
    private record Datos(String nombre, String categoria, BigDecimal monto, LocalDate fechaCobro,
                         String importancia, Boolean activa) { }

    /** Error de validación con el campo que se debe corregir en el formulario. */
    public static class DatosInvalidosException extends RuntimeException {
        private final String campo;

        public DatosInvalidosException(String mensaje, String campo) {
            super(mensaje);
            this.campo = campo;
        }

        public String getCampo() { return campo; }
    }

    /** La suscripción pertenece a Gmail y no admite edición manual. */
    public static class NoEditableException extends RuntimeException {
        public NoEditableException(String mensaje) {
            super(mensaje);
        }
    }
}
