-- Cola en espera de los correos entrantes de Gmail: un registro por mensaje
-- (o por notificación de llegada aún sin expandir). El worker la drena uno a uno.
CREATE TABLE IF NOT EXISTS gmail_entrada_pendiente (
    id VARCHAR(36) NOT NULL,
    id_fuente VARCHAR(36) NOT NULL,
    google_message_id VARCHAR(255) NULL,
    history_id VARCHAR(100) NULL,
    estado VARCHAR(20) NOT NULL,
    intentos INT NOT NULL,
    ultimo_error TEXT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_ultimo_intento DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gmail_entrada_fuente FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente),
    CONSTRAINT uk_gmail_entrada_mensaje UNIQUE (id_fuente, google_message_id)
);

CREATE INDEX idx_gmail_entrada_estado
    ON gmail_entrada_pendiente (estado, fecha_creacion);
