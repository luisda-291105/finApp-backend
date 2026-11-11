-- Persiste la cuenta Gmail desde la que se importó cada contacto.
-- Repetible para bases que ya ejecutaron migraciones anteriores.
SET @finapp_schema = DATABASE();

SET @existe_correo_origen = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'contacto' AND column_name = 'correo_origen');
SET @sql_correo_origen = IF(@existe_correo_origen = 0,
  'ALTER TABLE contacto ADD COLUMN correo_origen VARCHAR(254) NULL', 'SELECT 1');
PREPARE stmt_correo_origen FROM @sql_correo_origen; EXECUTE stmt_correo_origen; DEALLOCATE PREPARE stmt_correo_origen;
