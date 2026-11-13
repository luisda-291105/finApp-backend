-- 070: Unificar el correo del usuario: eliminar correo_google y conservar solo correo.
-- Motivo: el registro con Google guardaba el correo en correo_google y el campo correo quedaba
-- nulo, impidiendo el inicio de sesión por correo. Ahora la cuenta Google usa `correo`.

-- UP: copia el correo de Google a `correo` en las cuentas creadas antes de este cambio,
-- evitando colisiones con usuarios que ya tengan ese correo registrado.
UPDATE usuario u
SET u.correo = u.correo_google
WHERE u.correo IS NULL
  AND u.correo_google IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM usuario x WHERE x.correo = u.correo_google) c);

-- Elimina la columna y su índice único (MySQL elimina el índice asociado con la columna).
ALTER TABLE usuario DROP COLUMN correo_google;

-- DOWN: restaura la columna y devuelve a correo_google el correo de las cuentas Google.
ALTER TABLE usuario
    ADD COLUMN correo_google VARCHAR(255) NULL UNIQUE AFTER google_subject;

UPDATE usuario
SET correo_google = correo
WHERE google_subject IS NOT NULL AND correo IS NOT NULL;
