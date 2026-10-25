package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
public class MUsuario {
    // Atributos
    @Id
    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(length = 150, nullable = false, unique = true)
    private String contacto;

    @Column(length = 255, nullable = false)
    private String contrasena;

    @Column(length = 255)
    private String propiedad;

    @Column(nullable = false)
    private LocalDate fechaCreacion;

    // Relaciones entre clase usuario y grupo
    /*
    @OneToMany(mappedBy = "usuario")
    @JsonManagedReference
    private List<MGrupo> grupo;
    */

    public MUsuario() {
    }

    public MUsuario(String idUsuario, String nombre, String contacto, String contrasena, String propiedad, LocalDate fechaCreacion) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.contacto = contacto;
        this.contrasena = contrasena;
        this.propiedad = propiedad;
        this.fechaCreacion = fechaCreacion;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getContacto() {
        return contacto;
    }

    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getPropiedad() {
        return propiedad;
    }

    public void setPropiedad(String propiedad) {
        this.propiedad = propiedad;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
