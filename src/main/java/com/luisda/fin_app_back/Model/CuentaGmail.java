package com.luisda.fin_app_back.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Una cuenta Gmail autorizada por un usuario de FinApp. */
@Entity
@Table(name = "cuenta_gmail", uniqueConstraints =
        @UniqueConstraint(name = "uk_cuenta_gmail_usuario_email", columnNames = {"usuario_id", "email"}))
public class CuentaGmail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, length = 36)
    private String usuarioId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "refresh_token_cifrado", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    public Long getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }
    public Instant getCreadaEn() { return creadaEn; }
}
