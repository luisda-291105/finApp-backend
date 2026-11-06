package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.TarjetaSolicitudDTO;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Gestiona el CRUD de tarjetas propias y enmascara el PAN antes de persistir. */
@Service
public class STarjeta {
    private final ITarjeta tarjetas;
    private final IUsuario usuarios;

    public STarjeta(ITarjeta tarjetas, IUsuario usuarios) {
        this.tarjetas = tarjetas;
        this.usuarios = usuarios;
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
