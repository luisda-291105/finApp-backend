-- Spec correo Gmail: reutiliza origen_tipo/origen_id/origen_nombre para los movimientos importados.
-- Se amplía origen_id para guardar la cuenta Gmail y el ID del mensaje en una referencia estable.
ALTER TABLE movimiento_contable
    MODIFY COLUMN origen_id VARCHAR(180) NULL;
