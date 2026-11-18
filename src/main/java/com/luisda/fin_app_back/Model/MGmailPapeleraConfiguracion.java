package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * @uso               Configuración de la regla automática de papelera por cuenta Gmail
 * @capa              Modelo
 * @responsabilidades Guardar la antigüedad mínima y el límite por ejecución de la regla (spec 040)
 * @datos             Cuenta, antigüedad mínima en días, límite por ejecución y fecha de actualización
 * @dependencias      MFuente
 * @usadoPor          IGmailPapeleraConfiguracion, servicios de la papelera
 */
@Entity
@Table(name = "gmail_papelera_configuracion")
public class MGmailPapeleraConfiguracion {

    /** Antigüedad mínima por defecto (días) antes de mover un correo por la regla automática. */
    public static final int ANTIGUEDAD_MINIMA_DIAS_POR_DEFECTO = 7;

    /** Límite por defecto de correos movidos en cada ejecución de la regla automática. */
    public static final int LIMITE_POR_EJECUCION_POR_DEFECTO = 200;

    @Id
    @Column(name = "id_fuente", length = 36, nullable = false)
    private String idFuente;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id_fuente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MFuente fuente;

    @Column(name = "antiguedad_minima_dias", nullable = false)
    private int antiguedadMinimaDias = ANTIGUEDAD_MINIMA_DIAS_POR_DEFECTO;

    @Column(name = "limite_por_ejecucion", nullable = false)
    private int limitePorEjecucion = LIMITE_POR_EJECUCION_POR_DEFECTO;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected MGmailPapeleraConfiguracion() { }

    public MGmailPapeleraConfiguracion(MFuente fuente) {
        this.fuente = fuente;
    }

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        this.actualizadoEn = Instant.now();
    }

    public String getIdFuente() { return idFuente; }
    public MFuente getFuente() { return fuente; }

    public int getAntiguedadMinimaDias() { return antiguedadMinimaDias; }
    public void setAntiguedadMinimaDias(int antiguedadMinimaDias) { this.antiguedadMinimaDias = antiguedadMinimaDias; }

    public int getLimitePorEjecucion() { return limitePorEjecucion; }
    public void setLimitePorEjecucion(int limitePorEjecucion) { this.limitePorEjecucion = limitePorEjecucion; }

    public Instant getActualizadoEn() { return actualizadoEn; }
}
