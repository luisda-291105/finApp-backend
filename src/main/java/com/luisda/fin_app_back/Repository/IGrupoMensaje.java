package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MGrupoMensaje;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistencia de los mensajes del asistente de cada grupo. */
public interface IGrupoMensaje extends JpaRepository<MGrupoMensaje, String> {
    List<MGrupoMensaje> findByIdGrupoOrderByFechaAscIdMensajeAsc(String idGrupo);
    List<MGrupoMensaje> findByIdGrupoOrderByFechaDescIdMensajeDesc(String idGrupo, Pageable pageable);
    long deleteByIdGrupo(String idGrupo);
}
