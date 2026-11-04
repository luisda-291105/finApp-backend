package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MMuestra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IMuestraRepository extends JpaRepository<MMuestra, Long> {
    List<MMuestra> findByUsuarioIdOrderByFechaCreacionAsc(String usuarioId);
    List<MMuestra> findByUsuarioId(String usuarioId);
    boolean existsByUsuarioIdAndTipoAndTitulo(String usuarioId, String tipo, String titulo);
    boolean existsByUsuarioIdAndTipoAndTituloAndIdNot(String usuarioId, String tipo, String titulo, Long id);
}
