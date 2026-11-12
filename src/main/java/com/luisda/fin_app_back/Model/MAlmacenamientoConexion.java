package com.luisda.fin_app_back.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * @uso               Representa una cuenta de almacenamiento en la nube conectada por un usuario
 * @capa              Modelo
 * @responsabilidades Mantener proveedor, metadatos de cuenta, estado y material cifrado de la conexión
 * @datos             Identificadores, estado, fechas y credenciales cifradas
 * @dependencias      JPA
 * @usadoPor          Repositorio y servicios de almacenamiento en la nube
 */
@Entity
@Table(name = "almacenamiento_conexion")
public class MAlmacenamientoConexion {

    @Id
    @Column(length = 36, nullable = false)
    private String idConexion;

    @Column(length = 40, nullable = false)
    private String proveedor;

    @Column(name = "identificador_externo", length = 255)
    private String identificadorExterno;

    @Column(name = "etiqueta_cuenta", length = 255)
    private String etiquetaCuenta;

    @Column(name = "url_servidor", length = 500)
    private String urlServidor;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "conectado_en", nullable = false)
    private Instant conectadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @ManyToOne(optional = false)
    @JoinColumn(name = "idUsuario", nullable = false)
    private MUsuario usuario;

    @JsonIgnore
    @Column(name = "access_token_cifrado", columnDefinition = "TEXT")
    private String accessTokenCifrado;

    @JsonIgnore
    @Column(name = "refresh_token_cifrado", columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @JsonIgnore
    @Column(name = "credencial_cifrada", columnDefinition = "TEXT")
    private String credencialCifrada;

    @JsonIgnore
    @Column(name = "expira_en")
    private Instant expiraEn;

    public MAlmacenamientoConexion() {
    }

    public MAlmacenamientoConexion(String idConexion, MUsuario usuario, String proveedor) {
        this.idConexion = idConexion;
        this.usuario = usuario;
        this.proveedor = proveedor;
        this.estado = "ACTIVA";
        this.conectadoEn = Instant.now();
        this.actualizadoEn = this.conectadoEn;
    }

    public String getIdConexion() { return idConexion; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public String getIdentificadorExterno() { return identificadorExterno; }
    public void setIdentificadorExterno(String identificadorExterno) { this.identificadorExterno = identificadorExterno; }
    public String getEtiquetaCuenta() { return etiquetaCuenta; }
    public void setEtiquetaCuenta(String etiquetaCuenta) { this.etiquetaCuenta = etiquetaCuenta; }
    public String getUrlServidor() { return urlServidor; }
    public void setUrlServidor(String urlServidor) { this.urlServidor = urlServidor; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getConectadoEn() { return conectadoEn; }
    public void setConectadoEn(Instant conectadoEn) { this.conectadoEn = conectadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public MUsuario getUsuario() { return usuario; }
    public void setUsuario(MUsuario usuario) { this.usuario = usuario; }
    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }
    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }
    public String getCredencialCifrada() { return credencialCifrada; }
    public void setCredencialCifrada(String credencialCifrada) { this.credencialCifrada = credencialCifrada; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }

    /** Indica si la conexión conserva material renovable o credencial vigente. */
    @JsonIgnore
    public boolean renovable() {
        if ("NEXTCLOUD".equalsIgnoreCase(proveedor)) {
            return urlServidor != null && !urlServidor.isBlank()
                    && credencialCifrada != null && !credencialCifrada.isBlank();
        }
        boolean refreshTokenPresente = refreshTokenCifrado != null && !refreshTokenCifrado.isBlank();
        boolean accesoVigente = accessTokenCifrado != null && !accessTokenCifrado.isBlank()
                && expiraEn != null && expiraEn.isAfter(Instant.now());
        return refreshTokenPresente || accesoVigente;
    }
}
