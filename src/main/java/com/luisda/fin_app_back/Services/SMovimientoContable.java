package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.ResumenMovimientoBolsilloDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SMovimientoContable {
    private final IMovimientoContable movimientos;
    private final IBolsillo bolsillos;
    private final IUsuario usuarios;

    public SMovimientoContable(IMovimientoContable movimientos, IBolsillo bolsillos,
                               IUsuario usuarios) {
        this.movimientos = movimientos;
        this.bolsillos = bolsillos;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<MovimientoContableDTO> listar(String idUsuario, String idBolsillo, String tipo) {
        validarUsuario(idUsuario);
        String filtro = tipo == null ? "TODOS" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!filtro.equals("TODOS") && (filtro.isBlank() || filtro.length() > 40)) {
            throw new IllegalArgumentException("El filtro debe ser Todos o un tipo de movimiento válido.");
        }
        List<String> idsBolsillo = idBolsillo == null || idBolsillo.isBlank()
                ? bolsillosPropios(idUsuario).stream().map(MBolsillo::getIdBolsillo).toList()
                : List.of(bolsilloPropio(idUsuario, idBolsillo).getIdBolsillo());
        if (idsBolsillo.isEmpty()) return List.of();
        List<MMovimientoContable> encontrados = filtro.equals("TODOS")
                ? movimientos.findByBolsillo_IdBolsilloInOrderByFechaDesc(idsBolsillo)
                : movimientos.findByBolsillo_IdBolsilloInAndBolsillo_TipoIgnoreCaseOrderByFechaDesc(idsBolsillo, filtro);
        return encontrados.stream().map(SMovimientoContable::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<ResumenMovimientoBolsilloDTO> resumir(String idUsuario) {
        validarUsuario(idUsuario);
        List<MBolsillo> propios = bolsillosPropios(idUsuario);
        if (propios.isEmpty()) return List.of();
        Map<String, BigDecimal[]> totales = new LinkedHashMap<>();
        Map<String, Long> cantidades = new LinkedHashMap<>();
        propios.forEach(bolsillo -> {
            totales.put(bolsillo.getIdBolsillo(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            cantidades.put(bolsillo.getIdBolsillo(), 0L);
        });
        for (Object[] fila : movimientos.resumirPorBolsillos(List.copyOf(totales.keySet()))) {
            String idBolsillo = (String) fila[0];
            String tipo = String.valueOf(fila[1]).toUpperCase(Locale.ROOT);
            long cantidad = ((Number) fila[2]).longValue();
            BigDecimal monto = fila[3] == null ? BigDecimal.ZERO : new BigDecimal(fila[3].toString());
            cantidades.put(idBolsillo, cantidad);
            BigDecimal[] bolsilloTotal = totales.get(idBolsillo);
            if ("INGRESO".equals(tipo)) bolsilloTotal[0] = monto;
            if ("GASTO".equals(tipo)) bolsilloTotal[1] = monto;
            if ("AHORRO".equals(tipo)) bolsilloTotal[2] = monto;
        }
        return propios.stream().map(bolsillo -> {
            BigDecimal[] montos = totales.get(bolsillo.getIdBolsillo());
            BigDecimal saldo = montos[0].subtract(montos[1]).subtract(montos[2]);
            return new ResumenMovimientoBolsilloDTO(bolsillo.getIdBolsillo(), bolsillo.getNombre(),
                    cantidades.get(bolsillo.getIdBolsillo()), montos[0], montos[1], montos[2], saldo);
        }).toList();
    }

    @Transactional
    public MovimientoContableDTO crear(String idUsuario, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        MBolsillo bolsillo = bolsilloPorTipoPropio(idUsuario, solicitud.tipo());
        if (!bolsillo.isEstado()) throw new IllegalArgumentException("Activa el bolsillo para registrar movimientos.");
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(UUID.randomUUID().toString());
        movimiento.setBolsillo(bolsillo);
        movimiento.setTipo(bolsillo.getTipo());
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto().trim());
        return dto(movimientos.save(movimiento));
    }

    @Transactional
    public MovimientoContableDTO actualizar(String idUsuario, String idMovimiento, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        MMovimientoContable movimiento = propio(idUsuario, idMovimiento);
        MBolsillo bolsillo = bolsilloPorTipoPropio(idUsuario, solicitud.tipo());
        if (!bolsillo.isEstado() && !bolsillo.getIdBolsillo().equals(movimiento.getBolsillo().getIdBolsillo())) {
            throw new IllegalArgumentException("Activa el bolsillo para asignarle este movimiento.");
        }
        movimiento.setBolsillo(bolsillo);
        movimiento.setTipo(bolsillo.getTipo());
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto().trim());
        return dto(movimientos.save(movimiento));
    }

    @Transactional
    public void eliminar(String idUsuario, String idMovimiento) {
        validarUsuario(idUsuario);
        movimientos.delete(propio(idUsuario, idMovimiento));
    }

    private MMovimientoContable propio(String idUsuario, String idMovimiento) {
        return movimientos.findByIdMovimientoAndBolsillo_IdUsuario(idMovimiento, idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el movimiento."));
    }

    private MBolsillo bolsilloPorTipoPropio(String idUsuario, String tipo) {
        String tipoNormalizado = tipo.trim().toUpperCase(Locale.ROOT);
        List<MBolsillo> encontrados = bolsillosPropios(idUsuario).stream()
                .filter(bolsillo -> tipoNormalizado.equalsIgnoreCase(bolsillo.getTipo()))
                .toList();
        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("No tienes un bolsillo activo para el tipo " + tipoNormalizado.toLowerCase(Locale.ROOT) + ". Crea uno de ese tipo.");
        }
        if (encontrados.size() > 1) {
            throw new IllegalStateException("Hay varios bolsillos del mismo tipo. Deja solo uno para registrar movimientos automáticamente.");
        }
        return encontrados.get(0);
    }

    private MBolsillo bolsilloPropio(String idUsuario, String idBolsillo) {
        if (idBolsillo == null || idBolsillo.isBlank()) throw new IllegalArgumentException("Selecciona un bolsillo.");
        return bolsillos.findByIdBolsilloAndIdUsuario(idBolsillo, idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo seleccionado."));
    }

    private List<MBolsillo> bolsillosPropios(String idUsuario) {
        return bolsillos.findByIdUsuarioOrderByFechaCreacionDesc(idUsuario);
    }

    private void validarUsuario(String idUsuario) {
        if (idUsuario == null || !usuarios.existsById(idUsuario)) {
            throw new NoSuchElementException("No se encontró el usuario activo.");
        }
    }

    private void validar(MovimientoContableSolicitudDTO solicitud) {
        if (solicitud == null) throw new IllegalArgumentException("Completa los datos del movimiento.");
        if (solicitud.fecha() == null) {
            throw new IllegalArgumentException("Ingresa una fecha válida.");
        }
        BigDecimal monto = solicitud.monto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.scale() > 2 || monto.precision() > 19) {
            throw new IllegalArgumentException("El monto debe ser positivo y tener máximo dos decimales.");
        }
        if (solicitud.tipo() == null || solicitud.tipo().isBlank() || solicitud.tipo().trim().length() > 40) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio y no puede superar 40 caracteres.");
        }
        if (solicitud.categoria() == null || solicitud.categoria().isBlank() || solicitud.categoria().trim().length() > 80) {
            throw new IllegalArgumentException("La categoría es obligatoria y no puede superar 80 caracteres.");
        }
        if (solicitud.concepto() == null || solicitud.concepto().isBlank() || solicitud.concepto().trim().length() > 160) {
            throw new IllegalArgumentException("El concepto es obligatorio y no puede superar 160 caracteres.");
        }
    }

    private static MovimientoContableDTO dto(MMovimientoContable movimiento) {
        return new MovimientoContableDTO(movimiento.getIdMovimiento(), movimiento.getBolsillo().getIdBolsillo(), movimiento.getBolsillo().getNombre(),
                movimiento.getFecha(), movimiento.getMonto(), movimiento.getBolsillo().getTipo(), movimiento.getCategoria(), movimiento.getConcepto());
    }
}
