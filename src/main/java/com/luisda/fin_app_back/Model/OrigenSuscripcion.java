package com.luisda.fin_app_back.Model;

/**
 * Procedencia de una suscripción.
 * MANUAL: la registra el usuario en la aplicación.
 * GMAIL: se detecta en los encabezados List-Unsubscribe de los correos de una cuenta conectada.
 */
public enum OrigenSuscripcion {
    MANUAL,
    GMAIL
}
