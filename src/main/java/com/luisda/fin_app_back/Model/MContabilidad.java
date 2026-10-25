package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * @uso               Representa una contabilidad perteneciente a un grupo
 * @capa              Modelo
 * @responsabilidades Mantener los datos y el estado de la contabilidad
 * @datos             Identificador, grupo, contacto opcional, nombre, valor y fechas
 * @dependencias      JPA
 * @usadoPor          ContabilidadRepository
 */
@Entity
@Table(name = "contabilidad")

public class MContabilidad {
    @Id
    @Column(length = 36, nullable = false)
    private String idContabilidad;

    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(nullable = false)
    private double valor;

    @Column(nullable = false)
    private boolean estado;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    // Relación entre clase MContabilidad y MUsuario
    /*
    @ManyToOne
    @JoinColumn(name = "idUsuario")
    @JsonManagedReference
    private MUsuario MUsuario;
*/

    public MContabilidad() {
    }

    public MContabilidad(String idContabilidad, String idUsuario, String nombre, double valor, boolean estado, LocalDate fechaCreacion) {
        this.idContabilidad = idContabilidad;
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.valor = valor;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public String getIdContabilidad() {
        return idContabilidad;
    }

    public void setIdContabilidad(String idContabilidad) {
        this.idContabilidad = idContabilidad;
    }


    public String getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }


    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }

    public boolean isEstado() {
        return estado;
    }

    public void setEstado(boolean estado) {
        this.estado = estado;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
