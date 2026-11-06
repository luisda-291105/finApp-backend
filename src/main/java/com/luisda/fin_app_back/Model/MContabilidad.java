package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", unique = true)
    private MUsuario usuario;


    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(nullable = false)
    private double valor;

    @Column(nullable = false)
    private boolean estado;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    public MContabilidad() {
    }

    public MContabilidad(String idContabilidad, String nombre, double valor, boolean estado, LocalDate fechaCreacion) {
        this.idContabilidad = idContabilidad;
        this.nombre = nombre;
        this.valor = valor;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public String getIdContabilidad() {
        return idContabilidad;
    }

    public MUsuario getUsuario() { return usuario; }

    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }

    public void setIdContabilidad(String idContabilidad) {
        this.idContabilidad = idContabilidad;
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
