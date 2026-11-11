-- Los movimientos globales se vinculan al usuario y no a un bolsillo.
-- Ejecutar en MySQL después de desplegar la entidad que añade id_usuario.
ALTER TABLE movimiento_contable
    MODIFY COLUMN id_bolsillo VARCHAR(36) NULL;

-- Conserva la propiedad de los movimientos históricos para consultas y autorización.
UPDATE movimiento_contable m
JOIN bolsillo b ON b.id_bolsillo = m.id_bolsillo
SET m.id_usuario = b.id_usuario
WHERE m.id_usuario IS NULL;
