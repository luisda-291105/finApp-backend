package com.luisda.fin_app_back.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @uso               Representa un espacio de información dentro de una contabilidad
 * @capa              Modelo
 * @responsabilidades Mantener los datos y el tipo del bolsillo
 * @datos             Identificador, contabilidad, nombre, objetivo, estado y fecha
 * @dependencias      JPA
 * @usadoPor          BolsilloRepository
 */
@Entity
@Table(name = "bolsillo")
public class MBolsillo {
    @Id
    @Column(length = 36, nullable = false)
    private String idBolsillo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contabilidad", nullable = false)
    @JsonIgnore
    private MContabilidad contabilidad;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(name = "monto_objetivo", nullable = false, precision = 19, scale = 2,
            columnDefinition = "DECIMAL(19,2) NOT NULL DEFAULT 0.00")
    private BigDecimal montoObjetivo = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean estado;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    public MBolsillo() {
    }

    public MBolsillo(String idBolsillo, String idContabilidad, String nombre, boolean estado, LocalDate fechaCreacion) {
        this.idBolsillo = idBolsillo;
        MContabilidad referencia = new MContabilidad();
        referencia.setIdContabilidad(idContabilidad);
        this.contabilidad = referencia;
        this.nombre = nombre;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public BigDecimal getMontoObjetivo() { return montoObjetivo; }

    public void setMontoObjetivo(BigDecimal montoObjetivo) {
        this.montoObjetivo = montoObjetivo == null ? BigDecimal.ZERO : montoObjetivo;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public boolean isEstado() {
        return estado;
    }

    public void setEstado(boolean estado) {
        this.estado = estado;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @JsonIgnore
    public MContabilidad getContabilidad() { return contabilidad; }

    public void setContabilidad(MContabilidad contabilidad) { this.contabilidad = contabilidad; }

    public String getIdContabilidad() { return contabilidad == null ? null : contabilidad.getIdContabilidad(); }

    public void setIdContabilidad(String idContabilidad) {
        MContabilidad referencia = new MContabilidad();
        referencia.setIdContabilidad(idContabilidad);
        this.contabilidad = referencia;
    }

    public String getIdBolsillo() {
        return idBolsillo;
    }

    public void setIdBolsillo(String idBolsillo) {
        this.idBolsillo = idBolsillo;
    }
}
