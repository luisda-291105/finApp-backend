package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.ResumenMovimientoBolsilloDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.ITarjeta;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class SMovimientoContable {
    private static final Set<String> TIPOS_BASE = Set.of("INGRESO", "GASTO", "AHORRO");
    private final IMovimientoContable movimientos;
    private final IBolsillo bolsillos;
    private final IUsuario usuarios;
    private final ITarjeta tarjetas;

    public SMovimientoContable(IMovimientoContable movimientos, IBolsillo bolsillos,
                               IUsuario usuarios, ITarjeta tarjetas) {
        this.movimientos = movimientos;
        this.bolsillos = bolsillos;
        this.usuarios = usuarios;
        this.tarjetas = tarjetas;
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
                : movimientos.findByBolsillo_IdBolsilloInAndTipoIgnoreCaseOrderByFechaDesc(idsBolsillo, filtro);
        return encontrados.stream().map(SMovimientoContable::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<String> correosImportados(String idUsuario, List<String> origenesCorreo) {
        validarUsuario(idUsuario);
        if (origenesCorreo == null || origenesCorreo.isEmpty()) return List.of();
        return movimientos.findByOrigenTipoAndOrigenIdInAndBolsillo_IdUsuario("GMAIL", origenesCorreo, idUsuario)
                .stream().map(MMovimientoContable::getOrigenId).toList();
    }

    @Transactional(readOnly = true)
    public List<ResumenMovimientoBolsilloDTO> resumir(String idUsuario) {
        validarUsuario(idUsuario);
        List<MBolsillo> propios = bolsillosPropios(idUsuario);
        if (propios.isEmpty()) return List.of();
        Map<String, BigDecimal[]> totales = new LinkedHashMap<>();
        Map<String, Long> cantidades = new LinkedHashMap<>();
        propios.forEach(bolsillo -> {
            totales.put(bolsillo.getIdBolsillo(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            cantidades.put(bolsillo.getIdBolsillo(), 0L);
        });
        for (Object[] fila : movimientos.resumirPorBolsillos(List.copyOf(totales.keySet()))) {
            String idBolsillo = (String) fila[0];
            String tipo = String.valueOf(fila[1]).toUpperCase(Locale.ROOT);
            long cantidad = ((Number) fila[2]).longValue();
            BigDecimal monto = fila[3] == null ? BigDecimal.ZERO : new BigDecimal(fila[3].toString());
            cantidades.merge(idBolsillo, cantidad, Long::sum);
            BigDecimal[] bolsilloTotal = totales.get(idBolsillo);
            if ("INGRESO".equals(tipo)) bolsilloTotal[0] = bolsilloTotal[0].add(monto);
            else if ("GASTO".equals(tipo)) bolsilloTotal[1] = bolsilloTotal[1].add(monto);
            else if ("AHORRO".equals(tipo)) bolsilloTotal[2] = bolsilloTotal[2].add(monto);
            else bolsilloTotal[3] = bolsilloTotal[3].add(monto);
        }
        return propios.stream().map(bolsillo -> {
            BigDecimal[] montos = totales.get(bolsillo.getIdBolsillo());
            BigDecimal saldo = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), null);
            if (saldo == null) saldo = BigDecimal.ZERO;
            return new ResumenMovimientoBolsilloDTO(bolsillo.getIdBolsillo(), bolsillo.getNombre(),
                    cantidades.get(bolsillo.getIdBolsillo()), montos[0], montos[1], montos[2], montos[3], saldo);
        }).toList();
    }

    @Transactional
    public MovimientoContableDTO crear(String idUsuario, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        if ("GMAIL".equalsIgnoreCase(solicitud.origenTipo()) && movimientos.existsByOrigenTipoAndOrigenId("GMAIL", solicitud.origenId())) {
            throw new IllegalStateException("Este correo ya fue importado como movimiento.");
        }
        MBolsillo bolsillo = bolsilloPropio(idUsuario, solicitud.idBolsillo());
        if (!bolsillo.isEstado()) throw new IllegalArgumentException("Activa el bolsillo para registrar movimientos.");
        String tipo = tipoBolsillo(bolsillo);
        Origen origen = resolverOrigen(idUsuario, solicitud, tipo, null);
        validarSaldoDisponible(origen.bolsillo(), tipo, solicitud.monto(), null);
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(UUID.randomUUID().toString());
        movimiento.setBolsillo(bolsillo);
        movimiento.setTipo(tipo);
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto() == null ? "" : solicitud.concepto().trim());
        aplicarOrigen(movimiento, origen, tipo);
        return dto(movimientos.save(movimiento));
    }

    @Transactional
    public MovimientoContableDTO actualizar(String idUsuario, String idMovimiento, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        MMovimientoContable movimiento = propio(idUsuario, idMovimiento);
        MBolsillo bolsillo = bolsilloPropio(idUsuario, solicitud.idBolsillo());
        if (!bolsillo.isEstado() && !bolsillo.getIdBolsillo().equals(movimiento.getBolsillo().getIdBolsillo())) {
            throw new IllegalArgumentException("Activa el bolsillo para asignarle este movimiento.");
        }
        String tipo = tipoBolsillo(bolsillo);
        Origen origen = resolverOrigen(idUsuario, solicitud, tipo, movimiento.getIdMovimiento());
        String idMovimientoExcluir = origen.bolsillo() != null && origen.bolsillo().getIdBolsillo().equals(movimiento.getOrigenId())
                ? movimiento.getIdMovimiento() : null;
        validarSaldoDisponible(origen.bolsillo(), tipo, solicitud.monto(), idMovimientoExcluir);
        movimiento.setBolsillo(bolsillo);
        movimiento.setTipo(tipo);
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto() == null ? "" : solicitud.concepto().trim());
        aplicarOrigen(movimiento, origen, tipo);
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

    private MBolsillo bolsilloPropio(String idUsuario, String idBolsillo) {
        if (idBolsillo == null || idBolsillo.isBlank()) throw new IllegalArgumentException("Selecciona un bolsillo.");
        return bolsillos.findByIdBolsilloAndIdUsuario(idBolsillo, idUsuario)
                .orElseThrow(() -> new NoSuchElementException("No se encontró el bolsillo seleccionado."));
    }

    private void validarSaldoDisponible(MBolsillo bolsillo, String tipo, BigDecimal monto, String idMovimientoExcluir) {
        if ("INGRESO".equalsIgnoreCase(tipo) || bolsillo == null) return;
        BigDecimal disponible = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), idMovimientoExcluir);
        if (disponible == null) disponible = BigDecimal.ZERO;
        if (monto.compareTo(disponible) > 0) {
            throw new IllegalArgumentException("Saldo insuficiente en «" + bolsillo.getNombre()
                    + "». Disponible: " + disponible.toPlainString() + " COP.");
        }
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
        if (solicitud.origenId() != null && solicitud.origenId().length() > 180) {
            throw new IllegalArgumentException("El identificador del origen supera el máximo permitido.");
        }
        if (solicitud.fecha() == null) {
            throw new IllegalArgumentException("Ingresa una fecha válida.");
        }
        BigDecimal monto = solicitud.monto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.scale() > 2 || monto.precision() > 19) {
            throw new IllegalArgumentException("El monto debe ser positivo y tener máximo dos decimales.");
        }
        if (solicitud.idBolsillo() == null || solicitud.idBolsillo().isBlank()) {
            throw new IllegalArgumentException("Selecciona el bolsillo del movimiento.");
        }
        if (solicitud.categoria() == null || solicitud.categoria().isBlank() || solicitud.categoria().trim().length() > 80) {
            throw new IllegalArgumentException("La categoría es obligatoria y no puede superar 80 caracteres.");
        }
        if (solicitud.concepto() != null && solicitud.concepto().trim().length() > 160) {
            throw new IllegalArgumentException("El concepto no puede superar 160 caracteres.");
        }
    }

    private static String normalizarTipo(String tipo) {
        String limpio = tipo.trim().replaceAll("\\s+", " ");
        String mayusculas = limpio.toUpperCase(Locale.ROOT);
        return TIPOS_BASE.contains(mayusculas) ? mayusculas : limpio;
    }

    private String tipoBolsillo(MBolsillo bolsillo) {
        String tipo = bolsillo.getTipo();
        if (tipo == null || tipo.isBlank()) throw new IllegalArgumentException("El bolsillo no tiene un tipo de movimiento configurado.");
        return normalizarTipo(tipo);
    }

    private Origen resolverOrigen(String idUsuario, MovimientoContableSolicitudDTO solicitud, String tipo, String idMovimiento) {
        String origenTipo = solicitud.origenTipo() == null ? "" : solicitud.origenTipo().trim().toUpperCase(Locale.ROOT);
        if ("INGRESO".equalsIgnoreCase(tipo) && !"GMAIL".equals(origenTipo)) return new Origen(null, "", null, "");
        return switch (origenTipo) {
            case "EFECTIVO" -> new Origen(null, "EFECTIVO", null, "Efectivo");
            case "GMAIL" -> {
                if (solicitud.origenId() == null || solicitud.origenId().isBlank()
                        || solicitud.origenNombre() == null || solicitud.origenNombre().isBlank()) {
                    throw new IllegalArgumentException("La referencia Gmail debe incluir identificador y remitente.");
                }
                yield new Origen(null, "GMAIL", solicitud.origenId(), solicitud.origenNombre().trim());
            }
            case "BOLSILLO" -> {
                MBolsillo origen = bolsilloPropio(idUsuario, solicitud.origenId());
                if (!origen.isEstado() && (idMovimiento == null || !origen.getIdBolsillo().equals(solicitud.origenId()))) {
                    throw new IllegalArgumentException("Activa el bolsillo de origen para usarlo.");
                }
                String tipoOrigen = tipoBolsillo(origen);
                if (!"INGRESO".equalsIgnoreCase(tipoOrigen) && !"AHORRO".equalsIgnoreCase(tipoOrigen)) {
                    throw new IllegalArgumentException("El bolsillo de origen debe ser de tipo Ingreso o Ahorro.");
                }
                BigDecimal saldoOrigen = movimientos.calcularSaldoDisponible(origen.getIdBolsillo(), idMovimiento);
                if ("AHORRO".equalsIgnoreCase(tipoOrigen) && (saldoOrigen == null || saldoOrigen.compareTo(BigDecimal.ZERO) <= 0)) {
                    throw new IllegalArgumentException("El bolsillo de Ahorro debe tener saldo disponible para usarlo como origen.");
                }
                yield new Origen(origen, "BOLSILLO", origen.getIdBolsillo(), origen.getNombre());
            }
            case "CUENTA" -> {
                MTarjeta cuenta = tarjetas.findByIdTarjetaAndUsuario_IdUsuario(solicitud.origenId(), idUsuario)
                        .orElseThrow(() -> new NoSuchElementException("No se encontró la cuenta seleccionada."));
                yield new Origen(null, "CUENTA", cuenta.getIdTarjeta(), cuenta.getEntidad() + " · " + cuenta.getTitular());
            }
            default -> throw new IllegalArgumentException("Selecciona de dónde salió el dinero.");
        };
    }

    private void aplicarOrigen(MMovimientoContable movimiento, Origen origen, String tipo) {
        if ("INGRESO".equalsIgnoreCase(tipo) && !"GMAIL".equals(origen.tipo())) {
            movimiento.setOrigenTipo(null);
            movimiento.setOrigenId(null);
            movimiento.setOrigenNombre(null);
            return;
        }
        movimiento.setOrigenTipo(origen.tipo());
        movimiento.setOrigenId(origen.id());
        movimiento.setOrigenNombre(origen.nombre());
    }

    private record Origen(MBolsillo bolsillo, String tipo, String id, String nombre) {}

    private static MovimientoContableDTO dto(MMovimientoContable movimiento) {
        return new MovimientoContableDTO(movimiento.getIdMovimiento(), movimiento.getBolsillo().getIdBolsillo(), movimiento.getBolsillo().getNombre(),
                movimiento.getFecha(), movimiento.getMonto(), movimiento.getTipo(), movimiento.getCategoria(), movimiento.getConcepto(),
                movimiento.getOrigenTipo(), movimiento.getOrigenId(), movimiento.getOrigenNombre());
    }
}
