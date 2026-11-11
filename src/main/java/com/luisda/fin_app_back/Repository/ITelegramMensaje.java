package com.luisda.fin_app_back.Repository;

import com.luisda.fin_app_back.Model.MTelegramMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ITelegramMensaje extends JpaRepository<MTelegramMensaje, Long> {
    List<MTelegramMensaje> findTop100ByUsuario_IdUsuarioOrderByFechaDesc(String idUsuario);
}
