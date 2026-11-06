package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MBolsillo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IBolsillo extends JpaRepository<MBolsillo, String> {
    List<MBolsillo> findByIdContabilidadOrderByFechaCreacionDesc(String idContabilidad);
    Optional<MBolsillo> findByIdBolsilloAndIdContabilidad(String idBolsillo, String idContabilidad);
}
