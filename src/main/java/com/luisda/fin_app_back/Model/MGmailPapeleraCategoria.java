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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * @uso               Categoría de Gmail configurada para el movimiento automático a la papelera
 * @capa              Modelo
 * @responsabilidades Guardar si una categoría está activa, cuándo se activó y si se aceptó su advertencia
 * @datos             Cuenta, categoría, estado activo, instante de activación y aceptación de la advertencia
 * @dependencias      MFuente
 * @usadoPor          IGmailPapeleraCategoria, servicios de la papelera
 */
@Entity
@Table(name = "gmail_papelera_categoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmail_papelera_categoria_fuente",
                columnNames = {"id_fuente", "categoria"}),
        indexes = @Index(name = "idx_gmail_papelera_categoria_fuente", columnList = "id_fuente"))
public class MGmailPapeleraCategoria {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MFuente fuente;

    @Column(length = 30, nullable = false)
    private String categoria;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "activada_en")
    private Instant activadaEn;

    @Column(name = "advertencia_aceptada", nullable = false)
    private boolean advertenciaAceptada;

    protected MGmailPapeleraCategoria() { }

    public MGmailPapeleraCategoria(String id, MFuente fuente, String categoria) {
        this.id = id;
        this.fuente = fuente;
        this.categoria = categoria;
    }

    public String getId() { return id; }
    public MFuente getFuente() { return fuente; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Instant getActivadaEn() { return activadaEn; }
    public void setActivadaEn(Instant activadaEn) { this.activadaEn = activadaEn; }

    public boolean isAdvertenciaAceptada() { return advertenciaAceptada; }
    public void setAdvertenciaAceptada(boolean advertenciaAceptada) { this.advertenciaAceptada = advertenciaAceptada; }
}
