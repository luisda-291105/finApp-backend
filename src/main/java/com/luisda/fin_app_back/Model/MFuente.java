package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * @uso               Representa una fuente externa asociada a un usuario
 * @capa              Modelo
 * @responsabilidades Mantener la identidad, tipo, estado y fecha de la fuente
 * @datos             Identificadores y datos de la cuenta conectada
 * @dependencias      JPA
 * @usadoPor          Modelo y persistencia de fuentes
 */
@Entity
@Table(name = "fuente")
public class MFuente {

    @Id
    @Column(length = 36, nullable = false)
    private String idFuente;

    @Column(length = 36, nullable = false)
    private String tipo;

    @Column(length = 255, nullable = false)
    private String contenido;

    @Column(nullable = false)
    private boolean estado;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    // Relación entre clase MFuentes y MUsuario

    @ManyToOne(optional = false)
    @JoinColumn(name = "idUsuario", nullable = false)
    //@JsonManagedReference
    private MUsuario usuario;


    public MFuente(String idFuente, String tipo, String contenido, boolean estado, LocalDate fechaCreacion) {
        this.idFuente = idFuente;
        this.tipo = tipo;
        this.contenido = contenido;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public MFuente() {
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getIdFuente() {
        return idFuente;
    }

    public void setIdFuente(String idFuente) {
        this.idFuente = idFuente;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String identificador) {
        this.contenido = identificador;
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

    public MUsuario getUsuario() { return usuario; }

    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }
}
