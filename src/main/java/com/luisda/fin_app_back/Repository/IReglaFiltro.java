package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MReglaFiltro;
import com.luisda.fin_app_back.Model.TipoReglaFiltro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IReglaFiltro extends JpaRepository<MReglaFiltro, String> {
    List<MReglaFiltro> findByBolsillo_IdBolsillo(String idBolsillo);
    @Query("SELECT r FROM MReglaFiltro r WHERE r.bolsillo.contabilidad.usuario.idUsuario = :idUsuario " +
            "AND r.bolsillo.estado = true")
    List<MReglaFiltro> findActivasPorUsuario(@Param("idUsuario") String idUsuario);
    boolean existsByBolsillo_IdBolsilloAndTipoAndValorNormalizado(
            String idBolsillo, TipoReglaFiltro tipo, String valorNormalizado);
}
