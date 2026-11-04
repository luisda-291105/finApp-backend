package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Credenciales Gmail cifradas asociadas al usuario activo de FinApp. */
@Entity
@Table(name = "gmail_conexion")
public class MGmailConexion {
    @Id
    @Column(name = "id_usuario", length = 36, nullable = false)
    private String idUsuario;

    @Column(name = "correo_google", length = 255, nullable = false)
    private String correoGoogle;

    @Column(name = "access_token_cifrado", columnDefinition = "TEXT", nullable = false)
    private String accessTokenCifrado;

    @Column(name = "refresh_token_cifrado", columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }
    public String getCorreoGoogle() { return correoGoogle; }
    public void setCorreoGoogle(String correoGoogle) { this.correoGoogle = correoGoogle; }
    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }
    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
}
