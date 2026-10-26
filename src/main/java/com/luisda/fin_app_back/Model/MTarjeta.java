package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

/**
 * @uso               Representa un usuario de la aplicación
 * @capa              Modelo
 * @responsabilidades Mantener los datos y la fecha de creación del usuario
 * @datos             Identificador, nombre, contacto, contraseña, propiedad y fecha
 * @dependencias      JPA
 * @usadoPor          UsuarioRepository
 */

@Entity
@Table(name = "tarjetas")
public class MTarjeta {
    @Id
    @Column(length = 36, nullable = false)
    private String idTarjeta;

    @Column(length = 100, nullable = false)
    private String titular;

    @Column(length = 100, nullable = false)
    private String numero;

    @Column(length = 36, nullable = false)
    private LocalDate expiracion;

    @Column(length = 10, nullable = false)
    private String cvv;

    @Column(length = 100, nullable = false)
    private String entidad;

    @Column(length = 36, nullable = false)
    private String colorTema;


    // Relaciones entre clase usuario y tarjeta
    @ManyToOne
    @JoinColumn(name = "idUsuario")
    // @JsonManagedReference
    private MUsuario MUsuario;


    public MTarjeta(String idTarjeta, String titular, String numero, LocalDate expiracion, String cvv, String entidad, String colorTema) {
        this.idTarjeta = idTarjeta;
        this.titular = titular;
        this.numero = numero;
        this.expiracion = expiracion;
        this.cvv = cvv;
        this.entidad = entidad;
        this.colorTema = colorTema;
    }

    public MTarjeta() {
    }

    public String getIdTarjeta() {
        return idTarjeta;
    }

    public void setIdTarjeta(String idTarjeta) {
        this.idTarjeta = idTarjeta;
    }

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getExpiracion() {
        return expiracion;
    }

    public void setExpiracion(LocalDate expiracion) {
        this.expiracion = expiracion;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getColorTema() {
        return colorTema;
    }

    public void setColorTema(String colorTema) {
        this.colorTema = colorTema;
    }

}
