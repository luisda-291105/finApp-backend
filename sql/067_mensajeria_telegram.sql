SET @finapp_schema = DATABASE();

SET @existe_telegram_chat_id = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'usuario' AND column_name = 'telegram_chat_id');
SET @sql_telegram_chat_id = IF(@existe_telegram_chat_id = 0,
  'ALTER TABLE usuario ADD COLUMN telegram_chat_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt_telegram_chat_id FROM @sql_telegram_chat_id; EXECUTE stmt_telegram_chat_id; DEALLOCATE PREPARE stmt_telegram_chat_id;

SET @existe_telegram_username = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'usuario' AND column_name = 'telegram_username');
SET @sql_telegram_username = IF(@existe_telegram_username = 0,
  'ALTER TABLE usuario ADD COLUMN telegram_username VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt_telegram_username FROM @sql_telegram_username; EXECUTE stmt_telegram_username; DEALLOCATE PREPARE stmt_telegram_username;

SET @existe_indice_telegram_chat = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @finapp_schema AND table_name = 'usuario' AND index_name = 'uk_usuario_telegram_chat_id');
SET @sql_indice_telegram_chat = IF(@existe_indice_telegram_chat = 0,
  'CREATE UNIQUE INDEX uk_usuario_telegram_chat_id ON usuario (telegram_chat_id)', 'SELECT 1');
PREPARE stmt_indice_telegram_chat FROM @sql_indice_telegram_chat; EXECUTE stmt_indice_telegram_chat; DEALLOCATE PREPARE stmt_indice_telegram_chat;

CREATE TABLE IF NOT EXISTS telegram_mensaje (
  id BIGINT NOT NULL AUTO_INCREMENT,
  id_usuario VARCHAR(36) NOT NULL,
  texto VARCHAR(4096) NOT NULL,
  grupo_nombre VARCHAR(100) NULL,
  enviado_por_usuario BOOLEAN NOT NULL,
  rol_asistente VARCHAR(20) NOT NULL DEFAULT 'user',
  fecha TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_telegram_mensaje_usuario_fecha (id_usuario, fecha),
  CONSTRAINT fk_telegram_mensaje_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE
);
