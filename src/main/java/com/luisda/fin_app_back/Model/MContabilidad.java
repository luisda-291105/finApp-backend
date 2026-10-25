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
    private String idGrupo;

    @Column(length = 36)
    private String idContacto;

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

    public MContabilidad(String idContabilidad, String idGrupo, String idContacto,
                         String nombre, double valor, boolean estado, LocalDate fechaCreacion) {
        this.idContabilidad = idContabilidad;
        this.idGrupo = idGrupo;
        this.idContacto = idContacto;
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

    public String getIdGrupo() {
        return idGrupo;
    }

    public void setIdGrupo(String idGrupo) {
        this.idGrupo = idGrupo;
    }

    public String getIdContacto() {
        return idContacto;
    }

    public void setIdContacto(String idContacto) {
        this.idContacto = idContacto;
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
