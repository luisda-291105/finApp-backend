package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
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

@Service
public class SMovimientoContable {
    private static final Set<String> TIPOS = Set.of("INGRESO", "GASTO", "AHORRO");
    private final IMovimientoContable movimientos;
    private final IBolsillo bolsillos;
    private final IContabilidad contabilidades;
    private final IUsuario usuarios;

    public SMovimientoContable(IMovimientoContable movimientos, IBolsillo bolsillos,
                               IContabilidad contabilidades, IUsuario usuarios) {
        this.movimientos = movimientos;
        this.bolsillos = bolsillos;
        this.contabilidades = contabilidades;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<MovimientoContableDTO> listar(String idUsuario, String idBolsillo, String tipo) {
        validarUsuario(idUsuario);
        var bolsillo = bolsilloPropio(idUsuario, idBolsillo);
        String filtro = tipo == null ? "TODOS" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!filtro.equals("TODOS") && !TIPOS.contains(filtro)) {
            throw new IllegalArgumentException("El filtro debe ser Ingreso, Gasto, Ahorro o Todos.");
        }
        List<MMovimientoContable> encontrados = filtro.equals("TODOS")
                ? movimientos.findByBolsillo_IdBolsilloOrderByFechaDesc(bolsillo.getIdBolsillo())
                : movimientos.findByBolsillo_IdBolsilloAndTipoIgnoreCaseOrderByFechaDesc(bolsillo.getIdBolsillo(), filtro);
        return encontrados.stream().map(SMovimientoContable::dto).toList();
    }

    @Transactional
    public MovimientoContableDTO crear(String idUsuario, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        var bolsillo = bolsilloPropio(idUsuario, solicitud.idBolsillo());
        if (!bolsillo.isEstado()) throw new IllegalArgumentException("Activa el bolsillo para registrar movimientos.");
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(UUID.randomUUID().toString());
        movimiento.setBolsillo(bolsillo);
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setTipo(solicitud.tipo().trim().toUpperCase(Locale.ROOT));
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto().trim());
        return dto(movimientos.save(movimiento));
    }

    private com.luisda.fin_app_back.Model.MBolsillo bolsilloPropio(String idUsuario, String idBolsillo) {
        if (idBolsillo == null || idBolsillo.isBlank()) throw new IllegalArgumentException("Selecciona un bolsillo.");
        String idContabilidad = contabilidades.findByIdUsuario(idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró la contabilidad del usuario."))
                .getIdContabilidad();
        return bolsillos.findByIdBolsilloAndIdContabilidad(idBolsillo, idContabilidad)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo seleccionado."));
    }

    private void validarUsuario(String idUsuario) {
        if (idUsuario == null || !usuarios.existsById(idUsuario)) {
            throw new NoSuchElementException("No se encontró el usuario activo.");
        }
    }

    private void validar(MovimientoContableSolicitudDTO solicitud) {
        if (solicitud == null || solicitud.idBolsillo() == null || solicitud.idBolsillo().isBlank()) {
            throw new IllegalArgumentException("Selecciona un bolsillo.");
        }
        if (solicitud.fecha() == null) {
            throw new IllegalArgumentException("Ingresa una fecha válida.");
        }
        BigDecimal monto = solicitud.monto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.scale() > 2 || monto.precision() > 19) {
            throw new IllegalArgumentException("El monto debe ser positivo y tener máximo dos decimales.");
        }
        if (solicitud.tipo() == null || !TIPOS.contains(solicitud.tipo().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("El tipo debe ser Ingreso, Gasto o Ahorro.");
        }
        if (solicitud.categoria() == null || solicitud.categoria().isBlank() || solicitud.categoria().trim().length() > 80) {
            throw new IllegalArgumentException("La categoría es obligatoria y no puede superar 80 caracteres.");
        }
        if (solicitud.concepto() == null || solicitud.concepto().isBlank() || solicitud.concepto().trim().length() > 160) {
            throw new IllegalArgumentException("El concepto es obligatorio y no puede superar 160 caracteres.");
        }
    }

    private static MovimientoContableDTO dto(MMovimientoContable movimiento) {
        return new MovimientoContableDTO(movimiento.getIdMovimiento(), movimiento.getBolsillo().getIdBolsillo(),
                movimiento.getFecha(), movimiento.getMonto(), movimiento.getTipo(), movimiento.getCategoria(), movimiento.getConcepto());
    }
}
