-- Spec 057: enmascara PAN existentes y elimina CVV almacenados.
-- Antes de ejecutar: hacer un respaldo cifrado de la tabla y verificar su restauración.
-- Este saneamiento es irreversible: el PAN completo y los CVV se sustituyen por datos no sensibles.
START TRANSACTION;

UPDATE tarjetas
SET numero = CONCAT('**** ', RIGHT(REPLACE(REPLACE(numero, ' ', ''), '-', ''), 4)),
    cvv = ''
WHERE numero IS NOT NULL;

COMMIT;

-- Verificación posterior: todos los PAN deben tener solo máscara y cuatro dígitos finales;
-- todos los CVV quedan vacíos. No seleccionar ni imprimir los PAN/CVV originales.
SELECT COUNT(*) AS tarjetas,
       SUM(numero NOT REGEXP '^\\*{4} [0-9]{4}$') AS numeros_sin_enmascarar,
       SUM(cvv <> '') AS cvv_no_vacios
FROM tarjetas;
