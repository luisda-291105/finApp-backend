package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Filtrado.NormalizadorFiltro;
import com.luisda.fin_app_back.Model.MBolsillo;
import com.luisda.fin_app_back.Model.MMovimientoContable;
import com.luisda.fin_app_back.Model.MReglaFiltro;
import com.luisda.fin_app_back.Model.EstadoRevisionMovimiento;
import com.luisda.fin_app_back.Model.TipoReglaFiltro;
import com.luisda.fin_app_back.Repository.IReglaFiltro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Persiste reglas aprendidas cuando el usuario corrige un movimiento pendiente. */
@Service
public class SReglaFiltro {
    private final IReglaFiltro reglas;

    public SReglaFiltro(IReglaFiltro reglas) {
        this.reglas = reglas;
    }

    @Transactional
    public boolean registrarCorreccion(MMovimientoContable movimiento) {
        if (movimiento == null || movimiento.getEstadoRevision() != EstadoRevisionMovimiento.POR_REVISAR) return false;
        String remitente = movimiento.getOrigenNombre() == null ? "" : movimiento.getOrigenNombre().trim();
        return registrarSiNoExiste(movimiento.getBolsillo(), TipoReglaFiltro.REMITENTE, remitente);
    }

    @Transactional
    public boolean registrarSiNoExiste(MBolsillo bolsillo, TipoReglaFiltro tipo, String valor) {
        if (bolsillo == null || bolsillo.getIdBolsillo() == null || tipo == null || valor == null) return false;
        String valorLimpio = valor.trim();
        String normalizado = NormalizadorFiltro.normalizar(valorLimpio);
        if (normalizado.isBlank() || valorLimpio.length() > 180) return false;
        if (reglas.existsByBolsillo_IdBolsilloAndTipoAndValorNormalizado(
                bolsillo.getIdBolsillo(), tipo, normalizado)) return false;

        MReglaFiltro regla = new MReglaFiltro();
        regla.setId(UUID.randomUUID().toString());
        regla.setBolsillo(bolsillo);
        regla.setTipo(tipo);
        regla.setValor(valorLimpio);
        regla.setFechaCreacion(LocalDateTime.now());
        reglas.save(regla);
        return true;
    }
}
