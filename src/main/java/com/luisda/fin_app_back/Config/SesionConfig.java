package com.luisda.fin_app_back.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @uso               Fuente única de los tiempos de la sesión (access, refresh, tope y gracia)
 * @capa              Configuración
 * @responsabilidades Centralizar los cuatro valores de seguridad para que no haya constandes duplicadas
 * @datos             Propiedades `finapp.sesion.*`
 * @dependencias      Spring @Value
 * @usadoPor          JwtTokenService, SRefreshFamilias, CUsuario
 */
@Component
public class SesionConfig {

    /** Access token: 15 minutos por defecto. */
    @Value("${finapp.sesion.access-seconds:900}")
    private long accessSeconds;

    /** Ventana de inactividad del refresh token: 30 minutos por defecto. */
    @Value("${finapp.sesion.refresh-inactivity-seconds:1800}")
    private long refreshInactivitySeconds;

    /** Tope absoluto de la sesión desde el login: 8 horas por defecto. No lo extiende el refresh. */
    @Value("${finapp.sesion.absolute-seconds:28800}")
    private long absoluteSeconds;

    /** Gracia para tolerar un reuso concurrente del refresh aún vigente: 15 segundos por defecto. */
    @Value("${finapp.sesion.reuse-grace-seconds:15}")
    private long reuseGraceSeconds;

    /** Intervalo de la limpieza programada de familias vencidas. */
    @Value("${finapp.sesion.limpieza-intervalo-ms:3600000}")
    private long limpiezaIntervaloMs;

    public long accessSeconds() { return accessSeconds; }
    public long refreshInactivitySeconds() { return refreshInactivitySeconds; }
    public long absoluteSeconds() { return absoluteSeconds; }
    public long reuseGraceSeconds() { return reuseGraceSeconds; }
    public long limpiezaIntervaloMs() { return limpiezaIntervaloMs; }
}
