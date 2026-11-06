package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** CRUD de bolsillos privados del usuario autenticado. */
@Service
public class SBolsillo {
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
        return bolsillos.findByIdContabilidadOrderByFechaCreacionDesc(contabilidad.getIdContabilidad());
    }

    public MBolsillo crear(String idUsuario, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        MBolsillo bolsillo = new MBolsillo();
        bolsillo.setIdBolsillo(UUID.randomUUID().toString());
        bolsillo.setIdContabilidad(contabilidad.getIdContabilidad());
        bolsillo.setNombre(solicitud.nombre().trim());
        bolsillo.setEstado(true);
        bolsillo.setFechaCreacion(LocalDate.now());
        return bolsillos.save(bolsillo);
    }

    public MBolsillo actualizar(String idUsuario, String idBolsillo, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        MBolsillo bolsillo = propio(idUsuario, idBolsillo);
        bolsillo.setNombre(solicitud.nombre().trim());
        if (solicitud.estado() != null) bolsillo.setEstado(solicitud.estado());
        return bolsillos.save(bolsillo);
    }

    public void eliminar(String idUsuario, String idBolsillo) {
        bolsillos.delete(propio(idUsuario, idBolsillo));
    }

    private MBolsillo propio(String idUsuario, String idBolsillo) {
        MContabilidad contabilidad = obtenerContabilidad(idUsuario);
        return bolsillos.findByIdBolsilloAndIdContabilidad(idBolsillo, contabilidad.getIdContabilidad())
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo."));
    }

    private MContabilidad obtenerContabilidad(String idUsuario) {
        validarUsuario(idUsuario);
        return contabilidades.findByIdUsuario(idUsuario).orElseGet(() -> {
            MContabilidad nueva = new MContabilidad();
            nueva.setIdContabilidad(UUID.randomUUID().toString());
            nueva.setIdUsuario(idUsuario);
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
    }
}
