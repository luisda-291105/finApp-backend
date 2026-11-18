package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MRefreshFamilia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * @uso               Persistencia de familias de refresh tokens
 * @capa              Repositorio
 * @responsabilidades Buscar por usuario, revocar en bloque y purgar vencidas
 * @datos             MRefreshFamilia
 * @dependencias      Spring Data JPA
 * @usadoPor          SRefreshFamilias
 */
public interface IRefreshFamilia extends JpaRepository<MRefreshFamilia, String> {

    List<MRefreshFamilia> findByIdUsuario(String idUsuario);

    long deleteByAbsoluteExpiresAtBefore(Instant corte);
}
