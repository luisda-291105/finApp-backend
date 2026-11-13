package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MovimientoContableDTO;
import com.luisda.fin_app_back.DTO.MovimientoContableSolicitudDTO;
import com.luisda.fin_app_back.DTO.ResumenMovimientoBolsilloDTO;
import com.luisda.fin_app_back.DTO.ResumenGlobalContabilidadDTO;
import com.luisda.fin_app_back.DTO.SuscripcionRecurrenteDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.MTarjeta;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
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
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class SMovimientoContable {
    private static final Set<String> TIPOS_BASE = Set.of("INGRESO", "GASTO", "AHORRO", "TRANSFERENCIA");
    private final IMovimientoContable movimientos;
    private final IBolsillo bolsillos;
    private final IUsuario usuarios;
    private final ITarjeta tarjetas;
    private final SBolsillo servicioBolsillo;
    private final SReglaFiltro reglasFiltro;

    public SMovimientoContable(IMovimientoContable movimientos, IBolsillo bolsillos,
                               IUsuario usuarios, ITarjeta tarjetas,
                               SBolsillo servicioBolsillo, SReglaFiltro reglasFiltro) {
        this.movimientos = movimientos;
        this.bolsillos = bolsillos;
        this.usuarios = usuarios;
        this.tarjetas = tarjetas;
        this.servicioBolsillo = servicioBolsillo;
        this.reglasFiltro = reglasFiltro;
    }

    @Transactional(readOnly = true)
    public List<MovimientoContableDTO> listar(String idUsuario, String idBolsillo, String tipo) {
        return listar(idUsuario, idBolsillo, tipo, null);
    }

    @Transactional(readOnly = true)
    public List<MovimientoContableDTO> listar(String idUsuario, String idBolsillo, String tipo,
                                              EstadoRevisionMovimiento estadoRevision) {
        validarUsuario(idUsuario);
        String filtro = tipo == null ? "TODOS" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!filtro.equals("TODOS") && (filtro.isBlank() || filtro.length() > 40)) {
            throw new IllegalArgumentException("El filtro debe ser Todos o un tipo de movimiento válido.");
        }
        List<String> idsBolsillo = idBolsillo == null || idBolsillo.isBlank()
                ? bolsillosPropios(idUsuario).stream().map(MBolsillo::getIdBolsillo).toList()
                : List.of(bolsilloPropio(idUsuario, idBolsillo).getIdBolsillo());
        List<String> idsConsulta = idsBolsillo.isEmpty() ? List.of("") : idsBolsillo;
        return movimientos.listarDelUsuarioConGlobales(idsConsulta, idUsuario, estadoRevision).stream()
                .filter(movimiento -> filtro.equals("TODOS") || filtro.equalsIgnoreCase(movimiento.getTipo()))
                .filter(movimiento -> idBolsillo == null || idBolsillo.isBlank()
                        || (movimiento.getBolsillo() != null
                        && idBolsillo.equals(movimiento.getBolsillo().getIdBolsillo())))
                .map(SMovimientoContable::dto).toList();
    }

    /** Detecta cobros repetidos y periódicos entre los gastos reales confirmados del usuario. */
    @Transactional(readOnly = true)
    public List<SuscripcionRecurrenteDTO> detectarSuscripcionesRecurrentes(String idUsuario) {
        validarUsuario(idUsuario);
        Map<String, List<MovimientoContableDTO>> grupos = listar(idUsuario, null, "GASTO").stream()
                .filter(m -> m.fecha() != null && m.monto() != null && m.monto().signum() > 0)
                .collect(Collectors.groupingBy(m -> claveSuscripcion(
                        m.origenNombre() == null || m.origenNombre().isBlank() ? m.concepto() : m.origenNombre())));
        return grupos.entrySet().stream().map(entrada -> crearSuscripcionDetectada(entrada.getKey(), entrada.getValue()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(SuscripcionRecurrenteDTO::proximoCobro))
                .toList();
    }

    private SuscripcionRecurrenteDTO crearSuscripcionDetectada(String clave, List<MovimientoContableDTO> movimientosRepetidos) {
        if (clave.isBlank() || movimientosRepetidos.size() < 2) return null;
        List<MovimientoContableDTO> ordenados = movimientosRepetidos.stream()
                .sorted(Comparator.comparing(MovimientoContableDTO::fecha)).toList();
        List<Long> intervalos = new java.util.ArrayList<>();
        for (int i = 1; i < ordenados.size(); i++) intervalos.add(ChronoUnit.DAYS.between(ordenados.get(i - 1).fecha(), ordenados.get(i).fecha()));
        double media = intervalos.stream().mapToLong(Long::longValue).average().orElse(0);
        int[] periodos = {7, 14, 30, 60, 90, 180, 365};
        int periodo = java.util.Arrays.stream(periodos).boxed().min(Comparator.comparingDouble(p -> Math.abs(p - media))).orElse(0);
        long tolerancia = Math.max(4, Math.round(periodo * 0.18));
        boolean periodica = intervalos.stream().allMatch(dias -> Math.abs(dias - periodo) <= tolerancia);
        if (!periodica) return null;

        java.math.BigDecimal promedio = ordenados.stream().map(MovimientoContableDTO::monto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .divide(java.math.BigDecimal.valueOf(ordenados.size()), 2, java.math.RoundingMode.HALF_UP);
        MovimientoContableDTO ultimo = ordenados.get(ordenados.size() - 1);
        String frecuencia = switch (periodo) {
            case 7 -> "Semanal";
            case 14 -> "Quincenal";
            case 30 -> "Mensual";
            case 60 -> "Cada 2 meses";
            case 90 -> "Trimestral";
            case 180 -> "Semestral";
            default -> "Anual";
        };
        String nombre = ultimo.origenNombre() == null || ultimo.origenNombre().isBlank() ? ultimo.concepto() : ultimo.origenNombre();
        return new SuscripcionRecurrenteDTO(clave, nombre, ultimo.categoria(), promedio, frecuencia, periodo,
                ultimo.fecha(), ultimo.fecha().plusDays(periodo), ordenados.size());
    }

    private String claveSuscripcion(String nombre) {
        if (nombre == null) return "";
        return Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    @Transactional(readOnly = true)
    public List<String> correosImportados(String idUsuario, List<String> origenesCorreo) {
        validarUsuario(idUsuario);
        if (origenesCorreo == null || origenesCorreo.isEmpty()) return List.of();
        return movimientos.listarOrigenesImportadosDelUsuario("GMAIL", origenesCorreo, idUsuario);
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

    @Transactional(readOnly = true)
    public ResumenGlobalContabilidadDTO resumenGlobal(String idUsuario, YearMonth mes) {
        validarUsuario(idUsuario);
        if (mes == null) throw new IllegalArgumentException("Indica un mes válido para consultar el resumen.");

        List<MBolsillo> propios = bolsillosPropios(idUsuario);
        List<String> idsBolsillo = propios.stream().map(MBolsillo::getIdBolsillo).toList();
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        BigDecimal ahorros = BigDecimal.ZERO;
        BigDecimal transferencias = BigDecimal.ZERO;
        BigDecimal saldoAhorros = BigDecimal.ZERO;

        {
            List<String> idsConsulta = idsBolsillo.isEmpty() ? List.of("") : idsBolsillo;
            for (Object[] fila : movimientos.resumirPorMesIncluyendoGlobales(idsConsulta, idUsuario, true)) {
                int anio = ((Number) fila[0]).intValue();
                int numeroMes = ((Number) fila[1]).intValue();
                if (anio != mes.getYear() || numeroMes != mes.getMonthValue()) continue;
                String tipo = String.valueOf(fila[2]).toUpperCase(Locale.ROOT);
                BigDecimal monto = fila[4] == null ? BigDecimal.ZERO : new BigDecimal(fila[4].toString());
                switch (tipo) {
                    case "INGRESO" -> ingresos = ingresos.add(monto);
                    case "GASTO" -> gastos = gastos.add(monto);
                    case "AHORRO" -> ahorros = ahorros.add(monto);
                    case "TRANSFERENCIA" -> transferencias = transferencias.add(monto);
                    default -> { }
                }
            }
            for (MBolsillo bolsillo : propios) {
                if (bolsillo.getTipo() != null && "AHORRO".equalsIgnoreCase(normalizarTipo(bolsillo.getTipo()))
                        && !"Por revisar".equalsIgnoreCase(bolsillo.getNombre())) {
                    BigDecimal saldo = movimientos.calcularSaldoDisponible(bolsillo.getIdBolsillo(), null);
                    if (saldo != null) saldoAhorros = saldoAhorros.add(saldo);
                }
            }
        }

        long porRevisar = movimientos.contarPorRevisarIncluyendoGlobales(
                idsBolsillo.isEmpty() ? List.of("") : idsBolsillo, idUsuario);
        BigDecimal montoPorRevisar = idsBolsillo.isEmpty() ? BigDecimal.ZERO
                : movimientos.sumarPorRevisarPorMes(idsBolsillo, mes.getYear(), mes.getMonthValue());
        return new ResumenGlobalContabilidadDTO(ingresos, gastos, ahorros, transferencias, saldoAhorros,
                porRevisar, montoPorRevisar);
    }

    @Transactional
    public MovimientoContableDTO crear(String idUsuario, MovimientoContableSolicitudDTO solicitud) {
        return crear(idUsuario, solicitud, null, false, null);
    }

    /** Guarda una sugerencia financiera de Gmail en la bandeja común de revisión, sin tocar bolsillos de ahorro. */
    @Transactional
    public MovimientoContableDTO crearGmailPorRevisar(String idUsuario, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        if (solicitud == null || !"GMAIL".equalsIgnoreCase(solicitud.origenTipo())) {
            throw new IllegalArgumentException("La sugerencia debe provenir de un correo Gmail.");
        }
        String tipo = tipoManual(solicitud.tipo());
        if (!"GASTO".equals(tipo) && !"INGRESO".equals(tipo)) {
            throw new IllegalArgumentException("El movimiento Gmail debe ser un GASTO o INGRESO.");
        }
        MBolsillo porRevisar = servicioBolsillo.obtenerOCrearPorRevisar(idUsuario);
        MovimientoContableSolicitudDTO pendiente = new MovimientoContableSolicitudDTO(
                porRevisar.getIdBolsillo(), tipo, solicitud.fecha(), solicitud.monto(), solicitud.categoria(),
                solicitud.concepto(), "GMAIL", solicitud.origenId(), solicitud.origenNombre());
        return crear(idUsuario, pendiente, EstadoRevisionMovimiento.POR_REVISAR, true, tipo);
    }

    /** Confirma como movimiento global el tipo GASTO/INGRESO revisado por el usuario. */
    @Transactional
    public MovimientoContableDTO confirmarGmailGlobal(String idUsuario, String idMovimiento) {
        validarUsuario(idUsuario);
        MMovimientoContable movimiento = propio(idUsuario, idMovimiento);
        if (movimiento.getEstadoRevision() != EstadoRevisionMovimiento.POR_REVISAR
                || !"GMAIL".equalsIgnoreCase(movimiento.getOrigenTipo())) {
            throw new IllegalArgumentException("Solo se pueden confirmar globalmente movimientos Gmail por revisar.");
        }
        String tipo = tipoManual(movimiento.getTipo());
        if (!"GASTO".equals(tipo) && !"INGRESO".equals(tipo)) {
            throw new IllegalArgumentException("El movimiento Gmail debe ser un GASTO o INGRESO.");
        }
        movimiento.setBolsillo(null);
        movimiento.setIdUsuario(idUsuario);
        movimiento.setTipo(tipo);
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.CONFIRMADO);
        return dto(movimientos.save(movimiento));
    }

    private MovimientoContableDTO crear(String idUsuario, MovimientoContableSolicitudDTO solicitud,
                                        EstadoRevisionMovimiento estadoRevision, boolean omitirSaldo,
                                        String tipoMovimiento) {
        validarUsuario(idUsuario);
        validar(solicitud);
        if ("GMAIL".equalsIgnoreCase(solicitud.origenTipo()) && movimientos.existsByOrigenTipoAndOrigenId("GMAIL", solicitud.origenId())) {
            throw new IllegalStateException("Este correo ya fue importado como movimiento.");
        }
        MBolsillo bolsillo = bolsilloPropio(idUsuario, solicitud.idBolsillo());
        if (!bolsillo.isEstado()) throw new IllegalArgumentException("Activa el bolsillo para registrar movimientos.");
        // Allow manual registration on AHORRO, GASTO, and INGRESO pockets
        if (tipoMovimiento == null) {
            String tipoBolsilloNorm = normalizarTipo(bolsillo.getTipo());
            if (!Set.of("AHORRO", "GASTO", "INGRESO").contains(tipoBolsilloNorm)) {
                throw new IllegalArgumentException("Los movimientos manuales solo pueden asignarse a un bolsillo de tipo AHORRO, GASTO o INGRESO.");
            }
        }
        String tipo = tipoMovimiento == null ? normalizarTipo(bolsillo.getTipo()) : normalizarTipo(tipoMovimiento);
        Origen origen = resolverOrigen(idUsuario, solicitud, tipo, null);
        if (!omitirSaldo) validarSaldoDisponible(origen.bolsillo(), tipo, solicitud.monto(), null);
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(UUID.randomUUID().toString());
        movimiento.setBolsillo(bolsillo);
        movimiento.setIdUsuario(idUsuario);
        movimiento.setTipo(tipo);
        movimiento.setFecha(solicitud.fecha());
        movimiento.setMonto(solicitud.monto());
        movimiento.setCategoria(solicitud.categoria().trim());
        movimiento.setConcepto(solicitud.concepto() == null ? "" : solicitud.concepto().trim());
        movimiento.setEstadoRevision(estadoRevision);
        aplicarOrigen(movimiento, origen, tipo);
        return dto(movimientos.save(movimiento));
    }

    /**
     * Persiste la importación automática en un bolsillo compatible cuando la clasificación es confiable.
     * Si hay ambigüedad de bolsillo, el movimiento se envía a Por revisar.
     */
    @Transactional
    public java.util.Optional<MovimientoContableDTO> crearAutomaticamenteDesdeGmail(
            String idUsuario, String tipo, java.time.LocalDate fecha, BigDecimal monto,
            String categoria, String concepto, String origenId, String origenNombre,
            ResultadoClasificacion clasificacion) {
        validarUsuario(idUsuario);
        boolean revisar = clasificacion == null || clasificacion.revisar();
        MBolsillo bolsillo;
        if (revisar) {
            bolsillo = servicioBolsillo.obtenerOCrearPorRevisar(idUsuario);
        } else if (clasificacion.idBolsillo() != null) {
            bolsillo = bolsilloPropio(idUsuario, clasificacion.idBolsillo());
            if (!bolsillo.isEstado() || clasificacion.bolsillo() == null
                    || !clasificacion.bolsillo().name().equalsIgnoreCase(normalizarTipo(bolsillo.getTipo()))
                    || !tipo.equalsIgnoreCase(normalizarTipo(bolsillo.getTipo()))) {
                revisar = true;
                bolsillo = servicioBolsillo.obtenerOCrearPorRevisar(idUsuario);
            }
        } else if (clasificacion.bolsillo() != null && clasificacion.bolsillo().name().equalsIgnoreCase(tipo)) {
            List<MBolsillo> compatibles = bolsillosPropios(idUsuario).stream()
                    .filter(MBolsillo::isEstado)
                    .filter(item -> item.getTipo() != null && tipo.equalsIgnoreCase(normalizarTipo(item.getTipo())))
                    .toList();
            if (compatibles.size() == 1) bolsillo = compatibles.getFirst();
            else {
                if ("GASTO".equalsIgnoreCase(tipo) || "INGRESO".equalsIgnoreCase(tipo)) {
                    return java.util.Optional.of(crearMovimientoGlobalGmail(idUsuario, tipo, fecha, monto,
                            categoria, concepto, origenId, origenNombre));
                }
                revisar = true;
                bolsillo = servicioBolsillo.obtenerOCrearPorRevisar(idUsuario);
            }
        } else {
            revisar = true;
            bolsillo = servicioBolsillo.obtenerOCrearPorRevisar(idUsuario);
        }
        MovimientoContableSolicitudDTO solicitud = new MovimientoContableSolicitudDTO(
                bolsillo.getIdBolsillo(), tipo, fecha, monto, categoria, concepto,
                "GMAIL", origenId, origenNombre);
        EstadoRevisionMovimiento estado = revisar
                ? EstadoRevisionMovimiento.POR_REVISAR : EstadoRevisionMovimiento.CONFIRMADO;
        return java.util.Optional.of(crear(idUsuario, solicitud, estado, revisar, tipo));
    }

    private MovimientoContableDTO crearMovimientoGlobalGmail(String idUsuario, String tipo,
            java.time.LocalDate fecha, BigDecimal monto, String categoria, String concepto,
            String origenId, String origenNombre) {
        if (movimientos.existsByOrigenTipoAndOrigenId("GMAIL", origenId)) {
            throw new IllegalStateException("Este correo ya fue importado como movimiento.");
        }
        MMovimientoContable movimiento = new MMovimientoContable();
        movimiento.setIdMovimiento(UUID.randomUUID().toString());
        movimiento.setIdUsuario(idUsuario);
        movimiento.setBolsillo(null);
        movimiento.setTipo(tipo);
        movimiento.setFecha(fecha);
        movimiento.setMonto(monto);
        movimiento.setCategoria(categoria == null ? "" : categoria.trim());
        movimiento.setConcepto(concepto == null ? "" : concepto.trim());
        movimiento.setOrigenTipo("GMAIL");
        movimiento.setOrigenId(origenId);
        movimiento.setOrigenNombre(origenNombre);
        movimiento.setEstadoRevision(EstadoRevisionMovimiento.CONFIRMADO);
        return dto(movimientos.save(movimiento));
    }

    @Transactional
    public MovimientoContableDTO actualizar(String idUsuario, String idMovimiento, MovimientoContableSolicitudDTO solicitud) {
        validarUsuario(idUsuario);
        validar(solicitud);
        MMovimientoContable movimiento = propio(idUsuario, idMovimiento);
        boolean estabaPorRevisar = movimiento.getEstadoRevision() == EstadoRevisionMovimiento.POR_REVISAR;
        MBolsillo bolsillo = bolsilloPropio(idUsuario, solicitud.idBolsillo());
        if (!bolsillo.isEstado() && (movimiento.getBolsillo() == null
                || !bolsillo.getIdBolsillo().equals(movimiento.getBolsillo().getIdBolsillo()))) {
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
        if (estabaPorRevisar) {
            reglasFiltro.registrarCorreccion(movimiento);
            movimiento.setEstadoRevision(EstadoRevisionMovimiento.CONFIRMADO);
        }
        return dto(movimientos.save(movimiento));
    }

    @Transactional
    public void eliminar(String idUsuario, String idMovimiento) {
        validarUsuario(idUsuario);
        movimientos.delete(propio(idUsuario, idMovimiento));
    }

    private MMovimientoContable propio(String idUsuario, String idMovimiento) {
        return movimientos.buscarPropioIncluyendoGlobal(idMovimiento, idUsuario)
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

    private String tipoManual(String tipoSolicitado) {
        if (tipoSolicitado == null || tipoSolicitado.isBlank()) {
            throw new IllegalArgumentException("Selecciona un tipo de movimiento válido: GASTO, INGRESO, AHORRO o TRANSFERENCIA.");
        }
        String normalizado = tipoSolicitado.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_BASE.contains(normalizado)) {
            throw new IllegalArgumentException("El tipo de movimiento debe ser GASTO, INGRESO, AHORRO o TRANSFERENCIA.");
        }
        return normalizado;
    }

    private String tipoBolsillo(MBolsillo bolsillo) {
        String tipo = bolsillo.getTipo();
        if (tipo == null || tipo.isBlank()) throw new IllegalArgumentException("El bolsillo no tiene un tipo de movimiento configurado.");
        return normalizarTipo(tipo);
    }

    private Origen resolverOrigen(String idUsuario, MovimientoContableSolicitudDTO solicitud, String tipo, String idMovimiento) {
        String origenTipo = solicitud.origenTipo() == null ? "" : solicitud.origenTipo().trim().toUpperCase(Locale.ROOT);
        if ("TRANSFERENCIA".equalsIgnoreCase(tipo) && !"BOLSILLO".equals(origenTipo)) {
            throw new IllegalArgumentException("Una transferencia requiere origen en otro bolsillo.");
        }
        // For INGRESO or GASTO without an explicit origin, skip origin resolution
        if (("INGRESO".equalsIgnoreCase(tipo) || "GASTO".equalsIgnoreCase(tipo)) && !"GMAIL".equals(origenTipo) && !"EFECTIVO".equals(origenTipo) && !"BOLSILLO".equals(origenTipo) && !"CUENTA".equals(origenTipo)) {
            return new Origen(null, "", null, "");
        }
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
                if ("TRANSFERENCIA".equalsIgnoreCase(tipo)
                        && origen.getIdBolsillo().equals(solicitud.idBolsillo())) {
                    throw new IllegalArgumentException("El bolsillo de origen debe ser distinto del bolsillo destino.");
                }
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
        return new MovimientoContableDTO(movimiento.getIdMovimiento(),
                movimiento.getBolsillo() == null ? null : movimiento.getBolsillo().getIdBolsillo(),
                movimiento.getBolsillo() == null ? null : movimiento.getBolsillo().getNombre(),
                movimiento.getFecha(), movimiento.getMonto(), movimiento.getTipo(), movimiento.getCategoria(), movimiento.getConcepto(),
                movimiento.getOrigenTipo(), movimiento.getOrigenId(), movimiento.getOrigenNombre(), movimiento.getEstadoRevision());
    }
}
