package com.luisda.fin_app_back.DTO;

/**
 * Ajustes de organización que el usuario puede editar en cualquier suscripción,
 * sea manual o detectada en Gmail: importancia, prioridad y apodo.
 * Cada campo es opcional; omitirlo (null) conserva el valor actual.
 * `importancia` y `prioridad` aceptan "Alta", "Media" o "Baja" (sin distinguir mayúsculas).
 * `apodo` vacío limpia el alias guardado.
 */
public record SuscripcionAjustesDTO(
        String importancia,
        String prioridad,
        String apodo
) { }
