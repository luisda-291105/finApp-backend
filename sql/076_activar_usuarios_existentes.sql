-- Corrección única para bases donde Hibernate creó `usuario.activo` sin valor por
-- defecto y dejó a los usuarios existentes en 0, provocando 401 en /usuario/login.
-- La migración 075 ya corregida aplica lo mismo en instalaciones nuevas.
UPDATE usuario SET activo = 1 WHERE activo = 0 OR activo IS NULL;
