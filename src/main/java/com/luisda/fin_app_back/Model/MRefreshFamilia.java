package com.luisda.fin_app_back.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * @uso               Familia de refresh tokens creada en cada inicio de sesión
 * @capa              Modelo
 * @responsabilidades Guardar únicamente hashes del refresh vigente y del anterior para detectar reuso
 * @datos             familyId, usuario, hashes, marcas de tiempo, tope absoluto y versión de credenciales
 * @dependencias      JPA
 * @usadoPor          IRefreshFamilia, SRefreshFamilias
 */
@Entity
@Table(name = "refresh_familia", indexes = {
        @Index(name = "idx_refresh_familia_usuario", columnList = "id_usuario"),
        @Index(name = "idx_refresh_familia_vencimiento", columnList = "absolute_expires_at")
})
public class MRefreshFamilia {

    @Id
    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @Column(name = "id_usuario", length = 36, nullable = false)
    private String idUsuario;

    /** SHA-256 del refresh token vigente, en Base64. Nunca se guarda el token en claro. */
    @JsonIgnore
    @Column(name = "hash_actual", length = 64, nullable = false)
    private String hashActual;

    /** SHA-256 del refresh token inmediatamente anterior, en Base64. */
    @JsonIgnore
    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    /** Momento en que se emitió el refresh vigente (o el última rotación). */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Tope absoluto de la sesión; el refresh nunca lo extiende. */
    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion = 0L;

    public MRefreshFamilia() {
    }

    public MRefreshFamilia(String familyId, String idUsuario, String hashActual, Instant usedAt,
                           Instant createdAt, Instant absoluteExpiresAt, long credentialVersion) {
        this.familyId = familyId;
        this.idUsuario = idUsuario;
        this.hashActual = hashActual;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.credentialVersion = credentialVersion;
    }

    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }

    @JsonIgnore
    public String getHashActual() { return hashActual; }
    public void setHashActual(String hashActual) { this.hashActual = hashActual; }

    @JsonIgnore
    public String getHashAnterior() { return hashAnterior; }
    public void setHashAnterior(String hashAnterior) { this.hashAnterior = hashAnterior; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public void setAbsoluteExpiresAt(Instant absoluteExpiresAt) { this.absoluteExpiresAt = absoluteExpiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public long getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(long credentialVersion) { this.credentialVersion = credentialVersion; }

    public boolean estaRevocada() { return revokedAt != null; }
    public boolean vencida(Instant ahora) { return !ahora.isBefore(absoluteExpiresAt); }
}
