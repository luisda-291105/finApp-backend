package com.luisda.fin_app_back.Model;


import jakarta.persistence.*;

/**
 * @uso               Representa un contacto asociado a un usuario
 * @capa              Modelo
 * @responsabilidades Mantener los datos y el tipo del contacto
 * @datos             Identificador, usuario, nombre, teléfono, correo, tipo y notas
 * @dependencias      JPA
 * @usadoPor          ContactoRepository
 */
@Entity
@Table(name = "contacto")
public class MContacto {

    // Atributos
    @Id
    @Column(length = 36, nullable = false)
    private String idContacto;

    @Column(length = 36, nullable = false)
    private String idUsuario;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(length = 20, nullable = false)
    private String telefono;

    @Column(length = 500)
    private String notas;

    @ManyToOne
    @JoinColumn(name = "idUsuario")
    //@JoinManagedReference
    private MUsuario MUsuario;



    public MContacto(String idContacto, String idUsuario, String nombre, String telefono, String notas) {
        this.idContacto = idContacto;
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.telefono = telefono;
        this.notas = notas;
    }

    public MContacto() {
    }

    public String getIdContacto() {
        return idContacto;
    }

    public void setIdContacto(String idContacto) {
        this.idContacto = idContacto;
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

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }
}
