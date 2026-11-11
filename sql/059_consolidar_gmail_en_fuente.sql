-- Ejecutar antes del despliegue que elimina las entidades antiguas.
-- Es seguro si Hibernate ya agregó alguna columna al iniciar la versión nueva.
SET @finapp_schema = DATABASE();

SET @finapp_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'access_token_cifrado') = 0,
    'ALTER TABLE fuente ADD COLUMN access_token_cifrado TEXT NULL', 'SELECT 1'
);
PREPARE finapp_stmt FROM @finapp_ddl; EXECUTE finapp_stmt; DEALLOCATE PREPARE finapp_stmt;

SET @finapp_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'refresh_token_cifrado') = 0,
    'ALTER TABLE fuente ADD COLUMN refresh_token_cifrado TEXT NULL', 'SELECT 1'
);
PREPARE finapp_stmt FROM @finapp_ddl; EXECUTE finapp_stmt; DEALLOCATE PREPARE finapp_stmt;

SET @finapp_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'expira_en') = 0,
    'ALTER TABLE fuente ADD COLUMN expira_en DATETIME(6) NULL', 'SELECT 1'
);
PREPARE finapp_stmt FROM @finapp_ddl; EXECUTE finapp_stmt; DEALLOCATE PREPARE finapp_stmt;

START TRANSACTION;

INSERT INTO fuente (id_fuente, tipo, contenido, estado, fecha_creacion, id_usuario, access_token_cifrado, refresh_token_cifrado, expira_en)
SELECT UUID(), 'CORREO', c.email, TRUE, DATE(c.creada_en), c.usuario_id, NULL, c.refresh_token_cifrado, NULL
FROM cuenta_gmail c
LEFT JOIN fuente f ON f.id_usuario = c.usuario_id
    AND UPPER(f.tipo) = 'CORREO' AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(c.email))
WHERE f.id_fuente IS NULL;

INSERT INTO fuente (id_fuente, tipo, contenido, estado, fecha_creacion, id_usuario, access_token_cifrado, refresh_token_cifrado, expira_en)
SELECT UUID(), 'CORREO', g.correo_google, TRUE, CURRENT_DATE, g.id_usuario,
       g.access_token_cifrado, g.refresh_token_cifrado, g.expira_en
FROM gmail_conexion g
LEFT JOIN fuente f ON f.id_usuario = g.id_usuario
    AND UPPER(f.tipo) = 'CORREO' AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(g.correo_google))
WHERE f.id_fuente IS NULL;

UPDATE fuente f
JOIN cuenta_gmail c ON f.id_usuario = c.usuario_id
    AND UPPER(f.tipo) = 'CORREO' AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(c.email))
SET f.refresh_token_cifrado = c.refresh_token_cifrado;

UPDATE fuente f
JOIN gmail_conexion g ON f.id_usuario = g.id_usuario
    AND UPPER(f.tipo) = 'CORREO' AND LOWER(TRIM(f.contenido)) = LOWER(TRIM(g.correo_google))
SET f.access_token_cifrado = g.access_token_cifrado,
    f.refresh_token_cifrado = COALESCE(f.refresh_token_cifrado, g.refresh_token_cifrado),
    f.expira_en = g.expira_en;

COMMIT;

DROP TABLE cuenta_gmail;
DROP TABLE gmail_conexion;
