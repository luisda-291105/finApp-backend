-- Habilita guardar correo y contactos de Google en la tabla contacto existente.
-- Las sentencias son repetibles y compatibles con ddl-auto=update.
SET @finapp_schema = DATABASE();

SET @existe_correo = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'contacto' AND column_name = 'correo');
SET @sql_correo = IF(@existe_correo = 0,
  'ALTER TABLE contacto ADD COLUMN correo VARCHAR(254) NULL', 'SELECT 1');
PREPARE stmt_correo FROM @sql_correo; EXECUTE stmt_correo; DEALLOCATE PREPARE stmt_correo;

SET @sql_telefono = 'ALTER TABLE contacto MODIFY COLUMN telefono VARCHAR(64) NULL';
PREPARE stmt_telefono FROM @sql_telefono; EXECUTE stmt_telefono; DEALLOCATE PREPARE stmt_telefono;
