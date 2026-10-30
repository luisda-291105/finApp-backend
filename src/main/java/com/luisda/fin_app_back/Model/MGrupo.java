package com.luisda.fin_app_back.Model;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * @uso               Representa un grupo creado por un usuario
 * @capa              Modelo
 * @responsabilidades Mantener los datos, propietario y estado del grupo
 * @datos             Identificador, usuario creador, nombre, descripción, estado y fecha
 * @dependencias      JPA
 * @usadoPor          GrupoRepository
 */
@Entity
@Table(name = "grupo")
public class MGrupo {
    @Id
    @Column(length = 36 , nullable = false)
    private String idGrupo;

    @Column(length = 100 , nullable = false)
    private String nombre;
    @Column(length = 255 , nullable = false)
    private String descripcion;
    @Column(nullable = false)
    private boolean estado;
    @Column(nullable = false)
    private LocalDate fechaCreacion;

    // Relación entre clase MGrupo y MUsuario

    @ManyToOne(optional = false)
    @JoinColumn(name = "idUsuario", nullable = false)
    //@JoinManagedReference
    private MUsuario usuario;

    @ManyToMany
    @JoinTable(
            name = "grupo_fuente",
            joinColumns = @JoinColumn(name = "idGrupo"),
            inverseJoinColumns = @JoinColumn(name = "idFuente")
    )
    private List<MFuente> fuentes = new ArrayList<>();


    public MGrupo(String idGrupo, String nombre, String descripcion, boolean estado, LocalDate fechaCreacion, MUsuario MUsuario) {
        this.idGrupo = idGrupo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.usuario = MUsuario;
    }

    public MGrupo() {
    }

    public String getIdGrupo() {
        return idGrupo;
    }

    public void setIdGrupo(String idGrupo) {
        this.idGrupo = idGrupo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isEstado() {
        return estado;
    }

    public void setEstado(boolean estado) {
        this.estado = estado;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public MUsuario getUsuario() { return usuario; }

    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }

    public List<MFuente> getFuentes() { return fuentes; }

    public void setFuentes(List<MFuente> fuentes) {
        this.fuentes = fuentes == null ? new ArrayList<>() : new ArrayList<>(fuentes);
    }
}
