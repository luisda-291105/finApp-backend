package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "movimiento_contable")
public class MMovimientoContable {
    @Id
    @Column(length = 36, nullable = false)
    private String idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bolsillo", nullable = false)
    private MBolsillo bolsillo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    // La base instalada mantiene esta columna como obligatoria.
    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Column(nullable = false, length = 160)
    private String concepto;

    @Column(name = "origen_tipo", length = 20)
    private String origenTipo;

    @Column(name = "origen_id", length = 36)
    private String origenId;

    @Column(name = "origen_nombre", length = 120)
    private String origenNombre;

    public String getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(String idMovimiento) { this.idMovimiento = idMovimiento; }
    public MBolsillo getBolsillo() { return bolsillo; }
    public void setBolsillo(MBolsillo bolsillo) { this.bolsillo = bolsillo; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public String getOrigenTipo() { return origenTipo; }
    public void setOrigenTipo(String origenTipo) { this.origenTipo = origenTipo; }
    public String getOrigenId() { return origenId; }
    public void setOrigenId(String origenId) { this.origenId = origenId; }
    public String getOrigenNombre() { return origenNombre; }
    public void setOrigenNombre(String origenNombre) { this.origenNombre = origenNombre; }
}
