package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "gmail_suscripcion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_fuente", "correo_remitente"}),
        indexes = @Index(name = "idx_gmail_suscripcion_actividad", columnList = "ultima_actividad"))
public class MGmailSuscripcion {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private MFuente cuenta;

    @Column(name = "correo_remitente", length = 254, nullable = false)
    private String correoRemitente;

    @Column(length = 500)
    private String nombre;

    @Column(name = "categorias_json", columnDefinition = "TEXT")
    private String categoriasJson;

    @Column(name = "list_unsubscribe", columnDefinition = "TEXT")
    private String listUnsubscribe;

    @Column(name = "list_unsubscribe_post", length = 500)
    private String listUnsubscribePost;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    protected MGmailSuscripcion() { }

    public MGmailSuscripcion(String id, MFuente cuenta, String correoRemitente, Instant ultimaActividad) {
        this.id = id;
        this.cuenta = cuenta;
        this.correoRemitente = correoRemitente;
        this.ultimaActividad = ultimaActividad;
    }

    public String getId() { return id; }
    public MFuente getCuenta() { return cuenta; }
    public String getCorreoRemitente() { return correoRemitente; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoriasJson() { return categoriasJson; }
    public void setCategoriasJson(String categoriasJson) { this.categoriasJson = categoriasJson; }
    public String getListUnsubscribe() { return listUnsubscribe; }
    public void setListUnsubscribe(String listUnsubscribe) { this.listUnsubscribe = listUnsubscribe; }
    public String getListUnsubscribePost() { return listUnsubscribePost; }
    public void setListUnsubscribePost(String listUnsubscribePost) { this.listUnsubscribePost = listUnsubscribePost; }
    public Instant getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(Instant ultimaActividad) { this.ultimaActividad = ultimaActividad; }
}
