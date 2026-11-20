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

/**
 * @uso               Cola en espera de los correos entrantes de Gmail, un registro por mensaje
 * @capa              Modelo
 * @responsabilidades Representar cada correo entrante (o notificación de llegada) pendiente de procesar
 * @datos             Cuenta de origen, id del mensaje de Google, historyId de la notificación, estado, intentos y error
 * @dependencias      MFuente
 * @usadoPor          SGmailEntradas, CGmail
 */
@Entity
@Table(name = "gmail_entrada_pendiente",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_fuente", "google_message_id"}),
        indexes = @Index(name = "idx_gmail_entrada_estado", columnList = "estado, fecha_creacion"))
public class MGmailEntradaPendiente {

    /** Estados de una entrada en la cola. */
    public static final String ESTADO_EN_ESPERA = "EN_ESPERA";
    public static final String ESTADO_PROCESANDO = "PROCESANDO";
    public static final String ESTADO_ERROR = "ERROR";

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private MFuente cuenta;

    /** Id del mensaje en Gmail; nulo cuando la entrada es una notificación de llegada sin expandir. */
    @Column(name = "google_message_id", length = 255)
    private String googleMessageId;

    /** historyId de la notificación Pub/Sub; solo para entradas tipo notificación. */
    @Column(name = "history_id", length = 100)
    private String historyId;

    @Column(length = 20, nullable = false)
    private String estado;

    @Column(nullable = false)
    private int intentos;

    @Column(columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_ultimo_intento")
    private Instant fechaUltimoIntento;

    public MGmailEntradaPendiente() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public MFuente getCuenta() { return cuenta; }
    public void setCuenta(MFuente cuenta) { this.cuenta = cuenta; }

    public String getGoogleMessageId() { return googleMessageId; }
    public void setGoogleMessageId(String googleMessageId) { this.googleMessageId = googleMessageId; }

    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Instant getFechaUltimoIntento() { return fechaUltimoIntento; }
    public void setFechaUltimoIntento(Instant fechaUltimoIntento) { this.fechaUltimoIntento = fechaUltimoIntento; }
}
