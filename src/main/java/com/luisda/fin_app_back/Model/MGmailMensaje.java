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
@Table(name = "gmail_mensaje",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_fuente", "google_message_id"}),
        indexes = @Index(name = "idx_gmail_mensaje_fecha", columnList = "fecha_interna"))
public class MGmailMensaje {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fuente", nullable = false)
    private MFuente cuenta;

    @Column(name = "google_message_id", length = 255, nullable = false)
    private String googleMessageId;

    @Column(length = 1000)
    private String remitente;

    @Column(columnDefinition = "TEXT")
    private String destinatario;

    @Column(length = 998)
    private String asunto;

    @Column(length = 1000)
    private String fragmento;

    @Column(name = "etiquetas_json", columnDefinition = "TEXT")
    private String etiquetasJson;

    @Column(name = "nombres_etiquetas_json", columnDefinition = "TEXT")
    private String nombresEtiquetasJson;

    @Column(name = "list_unsubscribe", columnDefinition = "TEXT")
    private String listUnsubscribe;

    @Column(name = "list_unsubscribe_post", length = 500)
    private String listUnsubscribePost;

    @Column(name = "contenido_cifrado", columnDefinition = "LONGTEXT")
    private String contenidoCifrado;

    @Column(name = "fecha_interna", nullable = false)
    private Instant fechaInterna;

    @Column(name = "movimiento_automatico_estado", nullable = false, length = 30)
    private String movimientoAutomaticoEstado = "PENDIENTE";

    @Column(name = "movimiento_automatico_id", length = 36)
    private String movimientoAutomaticoId;

    @Column(name = "movimiento_alerta_pendiente", nullable = false)
    private boolean movimientoAlertaPendiente;

    protected MGmailMensaje() { }

    public MGmailMensaje(String id, MFuente cuenta, String googleMessageId, Instant fechaInterna) {
        this.id = id;
        this.cuenta = cuenta;
        this.googleMessageId = googleMessageId;
        this.fechaInterna = fechaInterna;
    }

    public String getId() { return id; }
    public MFuente getCuenta() { return cuenta; }
    public String getGoogleMessageId() { return googleMessageId; }
    public String getRemitente() { return remitente; }
    public void setRemitente(String remitente) { this.remitente = remitente; }
    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getFragmento() { return fragmento; }
    public void setFragmento(String fragmento) { this.fragmento = fragmento; }
    public String getEtiquetasJson() { return etiquetasJson; }
    public void setEtiquetasJson(String etiquetasJson) { this.etiquetasJson = etiquetasJson; }
    public String getNombresEtiquetasJson() { return nombresEtiquetasJson; }
    public void setNombresEtiquetasJson(String nombresEtiquetasJson) { this.nombresEtiquetasJson = nombresEtiquetasJson; }
    public String getListUnsubscribe() { return listUnsubscribe; }
    public void setListUnsubscribe(String listUnsubscribe) { this.listUnsubscribe = listUnsubscribe; }
    public String getListUnsubscribePost() { return listUnsubscribePost; }
    public void setListUnsubscribePost(String listUnsubscribePost) { this.listUnsubscribePost = listUnsubscribePost; }
    public String getContenidoCifrado() { return contenidoCifrado; }
    public void setContenidoCifrado(String contenidoCifrado) { this.contenidoCifrado = contenidoCifrado; }
    public Instant getFechaInterna() { return fechaInterna; }
    public void setFechaInterna(Instant fechaInterna) { this.fechaInterna = fechaInterna; }
    public String getMovimientoAutomaticoEstado() { return movimientoAutomaticoEstado; }
    public void setMovimientoAutomaticoEstado(String movimientoAutomaticoEstado) { this.movimientoAutomaticoEstado = movimientoAutomaticoEstado; }
    public String getMovimientoAutomaticoId() { return movimientoAutomaticoId; }
    public void setMovimientoAutomaticoId(String movimientoAutomaticoId) { this.movimientoAutomaticoId = movimientoAutomaticoId; }
    public boolean isMovimientoAlertaPendiente() { return movimientoAlertaPendiente; }
    public void setMovimientoAlertaPendiente(boolean movimientoAlertaPendiente) { this.movimientoAlertaPendiente = movimientoAlertaPendiente; }
}
