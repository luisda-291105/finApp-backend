-- 071: Unificar las suscripciones manuales y de Gmail en una sola tabla `suscripcion`.
--
-- Antes: `gmail_suscripcion` guardaba lo detectado en los correos y las suscripciones
-- manuales no tenían almacenamiento propio.
-- Ahora: todo vive en `suscripcion` y la columna `origen` distingue cada registro:
--   MANUAL -> la registra el usuario en la aplicación (CRUD /suscripcion).
--   GMAIL  -> se detecta en los encabezados List-Unsubscribe de una cuenta conectada.
--
-- Ejecutar en MySQL después de desplegar la entidad MSuscripcion.

CREATE TABLE IF NOT EXISTS suscripcion (
    id VARCHAR(36) NOT NULL,
    id_usuario VARCHAR(36) NOT NULL,
    origen ENUM('MANUAL', 'GMAIL') NOT NULL,
    nombre VARCHAR(500) NULL,
    categoria VARCHAR(60) NULL,
    categorias_json TEXT NULL,
    monto DECIMAL(12,2) NULL,
    fecha_cobro DATE NULL,
    importancia VARCHAR(10) NULL,
    activa BIT(1) NOT NULL DEFAULT b'1',
    id_fuente VARCHAR(36) NULL,
    correo_remitente VARCHAR(254) NULL,
    list_unsubscribe TEXT NULL,
    list_unsubscribe_post VARCHAR(500) NULL,
    ultima_actividad DATETIME(6) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_suscripcion_fuente_correo (id_fuente, correo_remitente),
    KEY idx_suscripcion_usuario_origen (id_usuario, origen),
    KEY idx_suscripcion_actividad (ultima_actividad),
    CONSTRAINT fk_suscripcion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    CONSTRAINT fk_suscripcion_fuente FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Conserva las suscripciones detectadas en Gmail que todavía no estén en la tabla nueva.
-- INSERT IGNORE omite las que ya existen (misma cuenta y mismo remitente).
INSERT IGNORE INTO suscripcion (
    id, id_usuario, origen, nombre, categorias_json, list_unsubscribe, list_unsubscribe_post,
    activa, id_fuente, correo_remitente, ultima_actividad, fecha_creacion, fecha_actualizacion)
SELECT gs.id,
       f.id_usuario,
       'GMAIL',
       gs.nombre,
       gs.categorias_json,
       gs.list_unsubscribe,
       gs.list_unsubscribe_post,
       b'1',
       gs.id_fuente,
       gs.correo_remitente,
       gs.ultima_actividad,
       gs.ultima_actividad,
       gs.ultima_actividad
FROM gmail_suscripcion gs
JOIN fuente f ON f.id_fuente = gs.id_fuente;

-- Tablas obsoletas: todas las suscripciones viven ahora en `suscripcion`.
DROP TABLE IF EXISTS gmail_suscripcion;
DROP TABLE IF EXISTS suscripcion_manual;

-- DOWN: recrear la tabla antigua de Gmail (las suscripciones manuales no tenían tabla).
-- CREATE TABLE gmail_suscripcion (
--     id VARCHAR(36) NOT NULL,
--     id_fuente VARCHAR(36) NOT NULL,
--     correo_remitente VARCHAR(254) NOT NULL,
--     nombre VARCHAR(500) NULL,
--     categorias_json TEXT NULL,
--     list_unsubscribe TEXT NULL,
--     list_unsubscribe_post VARCHAR(500) NULL,
--     ultima_actividad DATETIME(6) NOT NULL,
--     PRIMARY KEY (id),
--     UNIQUE KEY uk_gmail_suscripcion_cuenta_correo (id_fuente, correo_remitente),
--     CONSTRAINT fk_gmail_suscripcion_fuente FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente) ON DELETE CASCADE
-- ) ENGINE=InnoDB;
--
-- INSERT INTO gmail_suscripcion (id, id_fuente, correo_remitente, nombre, categorias_json,
--     list_unsubscribe, list_unsubscribe_post, ultima_actividad)
-- SELECT id, id_fuente, correo_remitente, nombre, categorias_json, list_unsubscribe,
--     list_unsubscribe_post, ultima_actividad
-- FROM suscripcion WHERE origen = 'GMAIL';
