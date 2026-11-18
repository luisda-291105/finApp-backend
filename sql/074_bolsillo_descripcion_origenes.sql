-- 074: Se agregan los campos descriptivos y de orígenes opcionales para el bolsillo.
--
-- `descripcion`: texto libre para que el usuario explique el propósito del bolsillo.
-- `origenes`: JSON array con las cadenas que el usuario asocia como origen de fondeo
--              (p. ej. ["Bancolombia", "Claro Móvil"]). Acepta NULL.

ALTER TABLE bolsillo
  ADD COLUMN descripcion TEXT NULL,
  ADD COLUMN origenes TEXT NULL;
