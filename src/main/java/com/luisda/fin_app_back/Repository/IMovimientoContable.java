package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IMovimientoContable extends JpaRepository<MMovimientoContable, String> {
    List<MMovimientoContable> findByBolsillo_IdBolsilloOrderByFechaDesc(String idBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInOrderByFechaDesc(List<String> idsBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloInAndBolsillo_TipoIgnoreCaseOrderByFechaDesc(List<String> idsBolsillo, String tipo);
    boolean existsByBolsillo_IdBolsillo(String idBolsillo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MMovimientoContable m SET m.tipo = :tipo WHERE m.bolsillo.idBolsillo = :idBolsillo")
    int actualizarTipoPorBolsillo(@Param("idBolsillo") String idBolsillo, @Param("tipo") String tipo);

    @Query("SELECT m.bolsillo.idBolsillo, m.bolsillo.tipo, COUNT(m), SUM(m.monto) FROM MMovimientoContable m " +
            "WHERE m.bolsillo.idBolsillo IN :idsBolsillo GROUP BY m.bolsillo.idBolsillo, m.bolsillo.tipo")
    List<Object[]> resumirPorBolsillos(@Param("idsBolsillo") List<String> idsBolsillo);
}
