package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IMovimientoContable extends JpaRepository<MMovimientoContable, String> {
    List<MMovimientoContable> findByBolsillo_IdBolsilloOrderByFechaDesc(String idBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInOrderByFechaDesc(List<String> idsBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInAndTipoIgnoreCaseOrderByFechaDesc(List<String> idsBolsillo, String tipo);
    boolean existsByBolsillo_IdBolsillo(String idBolsillo);
    Optional<MMovimientoContable> findByIdMovimientoAndBolsillo_IdUsuario(String idMovimiento, String idUsuario);

    @Query(value = "SELECT COALESCE(SUM(CASE WHEN UPPER(tipo) = 'INGRESO' AND id_bolsillo = :idBolsillo THEN monto " +
            "WHEN (origen_tipo = 'BOLSILLO' AND origen_id = :idBolsillo) OR " +
            "(origen_tipo IS NULL AND UPPER(tipo) <> 'INGRESO' AND id_bolsillo = :idBolsillo) THEN -monto ELSE 0 END), 0) " +
            "FROM movimiento_contable WHERE (id_bolsillo = :idBolsillo OR origen_id = :idBolsillo) " +
            "AND (:idMovimientoExcluir IS NULL OR id_movimiento <> :idMovimientoExcluir)", nativeQuery = true)
    java.math.BigDecimal calcularSaldoDisponible(@Param("idBolsillo") String idBolsillo,
                                                  @Param("idMovimientoExcluir") String idMovimientoExcluir);

    @Query("SELECT m.bolsillo.idBolsillo, m.tipo, COUNT(m), SUM(m.monto) FROM MMovimientoContable m " +
            "WHERE m.bolsillo.idBolsillo IN :idsBolsillo GROUP BY m.bolsillo.idBolsillo, m.tipo")
    List<Object[]> resumirPorBolsillos(@Param("idsBolsillo") List<String> idsBolsillo);
}
