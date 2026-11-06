package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMovimientoContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IMovimientoContable extends JpaRepository<MMovimientoContable, String> {
    List<MMovimientoContable> findByBolsillo_IdBolsilloOrderByFechaDesc(String idBolsillo);
    List<MMovimientoContable> findByBolsillo_IdBolsilloAndTipoIgnoreCaseOrderByFechaDesc(String idBolsillo, String tipo);
    boolean existsByBolsillo_IdBolsillo(String idBolsillo);
}
