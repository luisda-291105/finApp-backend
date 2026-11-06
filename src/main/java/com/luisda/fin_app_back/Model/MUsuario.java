package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * @uso               Representa un usuario de la aplicación
 * @capa              Modelo
 * @responsabilidades Mantener los datos y la fecha de creación del usuario
 * @datos             Identificador, nombre, contacto, contraseña, propiedad y fecha
 * @dependencias      JPA
 * @usadoPor          UsuarioRepository
 */
@Entity
@Table(name = "usuario")
public class MUsuario implements Persistable<String> {
    // Atributos
    @Id
    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(name = "correo", unique = true, length = 255)
    private String correo;

    @Column(name = "telefono", unique = true, length = 30)
    private String telefono;

    @Column(length = 255, nullable = false)
    private String contrasena;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "correo_google", unique = true, length = 255)
    private String correoGoogle;

    @Transient
    private boolean nuevo = true;

    public MUsuario() {
    }

    public MUsuario(String idUsuario, String nombre, String contrasena, LocalDate fechaCreacion) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.contrasena = contrasena;
        this.fechaCreacion = fechaCreacion;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    @Override
    @JsonIgnore
    public String getId() { return idUsuario; }

    @Override
    @JsonIgnore
    public boolean isNew() { return nuevo; }

    @PostLoad
    @PostPersist
    private void marcarPersistido() { nuevo = false; }

    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getCorreo() { return correo; }

    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }

    public void setTelefono(String telefono) { this.telefono = telefono; }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    @JsonIgnore
    public String getGoogleSubject() { return googleSubject; }

    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }

    @JsonIgnore
    public String getCorreoGoogle() { return correoGoogle; }

    public void setCorreoGoogle(String correoGoogle) { this.correoGoogle = correoGoogle; }
}
