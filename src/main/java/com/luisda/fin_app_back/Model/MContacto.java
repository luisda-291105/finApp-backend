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
    @Column(name = "id_contacto", length = 36, nullable = false)
    private String idContacto;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "telefono", length = 64)
    private String telefono;

    @Column(name = "correo", length = 254)
    private String correo;

    @Column(name = "origen", length = 255)
    private String origen;

    @Column(name = "nota", length = 500)
    private String notas;

    @Column(name = "cargo", length = 150)
    private String cargo;

    @Column(name = "empresa", length = 150)
    private String empresa;

    @Column(name = "correo_origen", length = 254)
    private String correoOrigen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    //@JoinManagedReference
    private MUsuario usuario;


    public MContacto(String idContacto, String nombre, String telefono, String notas) {
        this.idContacto = idContacto;
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

    public String getCorreo() { return correo; }

    public void setCorreo(String correo) { this.correo = correo; }

    public String getOrigen() { return origen; }

    public void setOrigen(String origen) { this.origen = origen; }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public String getCargo() { return cargo; }

    public void setCargo(String cargo) { this.cargo = cargo; }

    public String getEmpresa() { return empresa; }

    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getCorreoOrigen() { return correoOrigen; }

    public void setCorreoOrigen(String correoOrigen) { this.correoOrigen = correoOrigen; }

    public MUsuario getUsuario() { return usuario; }

    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }
}
