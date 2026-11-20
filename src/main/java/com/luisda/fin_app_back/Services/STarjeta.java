package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.TarjetaSolicitudDTO;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Gestiona el CRUD de tarjetas propias y enmascara el PAN antes de persistir. */
@Service
public class STarjeta {
    private static final Pattern CUENTA_EN_CORREO = Pattern.compile(
            "(?i)(?:cuenta(?:\\s+(?:origen|destino))?|tarjeta(?:\\s+(?:de\\s+)?(?:d[eé]bito|cr[eé]dito))?|t\\.\\s*(?:cred|d[eé]b)\\.?)\\D{0,16}(\\d{4,})");
    private static final Set<String> TIPOS_CUENTA = Set.of("AHORRO", "CREDITO", "CORRIENTE");

    /**
     * Normaliza el tipo de cuenta a mayúsculas sin acentos. Devuelve null cuando no es
     * «ahorro», «crédito» o «corriente» (exacto, insensible a acentos y mayúsculas).
     */
    public static String normalizarTipoCuenta(String valor) {
        if (valor == null) return null;
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", "");
        return TIPOS_CUENTA.contains(normalizado) ? normalizado : null;
    }

    private final ITarjeta tarjetas;
    private final IUsuario usuarios;
    private final IMovimientoContable movimientos;

    public STarjeta(ITarjeta tarjetas, IUsuario usuarios, IMovimientoContable movimientos) {
        this.tarjetas = tarjetas;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
    }

    /** Historial de movimientos de contabilidad vinculados a una tarjeta propia del usuario. */
    public List<MMovimientoContable> historialMovimientos(String idTarjeta, String idUsuario) {
        Optional<MTarjeta> tarjetaPropia = buscarPorIdPropio(idTarjeta, idUsuario);
        if (tarjetaPropia.isEmpty()) return null;
        String digitos = tarjetaPropia.get().getNumero() == null
                ? "" : tarjetaPropia.get().getNumero().replaceAll("\\D", "");
        String sufijo = digitos.length() < 4 ? "" : digitos.substring(digitos.length() - 4);
        LinkedHashMap<String, MMovimientoContable> historial = new LinkedHashMap<>();
        movimientos.findByIdUsuarioAndIdTarjetaOrderByFechaDesc(idUsuario, idTarjeta)
                .forEach(movimiento -> historial.put(movimiento.getIdMovimiento(), movimiento));
        if (!sufijo.isEmpty()) {
            movimientos.findByIdUsuarioAndOrigenTipoIgnoreCaseOrderByFechaDesc(
                    idUsuario, "GMAIL").stream()
                    .filter(movimiento -> movimiento.getEstadoRevision() != EstadoRevisionMovimiento.POR_REVISAR)
                    .filter(movimiento -> coincideSufijoCuenta(movimiento.getOrigenId(), sufijo))
                    .forEach(movimiento -> historial.putIfAbsent(movimiento.getIdMovimiento(), movimiento));
        }
        return historial.values().stream()
                .sorted(Comparator.comparing(MMovimientoContable::getFecha,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean coincideSufijoCuenta(String origenId, String sufijo) {
        if (origenId == null || origenId.isBlank()) return false;
        String fragmento = movimientos.findFragmentoPorOrigenId(origenId);
        if (fragmento == null || fragmento.isBlank()) return false;
        Matcher matcher = CUENTA_EN_CORREO.matcher(fragmento);
        while (matcher.find()) {
            String digitos = matcher.group(1);
            if (digitos.length() >= 4 && digitos.endsWith(sufijo)) return true;
        }
        return false;
    }

    public List<MTarjeta> buscarPorUsuario(String idUsuario) {
        return tarjetas.findByUsuario_IdUsuario(idUsuario);
    }

    public Optional<MTarjeta> buscarPorIdPropio(String idTarjeta, String idUsuario) {
        return tarjetas.findByIdTarjetaAndUsuario_IdUsuario(idTarjeta, idUsuario);
    }

    public MTarjeta crear(String idUsuario, TarjetaSolicitudDTO solicitud) {
        validar(solicitud, true);
        var usuario = usuarios.findById(idUsuario)
                .orElseThrow(() -> new IllegalStateException("La sesión no corresponde a un usuario activo."));
        MTarjeta tarjeta = new MTarjeta();
        tarjeta.setIdTarjeta(UUID.randomUUID().toString());
        tarjeta.setUsuario(usuario);
        tarjeta.setTitular(solicitud.titular().trim());
        tarjeta.setEntidad(solicitud.entidad().trim());
        tarjeta.setExpiracion(solicitud.expiracion());
        tarjeta.setNumero(enmascararPan(solicitud.numero()));
        tarjeta.setCvv(""); // Compatibilidad con bases antiguas: solo se escribe el valor vacío, nunca un CVV.
        tarjeta.setTipoCuenta(normalizarTipoCuenta(solicitud.tipoCuenta()));
        return tarjetas.save(tarjeta);
    }

    public MTarjeta actualizar(String idTarjeta, String idUsuario, TarjetaSolicitudDTO solicitud) {
        validar(solicitud, false);
        MTarjeta existente = buscarPorIdPropio(idTarjeta, idUsuario)
                .orElseThrow(() -> new IllegalStateException("No se encontró la tarjeta."));
        existente.setTitular(solicitud.titular().trim());
        existente.setEntidad(solicitud.entidad().trim());
        existente.setExpiracion(solicitud.expiracion());
        existente.setNumero(solicitud.numero() == null || solicitud.numero().isBlank()
                ? enmascararPan(existente.getNumero())
                : enmascararPan(solicitud.numero()));
        existente.setCvv("");
        existente.setTipoCuenta(normalizarTipoCuenta(solicitud.tipoCuenta()));
        return tarjetas.save(existente);
    }

    public boolean eliminarPropia(String idTarjeta, String idUsuario) {
        Optional<MTarjeta> existente = buscarPorIdPropio(idTarjeta, idUsuario);
        if (existente.isEmpty()) return false;
        tarjetas.delete(existente.get());
        return true;
    }

    /** Devuelve solo una máscara con los últimos cuatro dígitos del valor recibido. */
    public static String enmascararPan(String numero) {
        String digitos = numero == null ? "" : numero.replaceAll("\\D", "");
        String ultimos = digitos.length() > 4 ? digitos.substring(digitos.length() - 4) : digitos;
        return "**** " + ultimos;
    }

    private void validar(TarjetaSolicitudDTO solicitud, boolean requierePan) {
        if (solicitud == null || solicitud.titular() == null || solicitud.titular().isBlank()) {
            throw new IllegalArgumentException("El titular es obligatorio.");
        }
        if (solicitud.titular().trim().length() > 100) throw new IllegalArgumentException("El titular no puede superar 100 caracteres.");
        if (solicitud.entidad() == null || solicitud.entidad().isBlank()) throw new IllegalArgumentException("La entidad financiera es obligatoria.");
        if (solicitud.entidad().trim().length() > 100) throw new IllegalArgumentException("La entidad no puede superar 100 caracteres.");
        String tipoCuenta = normalizarTipoCuenta(solicitud.tipoCuenta());
        if (tipoCuenta == null) {
            throw new IllegalArgumentException("El tipo de cuenta es obligatorio. Usa ahorro, crédito o corriente.");
        }
        if (solicitud.expiracion() == null) throw new IllegalArgumentException("El vencimiento es obligatorio.");
        LocalDate mesActual = LocalDate.now().withDayOfMonth(1);
        if (solicitud.expiracion().withDayOfMonth(1).isBefore(mesActual)) {
            throw new IllegalArgumentException("La fecha de vencimiento debe ser el mes actual o uno futuro.");
        }
        String pan = solicitud.numero();
        if (requierePan || (pan != null && !pan.isBlank())) {
            if (pan == null || !pan.matches("\\d{14,19}")) {
                throw new IllegalArgumentException("El número debe contener solo dígitos (14 a 19).");
            }
        }
    }
}
