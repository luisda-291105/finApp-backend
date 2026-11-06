package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** CRUD de bolsillos privados del usuario autenticado. */
@Service
public class SBolsillo {
    private final IBolsillo bolsillos;
    private final IContabilidad contabilidades;
    private final IMovimientoContable movimientos;
    private final IUsuario usuarios;

    public SBolsillo(IBolsillo bolsillos, IContabilidad contabilidades, IMovimientoContable movimientos, IUsuario usuarios) {
        this.bolsillos = bolsillos;
        this.contabilidades = contabilidades;
        this.movimientos = movimientos;
        this.usuarios = usuarios;
    }

    public List<MBolsillo> listar(String idUsuario) {
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        return bolsillos.findByContabilidad_IdContabilidadOrderByFechaCreacionDesc(contabilidad.getIdContabilidad());
    }

    public MBolsillo crear(String idUsuario, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(UUID.randomUUID().toString());
        bolsillo.setContabilidad(contabilidad);
        bolsillo.setNombre(solicitud.nombre().trim());
        bolsillo.setMontoObjetivo(solicitud.montoObjetivo());
        bolsillo.setEstado(true);
        bolsillo.setFechaCreacion(LocalDate.now());
        return bolsillos.save(bolsillo);
    }

    public MBolsillo actualizar(String idUsuario, String idBolsillo, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MBolsillo bolsillo = propio(idUsuario, idBolsillo);
        bolsillo.setNombre(solicitud.nombre().trim());
        if (solicitud.estado() != null) bolsillo.setEstado(solicitud.estado());
        if (solicitud.montoObjetivo() != null) bolsillo.setMontoObjetivo(solicitud.montoObjetivo());
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
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        return bolsillos.findByIdBolsilloAndContabilidad_IdContabilidad(idBolsillo, contabilidad.getIdContabilidad())
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo."));
    }

    private MContabilidad obtenerContabilidad(String idUsuario) {
        validarUsuario(idUsuario);
        return contabilidades.findByUsuario_IdUsuario(idUsuario).orElseGet(() -> {
            MContabilidad nueva = new MContabilidad();
            nueva.setIdContabilidad(UUID.randomUUID().toString());
            nueva.setUsuario(usuarios.findById(idUsuario).orElseThrow(() -> new NoSuchElementException("No se encontró el usuario activo.")));
            nueva.setNombre("Contabilidad personal");
            nueva.setValor(0);
            nueva.setEstado(true);
            nueva.setFechaCreacion(LocalDate.now());
            return contabilidades.save(nueva);
        });
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
        BigDecimal objetivo = solicitud.montoObjetivo();
        if (objetivo != null && (objetivo.compareTo(BigDecimal.ZERO) < 0 || objetivo.scale() > 2 || objetivo.precision() > 19)) {
            throw new IllegalArgumentException("El monto objetivo no puede ser negativo ni superar dos decimales.");
        }
    }
}
