package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MBolsillo;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface IBolsillo extends JpaRepository<MBolsillo, String> {
    List<MBolsillo> findByContabilidad_IdContabilidadOrderByFechaCreacionDesc(String idContabilidad);
    Optional<MBolsillo> findByIdBolsilloAndContabilidad_IdContabilidad(String idBolsillo, String idContabilidad);
    List<MBolsillo> findByIdUsuarioOrderByFechaCreacionDesc(String idUsuario);
    Optional<MBolsillo> findByIdBolsilloAndIdUsuario(String idBolsillo, String idUsuario);
}
