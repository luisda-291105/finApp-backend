package com.luisda.fin_app_back.Model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/**
 * @uso               Modelo de mensaje del asistente de un grupo
 * @capa              Modelo
 * @responsabilidades Mantener el rol, autenticidad y la hora de cada mensaje del chat de grupo
 * @datos             Identificador, grupo destinatario, usuario emisor, rol (user | assistant), texto y fecha
 * @dependencias      JPA
 * @usadoPor          IGrupoMensaje, SGrupoChat
 */
@Entity
@Table(name = "grupo_mensaje", indexes = @Index(name = "idx_grupo_mensaje_grupo_fecha", columnList = "id_grupo,fecha"))
public class MGrupoMensaje {
    @Id
    @Column(length = 36, nullable = false)
    private String idMensaje;

    @Column(length = 36, nullable = false)
    private String idGrupo;

    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 12, nullable = false)
    private String rol;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texto;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public MGrupoMensaje() {
    }

    public String getIdMensaje() { return idMensaje; }
    public void setIdMensaje(String idMensaje) { this.idMensaje = idMensaje; }
    public String getIdGrupo() { return idGrupo; }
    public void setIdGrupo(String idGrupo) { this.idGrupo = idGrupo; }
    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
