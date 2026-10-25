package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * @uso               Representa un espacio de información dentro de una contabilidad
 * @capa              Modelo
 * @responsabilidades Mantener los datos y el tipo del bolsillo
 * @datos             Identificador, contabilidad, nombre, tipo, estado y fecha
 * @dependencias      JPA
 * @usadoPor          BolsilloRepository
 */
@Entity
@Table(name = "bolsillo")
public class MBolsillo {
    @Id
    @Column(length = 36, nullable = false)
    private String idBolsillo;

    @Column(length = 36, nullable = false)
    private String idContabilidad;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean estado;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    // Relación entre clase MBolsillo y MContabilidad
    /*
    @ManyToOne
    @JoinColumn(name = "idContabilidad")
    //@JsonManagedReference
    private MContabilidad MContabilidad;
    */

    public MBolsillo() {
    }

    public MBolsillo(String idBolsillo, String idContabilidad, String nombre, boolean estado, LocalDate fechaCreacion) {
        this.idBolsillo = idBolsillo;
        this.idContabilidad = idContabilidad;
        this.nombre = nombre;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
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

    public String getIdContabilidad() {
        return idContabilidad;
    }

    public void setIdContabilidad(String idContabilidad) {
        this.idContabilidad = idContabilidad;
    }

    public String getIdBolsillo() {
        return idBolsillo;
    }

    public void setIdBolsillo(String idBolsillo) {
        this.idBolsillo = idBolsillo;
    }
}
