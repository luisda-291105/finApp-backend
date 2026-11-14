package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @uso               Suscripción de cobro periódico del usuario
 * @capa              Modelo
 * @responsabilidades Guardar en una sola tabla las suscripciones manuales y las detectadas en Gmail
 * @datos             Identificador, propietario, origen, nombre, categoría, monto, fecha de cobro,
 *                    importancia, estado, cuenta de correo, remitente, baja y fechas de control
 * @dependencias      JPA
 * @usadoPor          ISuscripcion
 */
@Entity
@Table(name = "suscripcion",
        uniqueConstraints = @UniqueConstraint(name = "uk_suscripcion_fuente_correo",
                columnNames = {"id_fuente", "correo_remitente"}),
        indexes = {
                @Index(name = "idx_suscripcion_usuario_origen", columnList = "id_usuario,origen"),
                @Index(name = "idx_suscripcion_actividad", columnList = "ultima_actividad")
        })
public class MSuscripcion {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private MUsuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrigenSuscripcion origen;

    @Column(length = 500)
    private String nombre;

    /** Categoría elegida por el usuario (suscripciones manuales). */
    @Column(length = 60)
    private String categoria;

    /** Etiquetas detectadas en el correo (suscripciones de Gmail). */
    @Column(name = "categorias_json", columnDefinition = "TEXT")
    private String categoriasJson;

    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_cobro")
    private LocalDate fechaCobro;

    @Column(length = 10)
    private String importancia;

    @Column(nullable = false)
    private boolean activa;

    /** Cuenta de correo desde la que se detectó la suscripción (solo Gmail). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fuente")
    private MFuente cuenta;

    @Column(name = "correo_remitente", length = 254)
    private String correoRemitente;

    @Column(name = "list_unsubscribe", columnDefinition = "TEXT")
    private String listUnsubscribe;

    @Column(name = "list_unsubscribe_post", length = 500)
    private String listUnsubscribePost;

    @Column(name = "ultima_actividad")
    private Instant ultimaActividad;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public MSuscripcion() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public MUsuario getUsuario() { return usuario; }
    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }

    public OrigenSuscripcion getOrigen() { return origen; }
    public void setOrigen(OrigenSuscripcion origen) { this.origen = origen; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getCategoriasJson() { return categoriasJson; }
    public void setCategoriasJson(String categoriasJson) { this.categoriasJson = categoriasJson; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDate getFechaCobro() { return fechaCobro; }
    public void setFechaCobro(LocalDate fechaCobro) { this.fechaCobro = fechaCobro; }

    public String getImportancia() { return importancia; }
    public void setImportancia(String importancia) { this.importancia = importancia; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public MFuente getCuenta() { return cuenta; }
    public void setCuenta(MFuente cuenta) { this.cuenta = cuenta; }

    public String getCorreoRemitente() { return correoRemitente; }
    public void setCorreoRemitente(String correoRemitente) { this.correoRemitente = correoRemitente; }

    public String getListUnsubscribe() { return listUnsubscribe; }
    public void setListUnsubscribe(String listUnsubscribe) { this.listUnsubscribe = listUnsubscribe; }

    public String getListUnsubscribePost() { return listUnsubscribePost; }
    public void setListUnsubscribePost(String listUnsubscribePost) { this.listUnsubscribePost = listUnsubscribePost; }

    public Instant getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(Instant ultimaActividad) { this.ultimaActividad = ultimaActividad; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
