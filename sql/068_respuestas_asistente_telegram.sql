SET @finapp_schema = DATABASE();

SET @existe_rol_telegram = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'telegram_mensaje' AND column_name = 'rol_asistente');
SET @sql_rol_telegram = IF(@existe_rol_telegram = 0,
  'ALTER TABLE telegram_mensaje ADD COLUMN rol_asistente VARCHAR(20) NOT NULL DEFAULT ''user''', 'SELECT 1');
PREPARE stmt_rol_telegram FROM @sql_rol_telegram; EXECUTE stmt_rol_telegram; DEALLOCATE PREPARE stmt_rol_telegram;
