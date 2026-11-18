-- 075: Papelera de correos (spec 040).
--
-- Añade el estado de papelera por mensaje, el permiso (scope) concedido por cuenta y las tablas
-- de configuración de la regla automática, categorías activas y lista blanca de remitentes.
--
-- Idempotente: cada columna se guarda con `information_schema` y las tablas usan
-- `CREATE TABLE IF NOT EXISTS`, por lo que el script puede aplicarse varias veces sin error.
--
-- Compatibilidad y migración (CD-03): solo añade columnas y tablas nuevas; no altera ni elimina
-- columnas existentes y no borra datos. Los correos y cuentas previos siguen cargando con los
-- valores por defecto: categorías desactivadas, antigüedad mínima 7 días, límite 200 por
-- ejecución, advertencia sin aceptar, estado de papelera vacío y sin permiso ampliado.
--
-- Nota: `actualizado_en` de la configuración lo rellena la aplicación en cada alta o cambio; el
-- índice `id_fuente` de `gmail_papelera_configuracion` queda cubierto por su clave primaria.

SET @finapp_schema = DATABASE();

-- --- gmail_mensaje: estado de papelera por mensaje ---
SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'papelera_estado');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN papelera_estado VARCHAR(20) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'papelera_movido_automaticamente');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN papelera_movido_automaticamente BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'papelera_motivo');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN papelera_motivo VARCHAR(30) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'papelera_categoria');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN papelera_categoria VARCHAR(30) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'alerta_movimiento_descartada');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN alerta_movimiento_descartada BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_mensaje' AND column_name = 'papelera_exenta');
SET @sql = IF(@existe = 0, 'ALTER TABLE gmail_mensaje ADD COLUMN papelera_exenta BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --- fuente: permiso (scope) realmente concedido por cuenta ---
SET @existe = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @finapp_schema AND table_name = 'fuente' AND column_name = 'gmail_scope_concedido');
SET @sql = IF(@existe = 0, 'ALTER TABLE fuente ADD COLUMN gmail_scope_concedido VARCHAR(1000) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --- gmail_papelera_configuracion: antigüedad mínima y límite por ejecución por cuenta ---
CREATE TABLE IF NOT EXISTS gmail_papelera_configuracion (
    id_fuente VARCHAR(36) NOT NULL,
    antiguedad_minima_dias INT NOT NULL DEFAULT 7,
    limite_por_ejecucion INT NOT NULL DEFAULT 200,
    actualizado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id_fuente),
    CONSTRAINT fk_gmail_papelera_configuracion_fuente
        FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- --- gmail_papelera_categoria: categorías activas por cuenta ---
CREATE TABLE IF NOT EXISTS gmail_papelera_categoria (
    id VARCHAR(36) NOT NULL,
    id_fuente VARCHAR(36) NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT FALSE,
    activada_en DATETIME(6) NULL,
    advertencia_aceptada BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_gmail_papelera_categoria_fuente UNIQUE (id_fuente, categoria),
    CONSTRAINT fk_gmail_papelera_categoria_fuente
        FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- --- gmail_papelera_lista_blanca: remitentes exentos por cuenta ---
CREATE TABLE IF NOT EXISTS gmail_papelera_lista_blanca (
    id VARCHAR(36) NOT NULL,
    id_fuente VARCHAR(36) NOT NULL,
    remitente VARCHAR(320) NOT NULL,
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gmail_papelera_lista_blanca_fuente UNIQUE (id_fuente, remitente),
    CONSTRAINT fk_gmail_papelera_lista_blanca_fuente
        FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- --- índices por id_fuente (el de configuración ya lo cubre la clave primaria) ---
SET @existe = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_papelera_categoria' AND index_name = 'idx_gmail_papelera_categoria_fuente');
SET @sql = IF(@existe = 0, 'CREATE INDEX idx_gmail_papelera_categoria_fuente ON gmail_papelera_categoria (id_fuente)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe = (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @finapp_schema AND table_name = 'gmail_papelera_lista_blanca' AND index_name = 'idx_gmail_papelera_lista_blanca_fuente');
SET @sql = IF(@existe = 0, 'CREATE INDEX idx_gmail_papelera_lista_blanca_fuente ON gmail_papelera_lista_blanca (id_fuente)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
