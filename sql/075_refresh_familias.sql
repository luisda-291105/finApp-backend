-- 075: Familias de refresh tokens + control de credenciales del usuario.
--
-- Una familia se crea en cada /usuario/login. Solo se guardan hashes SHA-256 del refresh
-- vigente y del anterior; nunca el token en claro. El tope absoluto no se extiende al rotar.
--
-- Ejecutar en MySQL tras desplegar las entidades MRefreshFamilia y MUsuario.

CREATE TABLE IF NOT EXISTS refresh_familia (
    family_id VARCHAR(36) NOT NULL,
    id_usuario VARCHAR(36) NOT NULL,
    hash_actual VARCHAR(64) NOT NULL,
    hash_anterior VARCHAR(64) NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    absolute_expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    credential_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (family_id),
    CONSTRAINT fk_refresh_familia_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    INDEX idx_refresh_familia_usuario (id_usuario),
    INDEX idx_refresh_familia_vencimiento (absolute_expires_at)
);

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'activo') = 0,
    'ALTER TABLE usuario ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'credential_version') = 0,
    'ALTER TABLE usuario ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Hibernate pudo crear `activo` sin DEFAULT y dejar a los usuarios existentes en 0.
-- Se activan todos: el estado real de `activo` se gestiona desde SUsuario.
UPDATE usuario SET activo = 1;
UPDATE usuario SET credential_version = 0 WHERE credential_version IS NULL;

-- DOWN (aproximado): DROP TABLE refresh_familia; ALTER TABLE usuario DROP COLUMN credential_version; ALTER TABLE usuario DROP COLUMN activo;
