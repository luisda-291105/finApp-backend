package com.luisda.fin_app_back.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luisda.fin_app_back.Model.MFuente;
import com.luisda.fin_app_back.Model.MSuscripcion;
import com.luisda.fin_app_back.Model.OrigenSuscripcion;
import com.luisda.fin_app_back.Repository.ISuscripcion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @uso               Persistencia de las suscripciones detectadas en las cuentas Gmail
 * @capa              Servicio
 * @responsabilidades Guardar, listar y limpiar las suscripciones detectadas en los correos
 * @datos             Encabezados List-Unsubscribe de Gmail y cuenta de correo asociada
 * @dependencias      ISuscripcion
 * @usadoPor          CGmail
 */
@Service
public class SSuscripcionGmail {

    private final ISuscripcion suscripciones;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SSuscripcionGmail(ISuscripcion suscripciones) {
        this.suscripciones = suscripciones;
    }

    /**
     * Guarda o actualiza la suscripción detectada en los encabezados de un correo.
     * Solo se registran remitentes con List-Unsubscribe y con etiquetas de promociones o actualizaciones.
     */
    @Transactional
    public void guardar(MFuente cuenta, String from, String unsubscribe, String unsubscribePost,
                        List<String> etiquetas, Instant actividad) throws IOException {
        if (unsubscribe == null || unsubscribe.isBlank()) return;
        if (etiquetas.stream().noneMatch(etiqueta -> etiqueta.equals("CATEGORY_PROMOTIONS")
                || etiqueta.equals("CATEGORY_UPDATES"))) return;
        String correo = from.matches(".*<[^>]+>.*")
                ? from.replaceAll(".*<([^>]+)>.*", "$1").trim() : from.trim();
        if (correo.isBlank() || correo.length() > 254) return;
        String key = correo.toLowerCase(Locale.ROOT);
        String nombre = from.replaceAll("<[^>]+>", "").replaceAll("[\"']", "").trim();

        MSuscripcion suscripcion = this.suscripciones
                .findByCuenta_IdFuenteAndCorreoRemitente(cuenta.getIdFuente(), key)
                .orElseGet(() -> nueva(cuenta, key, actividad));

        suscripcion.setCuenta(cuenta);
        if (!nombre.isBlank()) suscripcion.setNombre(nombre);
        if (!unsubscribe.isBlank()) suscripcion.setListUnsubscribe(unsubscribe);
        if (unsubscribePost != null && !unsubscribePost.isBlank()) {
            suscripcion.setListUnsubscribePost(unsubscribePost);
        }

        List<String> categorias = new ArrayList<>(leerLista(suscripcion.getCategoriasJson()));
        for (String categoria : etiquetas) {
            if ((categoria.equals("CATEGORY_PROMOTIONS") || categoria.equals("CATEGORY_UPDATES"))
                    && !categorias.contains(categoria)) categorias.add(categoria);
        }
        suscripcion.setCategoriasJson(this.objectMapper.writeValueAsString(categorias));
        if (actividad.isAfter(suscripcion.getUltimaActividad())) suscripcion.setUltimaActividad(actividad);
        suscripcion.setFechaActualizacion(Instant.now());
        this.suscripciones.save(suscripcion);
    }

    /** Suscripciones detectadas en la cuenta indicada, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<MSuscripcion> listarPorCuenta(String idFuente) {
        return this.suscripciones.findByCuenta_IdFuenteOrderByUltimaActividadDesc(idFuente);
    }

    /** Indica si el remitente ya tiene una suscripción guardada en la cuenta. */
    @Transactional(readOnly = true)
    public boolean existeEnCuenta(String idFuente, String remitente) {
        if (remitente == null || remitente.isBlank()) return false;
        return this.suscripciones.findByCuenta_IdFuenteAndCorreoRemitente(
                idFuente, remitente.trim().toLowerCase(Locale.ROOT)).isPresent();
    }

    /** Elimina las suscripciones de Gmail de la cuenta indicada. */
    @Transactional
    public void eliminarPorCuenta(String idFuente) {
        this.suscripciones.deleteByCuenta_IdFuente(idFuente);
    }

    private MSuscripcion nueva(MFuente cuenta, String correo, Instant actividad) {
        Instant ahora = Instant.now();
        MSuscripcion suscripcion = new MSuscripcion();
        suscripcion.setId(UUID.randomUUID().toString());
        suscripcion.setUsuario(cuenta.getUsuario());
        suscripcion.setCuenta(cuenta);
        suscripcion.setOrigen(OrigenSuscripcion.GMAIL);
        suscripcion.setCorreoRemitente(correo);
        suscripcion.setActiva(true);
        suscripcion.setUltimaActividad(actividad);
        suscripcion.setFechaCreacion(ahora);
        suscripcion.setFechaActualizacion(ahora);
        return suscripcion;
    }

    private List<String> leerLista(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return this.objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            return List.of();
        }
    }
}
