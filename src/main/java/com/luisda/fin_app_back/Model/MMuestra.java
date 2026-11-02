package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "muestras", indexes = @Index(name = "idx_muestras_usuario", columnList = "usuario_id"))
public class MMuestra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "usuario_id", nullable = false, length = 36) private String usuarioId;
    @Column(nullable = false, length = 30) private String tipo;
    @Column(nullable = false, length = 160) private String titulo;
    @Column(nullable = false, columnDefinition = "TEXT") private String datos;
    @CreationTimestamp @Column(nullable = false, updatable = false) private LocalDateTime fechaCreacion;
    public MMuestra() {}
    public Long getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDatos() { return datos; }
    public void setDatos(String datos) { this.datos = datos; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
