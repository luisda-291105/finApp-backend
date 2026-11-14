package com.luisda.fin_app_back.Services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * @uso               Control de intentos fallidos de autenticación (login/registro)
 * @capa              Servicio
 * @responsabilidades Ventana deslizante de intentos fallidos por clave (IP + identificador)
 * @datos             Contadores en memoria
 * @dependencias      Ninguno
 * @usadoPor          CUsuario
 */
@Service
public class SIntentosRateLimit {
    private static final long VENTANA_SEGUNDOS = 900;
    private static final int MAX_INTENTOS = 5;
    private final Map<String, java.util.List<Instant>> intentos = new ConcurrentHashMap<>();

    /** Registra un intento fallido y decide si el intento actual debe aceptarse. */
    public boolean registrar(String clave, boolean fallo) {
        Instant ahora = Instant.now();
        java.util.List<Instant> historial = intentos.computeIfAbsent(clave, clave2 -> new java.util.ArrayList<>());
        historial.removeIf(instant -> ahora.minusSeconds(VENTANA_SEGUNDOS).isAfter(instant));
        boolean permitido = true;
        if (fallo) {
            if (historial.size() >= MAX_INTENTOS) permitido = false;
            historial.add(ahora);
        }
        if (historial.size() > 100) historial.clear();
        return permitido;
    }

    /** Limpia el historial de fallos de una clave (por ejemplo tras un inicio de sesión correcto). */
    public void limpiar(String clave) {
        if (clave != null) intentos.remove(clave);
    }
}
