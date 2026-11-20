package com.luisda.fin_app_back.Model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/**
 * @uso               Modelo de pertenencia de un usuario a un grupo
 * @capa              Modelo
 * @responsabilidades Mantener rol y fecha de cada integrante; el propietario lleva rol «DUENO»
 * @datos             Identificador, grupo, usuario, rol y fecha de ingreso
 * @dependencias      JPA
 * @usadoPor          IGrupoMiembro, SGrupoChat, SGrupo
 */
@Entity
@Table(name = "grupo_miembro",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_grupo", "id_usuario"}),
        indexes = @Index(name = "idx_grupo_miembro_grupo", columnList = "id_grupo"))
public class MGrupoMiembro {
    @Id
    @Column(length = 36, nullable = false)
    private String idMiembro;

    @Column(length = 36, nullable = false)
    private String idGrupo;

    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 12, nullable = false)
    private String rol;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public MGrupoMiembro() {
    }

    public String getIdMiembro() { return idMiembro; }
    public void setIdMiembro(String idMiembro) { this.idMiembro = idMiembro; }
    public String getIdGrupo() { return idGrupo; }
    public void setIdGrupo(String idGrupo) { this.idGrupo = idGrupo; }
    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
