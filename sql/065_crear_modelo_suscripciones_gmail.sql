CREATE TABLE IF NOT EXISTS gmail_suscripcion (
    id VARCHAR(36) NOT NULL,
    id_fuente VARCHAR(36) NOT NULL,
    correo_remitente VARCHAR(254) NOT NULL,
    nombre VARCHAR(500) NULL,
    categorias_json TEXT NULL,
    list_unsubscribe TEXT NULL,
    list_unsubscribe_post VARCHAR(500) NULL,
    ultima_actividad DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gmail_suscripcion_cuenta_correo (id_fuente, correo_remitente),
    KEY idx_gmail_suscripcion_actividad (ultima_actividad),
    CONSTRAINT fk_gmail_suscripcion_fuente
        FOREIGN KEY (id_fuente) REFERENCES fuente (id_fuente)
        ON DELETE CASCADE
) ENGINE=InnoDB;
