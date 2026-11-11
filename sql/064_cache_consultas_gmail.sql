-- Registra cuándo se actualizaron las copias locales de mensajes y suscripciones.
SET @finapp_schema = DATABASE();

SET @existe_cache_mensajes = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'gmail_mensajes_cache_en');
SET @sql_cache_mensajes = IF(@existe_cache_mensajes = 0,
  'ALTER TABLE fuente ADD COLUMN gmail_mensajes_cache_en DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt_cache_mensajes FROM @sql_cache_mensajes; EXECUTE stmt_cache_mensajes; DEALLOCATE PREPARE stmt_cache_mensajes;

SET @existe_clave_cache_mensajes = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'gmail_mensajes_cache_clave');
SET @sql_clave_cache_mensajes = IF(@existe_clave_cache_mensajes = 0,
  'ALTER TABLE fuente ADD COLUMN gmail_mensajes_cache_clave VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt_clave_cache_mensajes FROM @sql_clave_cache_mensajes; EXECUTE stmt_clave_cache_mensajes; DEALLOCATE PREPARE stmt_clave_cache_mensajes;

SET @existe_cache_suscripciones = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'gmail_suscripciones_cache_en');
SET @sql_cache_suscripciones = IF(@existe_cache_suscripciones = 0,
  'ALTER TABLE fuente ADD COLUMN gmail_suscripciones_cache_en DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt_cache_suscripciones FROM @sql_cache_suscripciones; EXECUTE stmt_cache_suscripciones; DEALLOCATE PREPARE stmt_cache_suscripciones;
