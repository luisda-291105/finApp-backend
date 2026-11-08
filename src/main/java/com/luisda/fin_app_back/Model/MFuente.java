package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
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

    @JsonIgnore
    @Column(name = "access_token_cifrado", columnDefinition = "TEXT")
    private String accessTokenCifrado;

    @JsonIgnore
    @Column(name = "refresh_token_cifrado", columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @JsonIgnore
    @Column(name = "expira_en")
    private Instant expiraEn;


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

    @JsonIgnore
    public boolean tieneConexionGmail() {
        boolean renovable = refreshTokenCifrado != null && !refreshTokenCifrado.isBlank();
        boolean accesoVigente = accessTokenCifrado != null && !accessTokenCifrado.isBlank()
                && expiraEn != null && expiraEn.isAfter(Instant.now());
        return renovable || accesoVigente;
    }

    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }
    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }

    public void desconectarGmail() {
        accessTokenCifrado = null;
        refreshTokenCifrado = null;
        expiraEn = null;
    }
}
