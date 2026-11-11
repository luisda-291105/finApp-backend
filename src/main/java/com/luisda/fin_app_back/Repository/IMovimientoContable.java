package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IMovimientoContable extends JpaRepository<MMovimientoContable, String> {
    @Query("SELECT m FROM MMovimientoContable m LEFT JOIN m.bolsillo b WHERE " +
            "(b.idBolsillo IN :idsBolsillo OR (b IS NULL AND m.idUsuario = :idUsuario)) " +
            "AND (:estado IS NULL OR m.estadoRevision = :estado) ORDER BY m.fecha DESC")
    List<MMovimientoContable> listarDelUsuarioConGlobales(@Param("idsBolsillo") List<String> idsBolsillo,
            @Param("idUsuario") String idUsuario,
            @Param("estado") com.luisda.fin_app_back.Model.EstadoRevisionMovimiento estado);

    @Query("SELECT m FROM MMovimientoContable m LEFT JOIN m.bolsillo b WHERE m.idMovimiento = :idMovimiento AND " +
            "(m.idUsuario = :idUsuario OR b.idUsuario = :idUsuario)")
    Optional<MMovimientoContable> buscarPropioIncluyendoGlobal(@Param("idMovimiento") String idMovimiento,
            @Param("idUsuario") String idUsuario);

    @Query("SELECT FUNCTION('YEAR', m.fecha), FUNCTION('MONTH', m.fecha), UPPER(m.tipo), COUNT(m), SUM(m.monto) " +
            "FROM MMovimientoContable m LEFT JOIN m.bolsillo b WHERE (b.idBolsillo IN :idsBolsillo OR " +
            "(b IS NULL AND m.idUsuario = :idUsuario)) " +
            "AND (m.estadoRevision IS NULL OR m.estadoRevision <> com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR) " +
            "AND (:incluirTransferencias = true OR UPPER(m.tipo) <> 'TRANSFERENCIA') " +
            "GROUP BY FUNCTION('YEAR', m.fecha), FUNCTION('MONTH', m.fecha), UPPER(m.tipo)")
    List<Object[]> resumirPorMesIncluyendoGlobales(@Param("idsBolsillo") List<String> idsBolsillo,
            @Param("idUsuario") String idUsuario, @Param("incluirTransferencias") boolean incluirTransferencias);

    @Query("SELECT m.origenId FROM MMovimientoContable m LEFT JOIN m.bolsillo b WHERE m.origenTipo = :origenTipo AND m.origenId IN :origenes " +
            "AND (m.idUsuario = :idUsuario OR b.idUsuario = :idUsuario)")
    List<String> listarOrigenesImportadosDelUsuario(@Param("origenTipo") String origenTipo,
            @Param("origenes") List<String> origenes, @Param("idUsuario") String idUsuario);

    @Query("SELECT COUNT(m) FROM MMovimientoContable m LEFT JOIN m.bolsillo b WHERE (b.idBolsillo IN :idsBolsillo OR " +
            "(b IS NULL AND m.idUsuario = :idUsuario)) AND " +
            "m.estadoRevision = com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR")
    long contarPorRevisarIncluyendoGlobales(@Param("idsBolsillo") List<String> idsBolsillo,
            @Param("idUsuario") String idUsuario);
    List<MMovimientoContable> findByBolsillo_IdBolsilloOrderByFechaDesc(String idBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInOrderByFechaDesc(List<String> idsBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInAndEstadoRevisionOrderByFechaDesc(
            List<String> idsBolsillo, com.luisda.fin_app_back.Model.EstadoRevisionMovimiento estadoRevision);
    long countByBolsillo_IdBolsilloInAndEstadoRevision(
            List<String> idsBolsillo, com.luisda.fin_app_back.Model.EstadoRevisionMovimiento estadoRevision);
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MMovimientoContable m " +
            "WHERE m.bolsillo.idBolsillo IN :idsBolsillo " +
            "AND m.estadoRevision = com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR " +
            "AND UPPER(m.tipo) <> 'TRANSFERENCIA' " +
            "AND UPPER(m.bolsillo.tipo) <> 'AHORRO' " +
            "AND FUNCTION('YEAR', m.fecha) = :anio AND FUNCTION('MONTH', m.fecha) = :mes")
    java.math.BigDecimal sumarPorRevisarPorMes(@Param("idsBolsillo") List<String> idsBolsillo,
                                               @Param("anio") int anio, @Param("mes") int mes);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInAndTipoIgnoreCaseOrderByFechaDesc(List<String> idsBolsillo, String tipo);
    boolean existsByBolsillo_IdBolsillo(String idBolsillo);
    boolean existsByOrigenTipoAndOrigenId(String origenTipo, String origenId);
    List<MMovimientoContable> findByOrigenTipoAndOrigenIdInAndBolsillo_IdUsuario(String origenTipo, List<String> origenes, String idUsuario);
    Optional<MMovimientoContable> findByIdMovimientoAndBolsillo_IdUsuario(String idMovimiento, String idUsuario);

    @Query(value = "SELECT COALESCE(SUM(CASE " +
            "WHEN UPPER(tipo) = 'INGRESO' AND id_bolsillo = :idBolsillo THEN monto " +
            "WHEN UPPER(tipo) = 'TRANSFERENCIA' AND id_bolsillo = :idBolsillo THEN monto " +
            "WHEN UPPER(tipo) = 'GASTO' AND id_bolsillo = :idBolsillo " +
            "AND (origen_tipo IS NULL OR origen_tipo <> 'BOLSILLO' OR origen_id = :idBolsillo) THEN -monto " +
            "WHEN origen_tipo = 'BOLSILLO' AND origen_id = :idBolsillo AND id_bolsillo <> :idBolsillo THEN -monto " +
            "ELSE 0 END), 0) " +
            "FROM movimiento_contable WHERE (id_bolsillo = :idBolsillo OR origen_id = :idBolsillo) " +
            "AND (estado_revision IS NULL OR estado_revision <> 'POR_REVISAR') " +
            "AND (:idMovimientoExcluir IS NULL OR id_movimiento <> :idMovimientoExcluir)", nativeQuery = true)
    java.math.BigDecimal calcularSaldoDisponible(@Param("idBolsillo") String idBolsillo,
                                                  @Param("idMovimientoExcluir") String idMovimientoExcluir);

    @Query("SELECT m.bolsillo.idBolsillo, m.tipo, COUNT(m), SUM(m.monto) FROM MMovimientoContable m " +
            "WHERE m.bolsillo.idBolsillo IN :idsBolsillo " +
            "AND (m.estadoRevision IS NULL OR m.estadoRevision <> com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR) " +
            "GROUP BY m.bolsillo.idBolsillo, m.tipo")
    List<Object[]> resumirPorBolsillos(@Param("idsBolsillo") List<String> idsBolsillo);

    @Query("SELECT m.bolsillo.idBolsillo, UPPER(m.tipo), m.categoria, COUNT(m), SUM(m.monto) " +
            "FROM MMovimientoContable m WHERE m.bolsillo.idBolsillo IN :idsBolsillo " +
            "AND (m.estadoRevision IS NULL OR m.estadoRevision <> com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR) " +
            "AND UPPER(m.tipo) <> 'TRANSFERENCIA' " +
            "GROUP BY m.bolsillo.idBolsillo, UPPER(m.tipo), m.categoria")
    List<Object[]> resumirPorBolsilloTipoCategoria(@Param("idsBolsillo") List<String> idsBolsillo);

    @Query("SELECT FUNCTION('YEAR', m.fecha), FUNCTION('MONTH', m.fecha), UPPER(m.tipo), COUNT(m), SUM(m.monto) " +
            "FROM MMovimientoContable m WHERE m.bolsillo.idBolsillo IN :idsBolsillo " +
            "AND (m.estadoRevision IS NULL OR m.estadoRevision <> com.luisda.fin_app_back.Model.EstadoRevisionMovimiento.POR_REVISAR) " +
            "AND (:incluirTransferencias = true OR UPPER(m.tipo) <> 'TRANSFERENCIA') " +
            "GROUP BY FUNCTION('YEAR', m.fecha), FUNCTION('MONTH', m.fecha), UPPER(m.tipo) " +
            "ORDER BY FUNCTION('YEAR', m.fecha) DESC, FUNCTION('MONTH', m.fecha) DESC, UPPER(m.tipo)")
    List<Object[]> resumirPorMesConFiltroTransferencias(@Param("idsBolsillo") List<String> idsBolsillo,
                                                        @Param("incluirTransferencias") boolean incluirTransferencias);

    default List<Object[]> resumirPorMes(List<String> idsBolsillo) {
        return resumirPorMesConFiltroTransferencias(idsBolsillo, false);
    }

    default List<Object[]> resumirPorMesIncluyendoTransferencias(List<String> idsBolsillo) {
        return resumirPorMesConFiltroTransferencias(idsBolsillo, true);
    }
}
