package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.Filtrado.ClasificadorMovimiento;
import com.luisda.fin_app_back.Filtrado.ReglaFiltro;
import com.luisda.fin_app_back.Filtrado.ResultadoClasificacion;
import com.luisda.fin_app_back.Filtrado.TipoBolsillo;
import com.luisda.fin_app_back.Model.MReglaFiltro;
import com.luisda.fin_app_back.Model.TipoReglaFiltro;
import com.luisda.fin_app_back.Repository.IReglaFiltro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Carga las reglas del usuario y las adapta al clasificador puro. */
@Service
public class SClasificadorMovimiento {
    private final IReglaFiltro reglas;
    private final ClasificadorMovimiento clasificador;

    public SClasificadorMovimiento(IReglaFiltro reglas, ClasificadorMovimiento clasificador) {
        this.reglas = reglas;
        this.clasificador = clasificador;
    }

    @Transactional(readOnly = true)
    public ResultadoClasificacion clasificar(String idUsuario, String remitente, String contenido) {
        List<ReglaFiltro> reglasUsuario = reglas.findActivasPorUsuario(idUsuario)
                .stream().map(this::convertir).toList();
        return clasificador.clasificar(remitente, contenido, reglasUsuario);
    }

    private ReglaFiltro convertir(MReglaFiltro regla) {
        TipoBolsillo tipoBolsillo = TipoBolsillo.parse(regla.getBolsillo().getTipo());
        String palabra = regla.getTipo() == TipoReglaFiltro.PALABRA_CLAVE ? regla.getValor() : null;
        String remitente = regla.getTipo() == TipoReglaFiltro.REMITENTE ? regla.getValor() : null;
        return new ReglaFiltro(regla.getBolsillo().getIdBolsillo(), tipoBolsillo,
                palabra == null ? List.of() : List.of(palabra),
                remitente == null ? List.of() : List.of(remitente));
    }
}
