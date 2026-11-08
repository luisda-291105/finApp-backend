package com.luisda.fin_app_back.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

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

    @Column(name = "id_usuario", length = 36)
    private String idUsuario;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(length = 40)
    private String tipo;

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

    public String getTipo() { return tipo; }

    public void setTipo(String tipo) {
        if (tipo == null) {
            this.tipo = null;
            return;
        }
        String limpio = tipo.trim().replaceAll("\\s+", " ");
        String normalizado = limpio.toUpperCase(Locale.ROOT);
        this.tipo = switch (normalizado) {
            case "INGRESO", "GASTO", "AHORRO" -> normalizado;
            default -> limpio;
        };
    }

    @JsonIgnore
    public MContabilidad getContabilidad() { return contabilidad; }

    public void setContabilidad(MContabilidad contabilidad) { this.contabilidad = contabilidad; }

    public String getIdUsuario() { return idUsuario; }

    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }

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
