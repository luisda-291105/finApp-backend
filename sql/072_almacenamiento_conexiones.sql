-- 072: Migración de la tabla de conexiones de almacenamiento (B1, spec de almacenamiento).
--
-- Alinea `almacenamiento_conexion` con la entidad MAlmacenamientoConexion después de la
-- capa de proveedor de archivos (Drive). Ejecutar en MySQL tras desplegar el backend.
--
-- Notas de seguridad:
--   * access_token_cifrado / refresh_token_cifrado / credencial_cifrada solo contienen
--     material cifrado con FINAPP_ALMACENAMIENTO_CIPHER_SECRET (ya no se deriva del JWT).
--   * Las conexiones cuyo material esté cifrado con la clave antigua quedan en estado
--     REQUIERE_RECONEXION: el usuario debe autorizar de nuevo en Configuración.

CREATE TABLE IF NOT EXISTS almacenamiento_conexion (
    id_conexion VARCHAR(36) NOT NULL,
    proveedor VARCHAR(40) NOT NULL,
    identificador_externo VARCHAR(255) NULL,
    etiqueta_cuenta VARCHAR(255) NULL,
    url_servidor VARCHAR(500) NULL,
    estado VARCHAR(30) NOT NULL,
    conectado_en DATETIME(6) NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    idUsuario VARCHAR(36) NOT NULL,
    access_token_cifrado TEXT NULL,
    refresh_token_cifrado TEXT NULL,
    credencial_cifrada TEXT NULL,
    expira_en DATETIME(6) NULL,
    PRIMARY KEY (id_conexion),
    KEY ix_almacenamiento_conexion_usuario (idUsuario),
    CONSTRAINT fk_almacenamiento_conexion_usuario FOREIGN KEY (idUsuario) REFERENCES usuario (idUsuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Columnas para despliegues existentes (ddl-auto=update ya las añade; es idempotente).
ALTER TABLE almacenamiento_conexion
    ADD COLUMN IF NOT EXISTS refresh_token_cifrado TEXT NULL;

-- Estado efectivo: las conexiones OAuth sin token renovable no pueden operar archivos.
-- Las conexiones NEXTCLOUD se autentican con credencial de aplicación (sin refresh_token),
-- por lo que quedan fuera de esta corrección.
UPDATE almacenamiento_conexion
SET estado = 'ACTIVA'
WHERE proveedor = 'NEXTCLOUD'
  AND estado = 'REQUIERE_RECONEXION'
  AND credencial_cifrada IS NOT NULL
  AND credencial_cifrada != '';

UPDATE almacenamiento_conexion
SET estado = 'REQUIERE_RECONEXION'
WHERE proveedor != 'NEXTCLOUD'
  AND estado = 'ACTIVA'
  AND (refresh_token_cifrado IS NULL OR refresh_token_cifrado = '');
