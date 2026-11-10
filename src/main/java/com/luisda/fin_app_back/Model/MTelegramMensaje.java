package com.luisda.fin_app_back.Model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "telegram_mensaje", indexes = @Index(name = "idx_telegram_mensaje_usuario_fecha", columnList = "id_usuario,fecha"))
public class MTelegramMensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private MUsuario usuario;

    @Column(nullable = false, length = 4096)
    private String texto;

    @Column(name = "grupo_nombre", length = 100)
    private String grupoNombre;

    @Column(name = "enviado_por_usuario", nullable = false)
    private boolean enviadoPorUsuario;

    @Column(name = "rol_asistente", nullable = false, length = 20)
    private String rolAsistente = "user";

    @Column(nullable = false)
    private Instant fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MUsuario getUsuario() { return usuario; }
    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getGrupoNombre() { return grupoNombre; }
    public void setGrupoNombre(String grupoNombre) { this.grupoNombre = grupoNombre; }
    public boolean isEnviadoPorUsuario() { return enviadoPorUsuario; }
    public void setEnviadoPorUsuario(boolean enviadoPorUsuario) { this.enviadoPorUsuario = enviadoPorUsuario; }
    public String getRolAsistente() { return rolAsistente; }
    public void setRolAsistente(String rolAsistente) { this.rolAsistente = rolAsistente; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
}
