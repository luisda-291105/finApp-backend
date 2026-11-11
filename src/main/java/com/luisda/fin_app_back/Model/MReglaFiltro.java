package com.luisda.fin_app_back.Model;

import com.luisda.fin_app_back.Filtrado.NormalizadorFiltro;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Regla de clasificación asociada a un bolsillo. */
@Entity
@Table(name = "regla_filtro", uniqueConstraints = @UniqueConstraint(
        name = "uk_regla_filtro_bolsillo_tipo_valor",
        columnNames = {"id_bolsillo", "tipo_regla", "valor_normalizado"}))
public class MReglaFiltro {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bolsillo", nullable = false)
    private MBolsillo bolsillo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_regla", length = 20, nullable = false)
    private TipoReglaFiltro tipo;

    @Column(length = 180, nullable = false)
    private String valor;

    @Column(name = "valor_normalizado", length = 180, nullable = false)
    private String valorNormalizado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public MBolsillo getBolsillo() { return bolsillo; }
    public void setBolsillo(MBolsillo bolsillo) { this.bolsillo = bolsillo; }
    public TipoReglaFiltro getTipo() { return tipo; }
    public void setTipo(TipoReglaFiltro tipo) { this.tipo = tipo; }
    public String getValor() { return valor; }
    public void setValor(String valor) {
        this.valor = valor == null ? null : valor.trim();
        this.valorNormalizado = NormalizadorFiltro.normalizar(valor);
    }
    public String getValorNormalizado() { return valorNormalizado; }
    public void setValorNormalizado(String valorNormalizado) { this.valorNormalizado = valorNormalizado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    @PrePersist
    @PreUpdate
    private void actualizarValorNormalizado() {
        valorNormalizado = NormalizadorFiltro.normalizar(valor);
    }
}
