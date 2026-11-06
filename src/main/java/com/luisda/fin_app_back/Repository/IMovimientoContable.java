package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IMovimientoContable extends JpaRepository<MMovimientoContable, String> {
    List<MMovimientoContable> findByBolsillo_IdBolsilloOrderByFechaDesc(String idBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloAndTipoIgnoreCaseOrderByFechaDesc(String idBolsillo, String tipo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInOrderByFechaDesc(List<String> idsBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInAndTipoIgnoreCaseOrderByFechaDesc(List<String> idsBolsillo, String tipo);
    boolean existsByBolsillo_IdBolsillo(String idBolsillo);

    @Query("SELECT m.bolsillo.idBolsillo, m.tipo, COUNT(m), SUM(m.monto) FROM MMovimientoContable m " +
            "WHERE m.bolsillo.idBolsillo IN :idsBolsillo GROUP BY m.bolsillo.idBolsillo, m.tipo")
    List<Object[]> resumirPorBolsillos(@Param("idsBolsillo") List<String> idsBolsillo);
}
