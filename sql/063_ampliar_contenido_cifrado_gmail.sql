-- Permite guardar cuerpos completos de correo cifrados en base64.
ALTER TABLE gmail_mensaje
    MODIFY COLUMN contenido_cifrado LONGTEXT NULL;
