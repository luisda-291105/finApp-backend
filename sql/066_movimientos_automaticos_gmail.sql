ALTER TABLE gmail_mensaje
    ADD COLUMN movimiento_automatico_estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN movimiento_automatico_id VARCHAR(36) NULL,
    ADD COLUMN movimiento_alerta_pendiente BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_gmail_mensaje_movimiento_pendiente
    ON gmail_mensaje (id_fuente, movimiento_automatico_estado, fecha_interna);
