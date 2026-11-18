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
 * @uso               Remitente de la lista blanca que nunca se mueve por la regla automática
 * @capa              Modelo
 * @responsabilidades Guardar el remitente exento por cuenta Gmail
 * @datos             Cuenta, remitente y fecha de creación
 * @dependencias      MFuente
 * @usadoPor          IGmailPapeleraListaBlanca, servicios de la papelera
 */
@Entity
@Table(name = "gmail_papelera_lista_blanca",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmail_papelera_lista_blanca_fuente",
                columnNames = {"id_fuente", "remitente"}),
        indexes = @Index(name = "idx_gmail_papelera_lista_blanca_fuente", columnList = "id_fuente"))
public class MGmailPapeleraListaBlanca {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MFuente fuente;

    @Column(length = 320, nullable = false)
    private String remitente;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected MGmailPapeleraListaBlanca() { }

    public MGmailPapeleraListaBlanca(String id, MFuente fuente, String remitente) {
        this.id = id;
        this.fuente = fuente;
        this.remitente = remitente;
        this.creadoEn = Instant.now();
    }

    public String getId() { return id; }
    public MFuente getFuente() { return fuente; }

    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }

    public Instant getCreadoEn() { return creadoEn; }
}
