package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.BolsilloSolicitudDTO;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MContabilidad;
import com.luisda.fin_app_back.Repository.IContabilidad;
import com.luisda.fin_app_back.Repository.IBolsillo;
import com.luisda.fin_app_back.Repository.IMovimientoContable;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/** CRUD de bolsillos privados del usuario autenticado. */
@Service
public class SBolsillo {
    private static final Set<String> TIPOS_BASE = Set.of("INGRESO", "GASTO", "AHORRO", "POR_REVISAR");
    /** Nombres deterministas de los bolsillos fijos de ingreso y gasto que siempre existen. */
    static final String NOMBRE_FIJO_INGRESO = "Ingresos";
    static final String NOMBRE_FIJO_GASTO = "Gastos";
    private final IBolsillo bolsillos;
    private final IContabilidad contabilidades;
    private final IUsuario usuarios;
    private final IMovimientoContable movimientos;

    public SBolsillo(IBolsillo bolsillos, IContabilidad contabilidades, IUsuario usuarios,
                     IMovimientoContable movimientos) {
        this.bolsillos = bolsillos;
        this.contabilidades = contabilidades;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
    }

    @Transactional
    public List<MBolsillo> listar(String idUsuario) {
        return asegurarBolsillosFijos(idUsuario,
                listarTodos(idUsuario).stream().filter(bolsillo -> !esPorRevisar(bolsillo)).toList());
    }

    /** Garantiza que los bolsillos fijos «Ingresos» y «Gastos» existan y estén activos; los lista primero. */
    private List<MBolsillo> asegurarBolsillosFijos(String idUsuario, List<MBolsillo> lista) {
        List<MBolsillo> resultado = new ArrayList<>(lista);
        List<MBolsillo> fijos = new ArrayList<>();
        for (String[] definicion : new String[][] {{NOMBRE_FIJO_INGRESO, "INGRESO"}, {NOMBRE_FIJO_GASTO, "GASTO"}}) {
            MBolsillo fijo = resultado.stream()
                    .filter(bolsillo -> definicion[0].equalsIgnoreCase(bolsillo.getNombre())
                            && definicion[1].equalsIgnoreCase(bolsillo.getTipo()))
                    .findFirst()
                    .orElseGet(() -> persistirNuevo(idUsuario,
                            new BolsilloSolicitudDTO(definicion[0], true, BigDecimal.ZERO, definicion[1])));
            if (!resultado.contains(fijo)) resultado.add(fijo);
            if (!fijo.isEstado()) {
                fijo.setEstado(true);
                bolsillos.save(fijo);
            }
            fijos.add(fijo);
        }
        resultado.removeAll(fijos);
        fijos.addAll(resultado);
        return fijos;
    }

    /** Devuelve el bolsillo interno por separado, sin incluirlo en el listado normal. */
    public java.util.Optional<MBolsillo> obtenerPorRevisar(String idUsuario) {
        return listarTodos(idUsuario).stream().filter(SBolsillo::esPorRevisar).findFirst();
    }

    private List<MBolsillo> listarTodos(String idUsuario) {
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
            if ("Por revisar".equalsIgnoreCase(bolsillo.getNombre())
                    && "OTROS".equalsIgnoreCase(bolsillo.getTipo())) {
                bolsillo.setTipo("POR_REVISAR");
                actualizados = true;
            }
        }
        return actualizados ? bolsillos.saveAll(lista) : lista;
    }

    public MBolsillo crear(String idUsuario, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        validarTipoAhorroUsuario(solicitud.tipo());
        return persistirNuevo(idUsuario, solicitud);
    }

    private MBolsillo persistirNuevo(String idUsuario, BolsilloSolicitudDTO solicitud) {
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

    /** Reutiliza o crea el bolsillo de sistema usado para movimientos que requieren revisión. */
    @Transactional
    public MBolsillo obtenerOCrearPorRevisar(String idUsuario) {
        return obtenerPorRevisar(idUsuario).orElseGet(() -> persistirNuevo(idUsuario,
                new BolsilloSolicitudDTO("Por revisar", true, BigDecimal.ZERO, "POR_REVISAR")));
    }

    @Transactional
    public MBolsillo actualizar(String idUsuario, String idBolsillo, BolsilloSolicitudDTO solicitud) {
        validar(solicitud);
        validarTipoAhorroUsuario(solicitud.tipo());
        MBolsillo bolsillo = propio(idUsuario, idBolsillo);
        if (esPorRevisar(bolsillo)) {
            throw new IllegalArgumentException("El bolsillo «Por revisar» es interno y no se puede editar desde esta operación.");
        }
        if (esFijo(bolsillo)) {
            throw new IllegalArgumentException("Los bolsillos fijos «Ingresos» y «Gastos» no se pueden editar.");
        }
        String tipoNuevo = normalizarTipo(solicitud.tipo());
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
        if (esPorRevisar(bolsillo)) {
            throw new IllegalStateException("No puedes eliminar el bolsillo interno «Por revisar».");
        }
        if (esFijo(bolsillo)) {
            throw new IllegalStateException("No puedes eliminar los bolsillos fijos «Ingresos» y «Gastos».");
        }
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

    private static void validarTipoAhorroUsuario(String tipo) {
        if (tipo == null || !"AHORRO".equals(tipo.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Los bolsillos del usuario solo pueden ser de tipo AHORRO.");
        }
    }

    private static boolean esPorRevisar(MBolsillo bolsillo) {
        return bolsillo != null && "Por revisar".equalsIgnoreCase(bolsillo.getNombre())
                && bolsillo.getTipo() != null
                && ("OTROS".equalsIgnoreCase(bolsillo.getTipo()) || "POR_REVISAR".equalsIgnoreCase(bolsillo.getTipo()));
    }

    /** Regla compartida con el frontend: bolsillo fijo por nombre y tipo («Ingresos»/INGRESO, «Gastos»/GASTO). */
    static boolean esFijo(MBolsillo bolsillo) {
        if (bolsillo == null) return false;
        String nombre = bolsillo.getNombre() == null ? "" : bolsillo.getNombre().trim();
        String tipo = bolsillo.getTipo() == null ? "" : bolsillo.getTipo().trim().toUpperCase(Locale.ROOT);
        return (NOMBRE_FIJO_INGRESO.equalsIgnoreCase(nombre) && "INGRESO".equals(tipo))
                || (NOMBRE_FIJO_GASTO.equalsIgnoreCase(nombre) && "GASTO".equals(tipo));
    }

    private static String normalizarTipo(String tipo) {
        if (tipo == null) return "";
        String limpio = tipo.trim().replaceAll("\\s+", " ");
        String mayusculas = limpio.toUpperCase(Locale.ROOT);
        return TIPOS_BASE.contains(mayusculas) ? mayusculas : limpio;
    }

    private static String inferirTipo(String nombre) {
        String limpio = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        if (limpio.contains("revis")) return "POR_REVISAR";
        if (limpio.contains("ingres")) return "INGRESO";
        if (limpio.contains("ahorr")) return "AHORRO";
        return "GASTO";
    }

}
