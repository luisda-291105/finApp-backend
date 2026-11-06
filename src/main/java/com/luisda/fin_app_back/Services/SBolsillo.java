package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/** CRUD de bolsillos privados del usuario autenticado. */
@Service
public class SBolsillo {
    private static final Set<String> TIPOS_BASE = Set.of("INGRESO", "GASTO", "AHORRO");
    private final IBolsillo bolsillos;
    private final IContabilidad contabilidades;
    private final IUsuario usuarios;

    public SBolsillo(IBolsillo bolsillos, IContabilidad contabilidades, IUsuario usuarios) {
        this.bolsillos = bolsillos;
        this.contabilidades = contabilidades;
        this.usuarios = usuarios;
    }

    public List<MBolsillo> listar(String idUsuario) {
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        List<MBolsillo> lista = bolsillos.findByContabilidad_IdContabilidadOrderByFechaCreacionDesc(contabilidad.getIdContabilidad());
        boolean actualizados = false;
        for (MBolsillo bolsillo : lista) {
            // En instalaciones antiguas esta columna quedó en NULL antes de
            // declararse obligatoria; normalízala antes de persistir reparaciones.
            if (bolsillo.getMontoObjetivo() == null) {
                bolsillo.setMontoObjetivo(BigDecimal.ZERO);
                actualizados = true;
            }
            if (bolsillo.getIdUsuario() == null || bolsillo.getIdUsuario().isBlank()) {
                bolsillo.setIdUsuario(idUsuario);
                actualizados = true;
            }
            if (bolsillo.getTipo() == null || bolsillo.getTipo().isBlank()) {
                bolsillo.setTipo(inferirTipo(bolsillo.getNombre()));
                actualizados = true;
            }
        }
        return actualizados ? bolsillos.saveAll(lista) : lista;
    }

    public MBolsillo crear(String idUsuario, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(UUID.randomUUID().toString());
        bolsillo.setContabilidad(contabilidad);
        bolsillo.setIdUsuario(idUsuario);
        bolsillo.setNombre(solicitud.nombre().trim());
        bolsillo.setTipo(solicitud.tipo());
        bolsillo.setMontoObjetivo(solicitud.montoObjetivo());
        bolsillo.setEstado(true);
        bolsillo.setFechaCreacion(LocalDate.now());
        return bolsillos.save(bolsillo);
    }

    @Transactional
    public MBolsillo actualizar(String idUsuario, String idBolsillo, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MBolsillo bolsillo = propio(idUsuario, idBolsillo);
        String tipoNuevo = solicitud.tipo() == null ? bolsillo.getTipo() : normalizarTipo(solicitud.tipo());
        if (!tipoNuevo.equalsIgnoreCase(bolsillo.getTipo())) {
            bolsillo.setTipo(tipoNuevo);
        }
        bolsillo.setNombre(solicitud.nombre().trim());
        if (solicitud.estado() != null) bolsillo.setEstado(solicitud.estado());
        bolsillo.setMontoObjetivo(solicitud.montoObjetivo());
        return bolsillos.save(bolsillo);
    }

    public void eliminar(String idUsuario, String idBolsillo) {
        MBolsillo bolsillo = propio(idUsuario, idBolsillo);
        if (movimientos.existsByBolsillo_IdBolsillo(bolsillo.getIdBolsillo())) {
            throw new IllegalStateException("No puedes eliminar un bolsillo que ya tiene movimientos.");
        }
        bolsillos.delete(bolsillo);
    }

    private MBolsillo propio(String idUsuario, String idBolsillo) {
        validarUsuario(idUsuario);
        return bolsillos.findByIdBolsilloAndIdUsuario(idBolsillo, idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo."));
    }

    private MContabilidad obtenerContabilidad(String idUsuario) {
        validarUsuario(idUsuario);
        MContabilidad contabilidad = contabilidades.findByUsuario_IdUsuario(idUsuario).orElseGet(() -> {
            MContabilidad nueva = new MContabilidad();
            nueva.setIdContabilidad(UUID.randomUUID().toString());
            nueva.setUsuario(usuarios.findById(idUsuario).orElseThrow(() -> new NoSuchElementException("No se encontró el usuario activo.")));
            nueva.setNombre("Contabilidad personal");
            nueva.setValor(0);
            nueva.setEstado(true);
            nueva.setFechaCreacion(LocalDate.now());
            return contabilidades.save(nueva);
        });
        // Repara contabilidades anteriores que quedaron sin dueño asociado.
        if (contabilidad.getUsuario() == null) {
            contabilidad.setUsuario(usuarios.findById(idUsuario)
                    .orElseThrow(() -> new NoSuchElementException("No se encontró el usuario activo.")));
            contabilidad = contabilidades.save(contabilidad);
        }
        return contabilidad;
    }

    private void validarUsuario(String idUsuario) {
        if (idUsuario == null || !usuarios.existsById(idUsuario)) {
            throw new NoSuchElementException("No se encontró el usuario activo.");
        }
    }

    private void validar(BolsilloSolicitudDTO solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del bolsillo es obligatorio.");
        }
        if (solicitud.nombre().trim().length() > 100) {
            throw new IllegalArgumentException("El nombre del bolsillo no puede superar 100 caracteres.");
        }
        if (solicitud.tipo() == null || solicitud.tipo().isBlank() || solicitud.tipo().trim().length() > 40) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio y no puede superar 40 caracteres.");
        }
        BigDecimal objetivo = solicitud.montoObjetivo();
        if (objetivo != null && (objetivo.compareTo(BigDecimal.ZERO) < 0 || objetivo.scale() > 2 || objetivo.precision() > 19)) {
            throw new IllegalArgumentException("El monto objetivo no puede ser negativo ni superar dos decimales.");
        }
    }

    private static String normalizarTipo(String tipo) {
        if (tipo == null) return "";
        String limpio = tipo.trim().replaceAll("\\s+", " ");
        String mayusculas = limpio.toUpperCase(Locale.ROOT);
        return TIPOS_BASE.contains(mayusculas) ? mayusculas : limpio;
    }

    private static String inferirTipo(String nombre) {
        String limpio = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        if (limpio.contains("ingres")) return "INGRESO";
        if (limpio.contains("ahorr")) return "AHORRO";
        return "GASTO";
    }

}
