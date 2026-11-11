-- Agrega cargo y empresa a la tabla contacto existente para sincronizar People API.
-- Puede ejecutarse más de una vez sin duplicar columnas.
SET @finapp_schema = DATABASE();

SET @existe_cargo = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'contacto' AND column_name = 'cargo');
SET @sql_cargo = IF(@existe_cargo = 0,
  'ALTER TABLE contacto ADD COLUMN cargo VARCHAR(150) NULL', 'SELECT 1');
PREPARE stmt_cargo FROM @sql_cargo; EXECUTE stmt_cargo; DEALLOCATE PREPARE stmt_cargo;

SET @existe_empresa = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'contacto' AND column_name = 'empresa');
SET @sql_empresa = IF(@existe_empresa = 0,
  'ALTER TABLE contacto ADD COLUMN empresa VARCHAR(150) NULL', 'SELECT 1');
PREPARE stmt_empresa FROM @sql_empresa; EXECUTE stmt_empresa; DEALLOCATE PREPARE stmt_empresa;
