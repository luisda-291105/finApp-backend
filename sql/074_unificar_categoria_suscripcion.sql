-- 074: Dejar una sola columna de categoría en `suscripcion`.
--
-- Antes: `categoria` guardaba la categoría de las suscripciones manuales y
-- `categorias_json` guardaba las etiquetas detectadas en Gmail.
-- Ahora: todo vive en `categoria` (texto plano en las manuales y JSON en las de Gmail).
--
-- Ejecutar en MySQL después de desplegar la entidad MSuscripcion.

-- La columna debe admitir la lista JSON de etiquetas de Gmail.
ALTER TABLE suscripcion MODIFY COLUMN categoria TEXT NULL;

-- Traslada todo lo guardado en `categorias_json` a `categoria`.
-- Las suscripciones manuales conservan su valor porque no tienen `categorias_json`.
UPDATE suscripcion
SET categoria = categorias_json
WHERE categorias_json IS NOT NULL AND categorias_json <> '';

ALTER TABLE suscripcion DROP COLUMN categorias_json;

-- DOWN (aproximado): recrear la columna de etiquetas y devolver los valores de Gmail.
-- ALTER TABLE suscripcion ADD COLUMN categorias_json TEXT NULL;
-- UPDATE suscripcion SET categorias_json = categoria
--  WHERE origen = 'GMAIL' AND categoria IS NOT NULL AND categoria <> '';
-- UPDATE suscripcion SET categoria = NULL
--  WHERE origen = 'GMAIL' AND categoria IS NOT NULL AND categoria <> '';
-- ALTER TABLE suscripcion MODIFY COLUMN categoria VARCHAR(60) NULL;
